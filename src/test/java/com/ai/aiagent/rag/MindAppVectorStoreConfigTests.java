package com.ai.aiagent.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MindAppVectorStoreConfigTests {

    @TempDir
    Path workingDirectory;

    private Path storePath;
    private Path metadataPath;
    private MockEnvironment environment;
    private EmbeddingModel embeddingModel;
    private MyKeywordEnricher keywordEnricher;

    @BeforeEach
    void setUp() {
        storePath = workingDirectory.resolve("data/mind-vector-store.json");
        metadataPath = storePath.resolveSibling("mind-vector-store.json.meta.json");
        environment = new MockEnvironment().withProperty("rag.vector-store.path", storePath.toString());
        embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.dimensions()).thenReturn(2);
        when(embeddingModel.embed(any(Document.class))).thenReturn(new float[]{1, 0});
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{1, 0});
        keywordEnricher = mock(MyKeywordEnricher.class);
        when(keywordEnricher.enrichDocuments(anyList())).thenAnswer(invocation -> {
            List<Document> documents = invocation.getArgument(0);
            documents.forEach(document -> document.getMetadata().put("excerpt_keywords", "压力, 工作"));
            return documents;
        });
    }

    @Test
    void firstStartupCreatesDirectoriesAndPersistsTextMetadataAndVectors() throws IOException {
        Document source = document("如何应对工作压力？");
        initialize(List.of(source));

        assertThat(storePath).exists();
        assertThat(metadataPath).exists();
        assertThat(Files.readString(storePath)).contains("如何应对工作压力？", "excerpt_keywords", "embedding");
        verify(keywordEnricher).enrichDocuments(anyList());
        verify(embeddingModel).embed(source);

        clearInvocations(embeddingModel, keywordEnricher);
        VectorStore restored = initialize(List.of(document("如何应对工作压力？")));
        verifyNoInteractions(embeddingModel, keywordEnricher);
        Document result = search(restored).getFirst();
        assertThat(result.getId()).isEqualTo(source.getId());
        assertThat(result.getText()).isEqualTo(source.getText());
        assertThat(result.getMetadata()).containsEntry("filename", "faq.md")
                .containsEntry("excerpt_keywords", "压力, 工作");
        verify(embeddingModel).embed("工作压力");
    }

    @Test
    void restartReusesVectorsDespiteNewDocumentIdsAndDifferentScanOrder() {
        initialize(List.of(document("问题一"), document("问题二")));
        clearInvocations(embeddingModel, keywordEnricher);

        initialize(List.of(document("问题二"), document("问题一")));

        verifyNoInteractions(embeddingModel, keywordEnricher);
    }

    @ParameterizedTest
    @ValueSource(strings = {"edit", "add", "remove", "metadata"})
    void documentChangesRebuildCacheWithoutRetainingDeletedDocuments(String change) {
        initialize(List.of(document("问题一"), document("问题二")));
        clearInvocations(embeddingModel, keywordEnricher);
        List<Document> changedDocuments = switch (change) {
            case "edit" -> List.of(document("修改后的问题"), document("问题二"));
            case "add" -> List.of(document("问题一"), document("问题二"), document("新增问题"));
            case "remove" -> List.of(document("问题二"));
            default -> List.of(new Document("问题一", Map.of("filename", "renamed.md")), document("问题二"));
        };

        VectorStore rebuilt = initialize(changedDocuments);

        verify(keywordEnricher).enrichDocuments(changedDocuments);
        verify(embeddingModel, times(changedDocuments.size())).embed(any(Document.class));
        assertThat(search(rebuilt)).extracting(Document::getText)
                .containsExactlyInAnyOrderElementsOf(changedDocuments.stream().map(Document::getText).toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "spring.ai.dashscope.embedding.options.model",
            "spring.ai.dashscope.embedding.options.dimensions",
            "spring.ai.dashscope.embedding.options.text-type",
            "spring.ai.dashscope.embedding.metadata-mode",
            "spring.ai.dashscope.chat.options.model",
            "rag.vector-store.cache-version"
    })
    void modelAndPipelineConfigurationChangesInvalidateCache(String configurationKey) {
        initialize(List.of(document("问题")));
        clearInvocations(embeddingModel, keywordEnricher);
        environment.setProperty(configurationKey, "changed");

        initialize(List.of(document("问题")));

        verify(keywordEnricher).enrichDocuments(anyList());
        verify(embeddingModel).embed(any(Document.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"vector", "metadata", "missingMetadata"})
    void corruptedOrIncompleteCacheIsRebuilt(String corruptedFile) throws IOException {
        initialize(List.of(document("问题")));
        clearInvocations(embeddingModel, keywordEnricher);
        switch (corruptedFile) {
            // 即使向量文件仍然是合法 JSON，也应由校验和发现数据损坏。
            case "vector" -> Files.writeString(storePath, "{}");
            case "metadata" -> Files.writeString(metadataPath, "invalid-json");
            default -> Files.delete(metadataPath);
        }

        VectorStore rebuilt = initialize(List.of(document("问题")));

        verify(keywordEnricher).enrichDocuments(anyList());
        verify(embeddingModel).embed(any(Document.class));
        assertThat(search(rebuilt)).extracting(Document::getText).containsExactly("问题");
        clearInvocations(embeddingModel, keywordEnricher);
        initialize(List.of(document("问题")));
        verifyNoInteractions(embeddingModel, keywordEnricher);
    }

    @Test
    void rebuildFlagForcesRegenerationOfUnchangedDocuments() {
        initialize(List.of(document("问题")));
        clearInvocations(embeddingModel, keywordEnricher);
        environment.setProperty("rag.vector-store.rebuild", "true");

        initialize(List.of(document("问题")));

        verify(keywordEnricher).enrichDocuments(anyList());
        verify(embeddingModel).embed(any(Document.class));
    }

    @Test
    void emptyKnowledgeBasePersistsAndReloadsWithoutModelCalls() throws IOException {
        initialize(List.of());
        assertThat(new ObjectMapper().readTree(storePath.toFile()).isEmpty()).isTrue();
        initialize(List.of());

        verifyNoInteractions(embeddingModel, keywordEnricher);
    }

    @Test
    void failedRegenerationLeavesPreviousCacheUntouched() throws IOException {
        initialize(List.of(document("原始问题")));
        byte[] previousStore = Files.readAllBytes(storePath);
        byte[] previousMetadata = Files.readAllBytes(metadataPath);
        when(embeddingModel.embed(any(Document.class))).thenThrow(new IllegalStateException("模型调用失败"));

        assertThatIllegalStateException().isThrownBy(() -> initialize(List.of(document("修改后的问题"))))
                .withMessage("模型调用失败");

        assertThat(Files.readAllBytes(storePath)).isEqualTo(previousStore);
        assertThat(Files.readAllBytes(metadataPath)).isEqualTo(previousMetadata);
        clearInvocations(embeddingModel, keywordEnricher);
        VectorStore restored = initialize(List.of(document("原始问题")));
        verifyNoInteractions(embeddingModel, keywordEnricher);
        assertThat(search(restored)).extracting(Document::getText).containsExactly("原始问题");
    }

    @Test
    void unwritableStoreLocationReportsPersistenceFailure() throws IOException {
        Files.createFile(workingDirectory.resolve("data"));

        assertThatIllegalStateException().isThrownBy(() -> initialize(List.of()))
                .withMessageContaining("知识库向量持久化失败");
    }

    private VectorStore initialize(List<Document> documents) {
        MindAppDocumentLoader loader = mock(MindAppDocumentLoader.class);
        when(loader.loadMarkdowns()).thenReturn(documents);
        return new MindAppVectorStoreConfig(loader, keywordEnricher, environment)
                .mindAppVectorStore(embeddingModel);
    }

    private static Document document(String text) {
        return new Document(text, Map.of("filename", "faq.md"));
    }

    private static List<Document> search(VectorStore store) {
        return store.similaritySearch(SearchRequest.builder().query("工作压力").topK(10).build());
    }
}
