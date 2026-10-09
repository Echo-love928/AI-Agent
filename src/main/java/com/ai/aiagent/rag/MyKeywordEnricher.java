package com.ai.aiagent.rag;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 使用聊天模型为知识库文档补充关键词元信息。
 */
@Component
public class MyKeywordEnricher {

    static final int KEYWORD_COUNT = 5;

    private final ChatModel dashScopeChatModel;

    public MyKeywordEnricher(@Qualifier("dashScopeChatModel") ChatModel dashScopeChatModel) {
        this.dashScopeChatModel = dashScopeChatModel;
    }

    public List<Document> enrichDocuments(List<Document> documents) {
        KeywordMetadataEnricher enricher = new KeywordMetadataEnricher(dashScopeChatModel, KEYWORD_COUNT);
        return enricher.apply(documents);
    }
}
