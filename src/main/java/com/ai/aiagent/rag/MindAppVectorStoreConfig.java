package com.ai.aiagent.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 初始化心理树洞的内存向量库，并加载 Markdown 文档。
 */
@Configuration
public class MindAppVectorStoreConfig {

    private final MindAppDocumentLoader mindAppDocumentLoader;

    public MindAppVectorStoreConfig(MindAppDocumentLoader mindAppDocumentLoader) {
        this.mindAppDocumentLoader = mindAppDocumentLoader;
    }

    @Bean
    public VectorStore mindAppVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel)
                .build();
        List<Document> documents = mindAppDocumentLoader.loadMarkdowns();
        if (!documents.isEmpty()) {
            simpleVectorStore.add(documents);
        }
        return simpleVectorStore;
    }
}
