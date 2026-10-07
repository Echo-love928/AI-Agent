package com.ai.aiagent.app;

import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeChatAutoConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MindAppTests {

    @TempDir
    Path workingDirectory;

    private String previousUserDir;

    @BeforeEach
    void useTemporaryMemoryDirectory() {
        previousUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", workingDirectory.toString());
    }

    @AfterEach
    void restoreWorkingDirectory() {
        System.setProperty("user.dir", previousUserDir);
    }

    @Test
    void completesThreeRoundsWithOrderedUserAndAssistantHistory() {
        ChatModel chatModel = mock(ChatModel.class);
        String chatId = "three-round-chat";
        String firstMessage = "最近工作压力很大，我总担心做不好。";
        String firstReply = "听起来你承受着不少压力。最近哪件事最让你担心？";
        String secondMessage = "明天要做汇报，我怕被领导批评。";
        String secondReply = "你担心的是明天汇报时被否定。你最想先准备哪一部分？";
        String thirdMessage = "结合刚才说的，帮我梳理一下准备思路。";
        String thirdReply = "可以先整理汇报重点，再准备可能被问到的问题，按你的节奏逐步准备。";
        when(chatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage(firstReply)))),
                new ChatResponse(List.of(new Generation(new AssistantMessage(secondReply)))),
                new ChatResponse(List.of(new Generation(new AssistantMessage(thirdReply)))));
        MindApp mindApp = new MindApp(chatModel);

        String firstResult = mindApp.doChat(firstMessage, chatId);
        printRound(1, firstMessage, firstResult);
        assertThat(firstResult).isEqualTo(firstReply);

        String secondResult = mindApp.doChat(secondMessage, chatId);
        printRound(2, secondMessage, secondResult);
        assertThat(secondResult).isEqualTo(secondReply);

        String thirdResult = mindApp.doChat(thirdMessage, chatId);
        printRound(3, thirdMessage, thirdResult);
        assertThat(thirdResult).isEqualTo(thirdReply);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(3)).call(promptCaptor.capture());
        List<Prompt> prompts = promptCaptor.getAllValues();

        // 首轮只有当前用户消息，后续各轮按顺序携带之前的用户消息和 AI 回复。
        assertThat(conversationMessages(prompts.get(0)))
                .extracting(Message::getMessageType, Message::getText)
                .containsExactly(tuple(MessageType.USER, firstMessage));
        assertThat(conversationMessages(prompts.get(1)))
                .extracting(Message::getMessageType, Message::getText)
                .containsExactly(
                        tuple(MessageType.USER, firstMessage),
                        tuple(MessageType.ASSISTANT, firstReply),
                        tuple(MessageType.USER, secondMessage));
        assertThat(conversationMessages(prompts.get(2)))
                .extracting(Message::getMessageType, Message::getText)
                .containsExactly(
                        tuple(MessageType.USER, firstMessage),
                        tuple(MessageType.ASSISTANT, firstReply),
                        tuple(MessageType.USER, secondMessage),
                        tuple(MessageType.ASSISTANT, secondReply),
                        tuple(MessageType.USER, thirdMessage));
    }

    @Test
    void initializesWithAutoConfiguredDashScopeChatModel() {
        // 只验证自动装配，使用占位 API Key，不调用模型。
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DashScopeChatAutoConfiguration.class))
                .withUserConfiguration(MindApp.class)
                .withPropertyValues("spring.ai.dashscope.api-key=test-key")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MindApp.class);
                    assertThat(context).hasBean("dashScopeChatModel");
                });
    }

    @Test
    void continuesTheSameConversationAndIsolatesOtherConversations() {
        List<Prompt> prompts = new ArrayList<>();
        MindApp mindApp = createMindApp(prompts);

        assertThat(mindApp.doChat("最近工作压力很大", "chat-1")).isEqualTo("测试回复");
        mindApp.doChat("我想继续聊聊", "chat-1");
        mindApp.doChat("我想聊家庭关系", "chat-2");

        assertThat(userMessages(prompts.get(1)))
                .containsExactly("最近工作压力很大", "我想继续聊聊");
        assertThat(prompts.get(1).getInstructions())
                .filteredOn(message -> message instanceof AssistantMessage)
                .extracting(Message::getText)
                .containsExactly("测试回复");
        assertThat(userMessages(prompts.get(2))).containsExactly("我想聊家庭关系");
    }

    @Test
    void retainsFileHistoryBeyondTenMessages() {
        List<Prompt> prompts = new ArrayList<>();
        MindApp mindApp = createMindApp(prompts);

        for (int i = 1; i <= 7; i++) {
            mindApp.doChat("消息" + i, "chat-1");
        }

        Prompt lastPrompt = prompts.getLast();
        // 文件记忆保留全部历史：12 条历史消息，加默认系统提示词和本轮用户消息。
        assertThat(lastPrompt.getInstructions()).hasSize(14);
        assertThat(userMessages(lastPrompt))
                .containsExactly("消息1", "消息2", "消息3", "消息4", "消息5", "消息6", "消息7");
    }

    @Test
    void resumesConversationAfterMindAppIsRecreated() {
        List<Prompt> prompts = new ArrayList<>();
        MindApp firstApp = createMindApp(prompts);
        firstApp.doChat("最近工作压力很大", "persisted-chat");

        assertThat(workingDirectory.resolve("chat-memory/persisted-chat.kryo")).exists();
        MindApp reloadedApp = createMindApp(prompts);
        reloadedApp.doChat("结合刚才的情况继续聊聊", "persisted-chat");

        assertThat(conversationMessages(prompts.getLast()))
                .extracting(Message::getMessageType, Message::getText)
                .containsExactly(tuple(MessageType.USER, "最近工作压力很大"),
                        tuple(MessageType.ASSISTANT, "测试回复"),
                        tuple(MessageType.USER, "结合刚才的情况继续聊聊"));
    }

    @Test
    void generatesStructuredReportWithOriginalSystemPrompt() throws IOException {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(
                new AssistantMessage("""
                        {"title":"小明的心理报告","suggestions":["安排短暂休息","整理压力来源"]}
                        """)))));
        MindApp mindApp = new MindApp(chatModel);

        MindApp.MindReport report = mindApp.doChatWithReport("我叫小明，最近工作压力很大", "report-chat");

        assertThat(report.title()).isEqualTo("小明的心理报告");
        assertThat(report.suggestions()).containsExactly("安排短暂休息", "整理压力来源");
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        Prompt prompt = captor.getValue();
        String originalSystemPrompt = new ClassPathResource("prompts/psychological-support-system.txt")
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(prompt.getSystemMessage().getText()).isEqualToNormalizingNewlines(originalSystemPrompt
                + "\n每次对话后都要生成心理状况结果，标题为{用户名}的心理报告，内容为建议列表");
        assertThat(prompt.getUserMessage().getText()).contains("title", "suggestions");
    }

    @Test
    void reportSharesConversationMemoryAndDoesNotChangeDefaultSystemPrompt() throws IOException {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("之前的回复")))),
                new ChatResponse(List.of(new Generation(new AssistantMessage(
                        "{\"title\":\"小明的心理报告\",\"suggestions\":[\"适当休息\"]}")))),
                new ChatResponse(List.of(new Generation(new AssistantMessage("继续聊聊")))),
                new ChatResponse(List.of(new Generation(new AssistantMessage(
                        "{\"title\":\"用户的心理报告\",\"suggestions\":[\"梳理感受\"]}")))));
        MindApp mindApp = new MindApp(chatModel);

        mindApp.doChat("我叫小明，最近工作压力很大", "shared-chat");
        mindApp.doChatWithReport("结合刚才的对话给我建议", "shared-chat");
        mindApp.doChat("继续聊聊", "shared-chat");
        mindApp.doChatWithReport("我想聊家庭关系", "other-chat");

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(4)).call(captor.capture());
        List<Prompt> prompts = captor.getAllValues();
        assertThat(userMessages(prompts.get(1))).hasSize(2);
        assertThat(userMessages(prompts.get(1)).getFirst()).isEqualTo("我叫小明，最近工作压力很大");
        assertThat(userMessages(prompts.get(1)).getLast()).startsWith("结合刚才的对话给我建议");
        assertThat(prompts.get(1).getInstructions()).filteredOn(message -> message instanceof AssistantMessage)
                .extracting(Message::getText).containsExactly("之前的回复");
        assertThat(userMessages(prompts.get(2))).hasSize(3);
        assertThat(prompts.get(2).getInstructions()).filteredOn(message -> message instanceof AssistantMessage)
                .extracting(Message::getText).hasSize(2);
        assertThat(prompts.get(2).getSystemMessage().getText()).isEqualTo(
                new ClassPathResource("prompts/psychological-support-system.txt")
                        .getContentAsString(StandardCharsets.UTF_8));
        assertThat(userMessages(prompts.get(3))).hasSize(1);
        assertThat(userMessages(prompts.get(3)).getFirst()).startsWith("我想聊家庭关系");
    }

    private void printRound(int round, String message, String reply) {
        System.out.printf("第 %d 轮%n用户：%s%nAI（模拟回复）：%s%n%n", round, message, reply);
    }

    private MindApp createMindApp(List<Prompt> prompts) {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            prompts.add(invocation.getArgument(0));
            return new ChatResponse(List.of(new Generation(new AssistantMessage("测试回复"))));
        });
        return new MindApp(chatModel);
    }

    private List<String> userMessages(Prompt prompt) {
        return prompt.getInstructions().stream()
                .filter(message -> message instanceof UserMessage)
                .map(Message::getText)
                .toList();
    }

    private List<Message> conversationMessages(Prompt prompt) {
        return prompt.getInstructions().stream()
                .filter(message -> !(message instanceof SystemMessage))
                .toList();
    }
}
