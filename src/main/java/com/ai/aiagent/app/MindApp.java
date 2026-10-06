package com.ai.aiagent.app;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class MindApp {

    private final ChatClient chatClient;

    private static final int CHAT_MEMORY_SIZE = 10;

    private static final Resource SYSTEM_PROMPT =
            new ClassPathResource("prompts/psychological-support-system.txt");

    public MindApp(@Qualifier("dashScopeChatModel") ChatModel dashScopeChatModel) {
        // 初始化基于内存的对话记忆，保留最近 10 条消息（用户和 AI 消息均计入）。
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(CHAT_MEMORY_SIZE)
                .build();
        chatClient = ChatClient.builder(dashScopeChatModel)
                .defaultSystem(SYSTEM_PROMPT, StandardCharsets.UTF_8)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    /**
     * 发送用户消息，同一 chatId 的后续调用会携带该会话的记忆。
     */
    public String doChat(String message, String chatId) {
        Assert.hasText(message, "message 不能为空");
        Assert.hasText(chatId, "chatId 不能为空");

        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("模型未返回有效回复");
        }
        String content = response.getResult().getOutput().getText();
        log.info("心理树洞对话调用完成");
        return content;
    }
}
