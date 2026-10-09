package com.ai.aiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 加载 document 目录下的 Markdown 文档，供心理树洞知识库使用。
 */
@Component
@Slf4j
public class MindAppDocumentLoader {

    private static final String DOCUMENT_PATTERN = "classpath*:document/**/*.md";

    private final ResourcePatternResolver resourcePatternResolver;

    public MindAppDocumentLoader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    /**
     * 读取所有匹配的 Markdown 文件，并为每个 Document 添加来源文件名。
     */
    public List<Document> loadMarkdowns() {
        List<Document> allDocuments = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources(DOCUMENT_PATTERN);
            for (Resource resource : resources) {
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(false)
                        .withIncludeBlockquote(false)
                        .withAdditionalMetadata("filename", resource.getFilename())
                        .build();
                MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);
                allDocuments.addAll(reader.get());
            }
            log.info("已加载 {} 个 Markdown 文件，解析为 {} 个文档", resources.length, allDocuments.size());
        } catch (IOException e) {
            // 加载失败时中止初始化，避免用不完整的文档覆盖已持久化的向量库。
            throw new UncheckedIOException("Markdown 文档加载失败", e);
        }
        return allDocuments;
    }
}
