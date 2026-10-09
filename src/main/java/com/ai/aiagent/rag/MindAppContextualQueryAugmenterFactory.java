package com.ai.aiagent.rag;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;

/**
 * 统一创建心理树洞的查询增强器，配置知识库上下文和空检索结果的提示词。
 */
public final class MindAppContextualQueryAugmenterFactory {

    private MindAppContextualQueryAugmenterFactory() {
    }

    public static ContextualQueryAugmenter createInstance() {
        PromptTemplate contextPromptTemplate = new PromptTemplate("""
                {query}

                Context information is below, surrounded by ---------------------

                ---------------------
                {context}
                ---------------------

                Given the context and provided history information and not prior knowledge,
                reply to the user comment. If the answer is not in the context, inform
                the user that you can't answer the question.
                """);
        PromptTemplate emptyContextPromptTemplate = new PromptTemplate("""
                当前没有检索到可用的知识库内容。请用中文简短输出下面的内容：
                抱歉，当前知识库暂时没有找到相关内容。我主要提供情绪、压力和人际关系方面的支持，
                您可以换一种说法或补充具体情境。
                """);
        return ContextualQueryAugmenter.builder()
                .promptTemplate(contextPromptTemplate)
                .allowEmptyContext(false)
                .emptyContextPromptTemplate(emptyContextPromptTemplate)
                .build();
    }
}
