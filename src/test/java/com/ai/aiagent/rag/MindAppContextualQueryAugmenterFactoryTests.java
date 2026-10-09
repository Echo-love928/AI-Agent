package com.ai.aiagent.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MindAppContextualQueryAugmenterFactoryTests {

    @Test
    void retrievedContextAugmentsOriginalQuestionWithAllDocuments() {
        ContextualQueryAugmenter augmenter = MindAppContextualQueryAugmenterFactory.createInstance();
        Query query = new Query("在{项目A}中被批评后，如何调整自己的心态？");

        Query augmentedQuery = augmenter.augment(query, List.of(
                new Document("把具体反馈和对自己的整体否定分开。"),
                new Document("确认最优先需要修改的部分。")));

        assertThat(augmentedQuery.text()).contains(query.text(),
                "把具体反馈和对自己的整体否定分开。", "确认最优先需要修改的部分。")
                .doesNotContain("当前知识库暂时没有找到相关内容");
    }

    @Test
    void emptyContextUsesMindAppFallbackInsteadOfOriginalQuestion() {
        ContextualQueryAugmenter augmenter = MindAppContextualQueryAugmenterFactory.createInstance();
        Query query = new Query("帮我写一段 Java 代码");

        Query augmentedQuery = augmenter.augment(query, List.of());

        assertThat(augmentedQuery.text()).contains("当前知识库暂时没有找到相关内容",
                "情绪、压力和人际关系", "换一种说法或补充具体情境")
                .doesNotContain(query.text());
    }
}
