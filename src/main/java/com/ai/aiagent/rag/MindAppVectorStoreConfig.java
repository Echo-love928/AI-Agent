package com.ai.aiagent.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 初始化心理树洞向量库，使用本地 JSON 文件保存和恢复向量。
 */
@Configuration
@Slf4j
public class MindAppVectorStoreConfig {

    private static final List<String> CACHE_CONFIG_KEYS = List.of(
            "spring.ai.dashscope.embedding.options.model",
            "spring.ai.dashscope.embedding.options.dimensions",
            "spring.ai.dashscope.embedding.options.text-type",
            "spring.ai.dashscope.embedding.metadata-mode",
            "spring.ai.dashscope.chat.options.model",
            "rag.vector-store.cache-version");

    private final MindAppDocumentLoader mindAppDocumentLoader;

    private final MyKeywordEnricher myKeywordEnricher;

    private final Environment environment;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public MindAppVectorStoreConfig(MindAppDocumentLoader mindAppDocumentLoader,
                                    MyKeywordEnricher myKeywordEnricher,
                                    Environment environment) {
        this.mindAppDocumentLoader = mindAppDocumentLoader;
        this.myKeywordEnricher = myKeywordEnricher;
        this.environment = environment;
    }

    @Bean
    public VectorStore mindAppVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel)
                .build();
        List<Document> documents = mindAppDocumentLoader.loadMarkdowns();
        Path storePath = Path.of(environment.getProperty(
                "rag.vector-store.path", "data/mind-vector-store.json")).toAbsolutePath().normalize();
        Path metadataPath = storePath.resolveSibling(storePath.getFileName() + ".meta.json");
        String fingerprint = fingerprint(documents, dashscopeEmbeddingModel);
        boolean rebuild = environment.getProperty("rag.vector-store.rebuild", Boolean.class, false);
        if (!rebuild && loadCachedStore(simpleVectorStore, storePath, metadataPath, fingerprint)) {
            return simpleVectorStore;
        }

        if (!documents.isEmpty()) {
            List<Document> enrichedDocuments = myKeywordEnricher.enrichDocuments(documents);
            simpleVectorStore.add(enrichedDocuments);
        }
        saveStore(simpleVectorStore, storePath, metadataPath, fingerprint);
        return simpleVectorStore;
    }

    private String fingerprint(List<Document> documents, EmbeddingModel embeddingModel) {
        try {
            // 不使用随机生成的文档 ID，并排序以避免资源扫描顺序影响缓存命中。
            List<String> sourceDocuments = new ArrayList<>();
            for (Document document : documents) {
                sourceDocuments.add(objectMapper.writeValueAsString(Map.of(
                        "text", document.getText(), "metadata", new TreeMap<>(document.getMetadata()))));
            }
            sourceDocuments.sort(String::compareTo);
            Map<String, Object> configuration = new TreeMap<>();
            for (String key : CACHE_CONFIG_KEYS) {
                configuration.put(key, environment.getProperty(key, "<default>"));
            }
            configuration.put("embeddingModelClass", embeddingModel.getClass().getName());
            configuration.put("keywordCount", MyKeywordEnricher.KEYWORD_COUNT);
            configuration.put("keywordTemplate", KeywordMetadataEnricher.KEYWORDS_TEMPLATE);
            configuration.put("documents", sourceDocuments);
            return HexFormat.of().formatHex(sha256().digest(objectMapper.writeValueAsBytes(configuration)));
        } catch (IOException e) {
            throw new IllegalStateException("无法计算知识库文档校验信息", e);
        }
    }

    private boolean loadCachedStore(SimpleVectorStore vectorStore, Path storePath,
                                    Path metadataPath, String fingerprint) {
        if (!Files.isRegularFile(storePath) || !Files.isRegularFile(metadataPath)) {
            return false;
        }
        try {
            CacheMetadata metadata = objectMapper.readValue(metadataPath.toFile(), CacheMetadata.class);
            if (!fingerprint.equals(metadata.fingerprint())) {
                log.info("知识库文档或模型配置已变化，重新构建向量库");
                return false;
            }
            if (!checksum(storePath).equals(metadata.storeChecksum())) {
                log.warn("向量库文件校验失败，重新构建向量库: {}", storePath);
                return false;
            }
            vectorStore.load(storePath.toFile());
            log.info("已从本地文件恢复知识库向量: {}", storePath);
            return true;
        } catch (IOException | RuntimeException e) {
            log.warn("无法加载向量库缓存，将重新构建: {}", storePath, e);
            return false;
        }
    }

    private void saveStore(SimpleVectorStore vectorStore, Path storePath,
                           Path metadataPath, String fingerprint) {
        Path temporaryStore = null;
        Path temporaryMetadata = null;
        try {
            Files.createDirectories(storePath.getParent());
            temporaryStore = Files.createTempFile(storePath.getParent(), "mind-vector-", ".json");
            temporaryMetadata = Files.createTempFile(storePath.getParent(), "mind-vector-meta-", ".json");
            vectorStore.save(temporaryStore.toFile());
            objectMapper.writeValue(temporaryMetadata.toFile(),
                    new CacheMetadata(fingerprint, checksum(temporaryStore)));
            // 先替换向量文件再替换校验信息；中途失败时，下次启动会识别不匹配并重建。
            replaceFile(temporaryStore, storePath);
            replaceFile(temporaryMetadata, metadataPath);
            log.info("知识库向量已保存到本地文件: {}", storePath);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("知识库向量持久化失败: " + storePath, e);
        } finally {
            deleteTemporaryFile(temporaryStore);
            deleteTemporaryFile(temporaryMetadata);
        }
    }

    private static void replaceFile(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String checksum(Path path) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) {
                digest.update(buffer, 0, length);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }

    private static void deleteTemporaryFile(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.warn("无法清理向量库临时文件: {}", path, e);
            }
        }
    }

    private record CacheMetadata(String fingerprint, String storeChecksum) {
    }
}
