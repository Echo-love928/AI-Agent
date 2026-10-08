package com.ai.aiagent.app;

import com.ai.aiagent.rag.MindAppDocumentLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MindAppRagTests {

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
    void answersQuestionCoveredByMarkdownAndSharesConversationMemory() throws IOException {
        String question = "被领导批评后，我总觉得自己很差，怎么办？";
        String chatId = "rag-known-answer-chat";
        MindAppDocumentLoader loader = new MindAppDocumentLoader(new PathMatchingResourcePatternResolver());
        Document answerDocument = loader.loadMarkdowns().stream()
                .filter(document -> "01-职场压力与内耗问答.md".equals(document.getMetadata().get("filename")))
                .filter(document -> String.valueOf(document.getMetadata().get("title")).contains(question))
                .findFirst()
                .orElseThrow(() -> new AssertionError("知识库中应包含这个问题的回答"));
        assertThat(answerDocument.getText()).contains("对方的原话和自己的推测分开写", "您希望我优先修改哪一部分");

        // 使用真实 Markdown 回答作为检索结果，外部检索和模型调用均使用模拟对象。
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(answerDocument));
        ChatModel chatModel = mock(ChatModel.class);
        String expectedReply = "被批评后难受可以理解。可以先把具体反馈与对自己的整体否定分开，"
                + "再确认这次需要修改的内容，例如问：您希望我优先修改哪一部分？";
        when(chatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage(expectedReply)))),
                new ChatResponse(List.of(new Generation(new AssistantMessage("可以先从这次报告要补充的数据开始。")))));
        MindApp mindApp = new MindApp(chatModel, vectorStore);

        String reply = mindApp.doChatWithRag(question, chatId);
        assertThat(reply).isEqualTo(expectedReply);
        System.out.printf("RAG 提问：%s%n来源文档：%s%nAI（模拟回复）：%s%n%n",
                question, answerDocument.getMetadata().get("filename"), reply);

        // 普通对话可接续 RAG 会话，历史中保存原始提问，而不是附带整段知识库上下文。
        String followUp = "我应该先从哪一步开始？";
        mindApp.doChat(followUp, chatId);

        ArgumentCaptor<SearchRequest> searchCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(searchCaptor.capture());
        assertThat(searchCaptor.getValue().getQuery()).isEqualTo(question);
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(promptCaptor.capture());
        List<Prompt> prompts = promptCaptor.getAllValues();
        assertThat(prompts.getFirst().getUserMessage().getText()).contains(question, answerDocument.getText());
        assertThat(prompts.getFirst().getSystemMessage().getText()).isEqualTo(
                new ClassPathResource("prompts/psychological-support-system.txt")
                        .getContentAsString(StandardCharsets.UTF_8));
        assertThat(prompts.getLast().getInstructions())
                .filteredOn(message -> message instanceof UserMessage)
                .extracting(Message::getText)
                .containsExactly(question, followUp);
        assertThat(prompts.getLast().getInstructions())
                .filteredOn(message -> message instanceof AssistantMessage)
                .extracting(Message::getText)
                .containsExactly(expectedReply);
    }

    @Test
    void rejectsBlankInputsBeforeRetrievingOrCallingModel() {
        ChatModel chatModel = mock(ChatModel.class);
        VectorStore vectorStore = mock(VectorStore.class);
        MindApp mindApp = new MindApp(chatModel, vectorStore);

        assertThatIllegalArgumentException().isThrownBy(() -> mindApp.doChatWithRag(" ", "chat-1"))
                .withMessage("message 不能为空");
        assertThatIllegalArgumentException().isThrownBy(() -> mindApp.doChatWithRag("我想聊聊", " "))
                .withMessage("chatId 不能为空");
        verifyNoInteractions(vectorStore);
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void rejectsMissingModelReply() {
        ChatModel chatModel = mock(ChatModel.class);
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of()));
        MindApp mindApp = new MindApp(chatModel, vectorStore);

        assertThatIllegalStateException().isThrownBy(() -> mindApp.doChatWithRag("我想聊聊", "chat-1"))
                .withMessage("模型未返回有效回复");
    }
}
