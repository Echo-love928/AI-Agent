package com.ai.aiagent.app;

import com.ai.aiagent.advisor.MyLoggerAdvisor;
import com.ai.aiagent.chatmemory.FileBasedChatMemory;
import com.ai.aiagent.rag.MindAppContextualQueryAugmenterFactory;
import com.ai.aiagent.rag.QueryRewriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Slf4j
public class MindApp {

    private final ChatClient chatClient;

    private final RetrievalAugmentationAdvisor ragAdvisor;

    @jakarta.annotation.Resource
    private ToolCallback[] allTools;

    private static final Resource SYSTEM_PROMPT =
            new ClassPathResource("prompts/psychological-support-system.txt");

    public MindApp(@Qualifier("dashScopeChatModel") ChatModel dashScopeChatModel,
                   @Qualifier("mindAppVectorStore") VectorStore mindAppVectorStore,
                   QueryRewriter queryRewriter) {
        ragAdvisor = RetrievalAugmentationAdvisor.builder()
                .queryTransformers(query -> query.mutate()
                        .text(queryRewriter.doQueryRewrite(query.text())).build())
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .vectorStore(mindAppVectorStore).build())
                // 工厂统一配置文档上下文和空检索结果的提示词。
                .queryAugmenter(MindAppContextualQueryAugmenterFactory.createInstance())
                // 单次查询使用当前线程，避免为每个 MindApp 创建检索线程池。
                .taskExecutor(new SyncTaskExecutor())
                .build();
        // 初始化基于文件的对话记忆，同一 chatId 的历史消息可在应用重启后读取。
        String fileDir = System.getProperty("user.dir") + "/chat-memory";
        ChatMemory chatMemory = new FileBasedChatMemory(fileDir);
        chatClient = ChatClient.builder(dashScopeChatModel)
                .defaultSystem(SYSTEM_PROMPT, StandardCharsets.UTF_8)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new MyLoggerAdvisor())
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

    /**
     * 重写查询并检索心理树洞知识库后回复用户，沿用同一 chatId 的对话记忆。
     */
    public String doChatWithRag(String message, String chatId) {
        Assert.hasText(message, "message 不能为空");
        Assert.hasText(chatId, "chatId 不能为空");

        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(ragAdvisor)
                .call()
                .chatResponse();
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("模型未返回有效回复");
        }
        String content = response.getResult().getOutput().getText();
        log.info("心理树洞 RAG 对话调用完成");
        return content;
    }

    /**
     * 绑定所有已注册的工具后回复用户，沿用同一 chatId 的对话记忆。
     */
    public String doChatWithTools(String message, String chatId) {
        Assert.hasText(message, "message 不能为空");
        Assert.hasText(chatId, "chatId 不能为空");

        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // Spring AI 1.1.x 使用 toolCallbacks 绑定 ToolCallback 数组。
                .toolCallbacks(allTools)
                .call()
                .chatResponse();
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("模型未返回有效回复");
        }
        String content = response.getResult().getOutput().getText();
        log.info("心理树洞工具对话调用完成");
        return content;
    }

    public record MindReport(String title, List<String> suggestions) {
    }

    /**
     * 基于当前会话生成包含标题和建议列表的心理报告。(实战结构化输出)
     */
    public MindReport doChatWithReport(String message, String chatId) {
        Assert.hasText(message, "message 不能为空");
        Assert.hasText(chatId, "chatId 不能为空");

        String reportSystemPrompt;
        try {
            reportSystemPrompt = SYSTEM_PROMPT.getContentAsString(StandardCharsets.UTF_8)
                    + "\n每次对话后都要生成心理状况结果，标题为{用户名}的心理报告，内容为建议列表";
        } catch (IOException e) {
            throw new UncheckedIOException("无法读取心理支持系统提示词", e);
        }

        MindReport mindReport = chatClient
                .prompt()
                // 通过参数传入完整提示词，将 {用户名} 保留为模型理解的标题格式。
                .system(spec -> spec.text("{reportSystemPrompt}").param("reportSystemPrompt", reportSystemPrompt))
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(MindReport.class);
        log.info("mindReport: {}", mindReport);
        return mindReport;
    }
}
