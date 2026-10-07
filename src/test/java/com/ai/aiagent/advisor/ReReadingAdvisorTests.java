package com.ai.aiagent.advisor;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReReadingAdvisorTests {

    private final ReReadingAdvisor advisor = new ReReadingAdvisor();

    @Test
    void repeatsOnlyCurrentQuestionAndPreservesRequestState() {
        UserMessage currentMessage = UserMessage.builder()
                .text("当前的问题")
                .metadata(Map.of("source", "test"))
                .build();
        List<Message> messages = List.of(new SystemMessage("系统提示词"),
                new UserMessage("历史问题"), new AssistantMessage("历史回复"), currentMessage);
        ChatOptions options = ChatOptions.builder().model("test-model").temperature(0.3).build();
        ChatClientRequest request = new ChatClientRequest(new Prompt(messages, options),
                Map.of("conversationId", "test-chat"));
        ChatClientResponse response = response("测试回复");
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any(ChatClientRequest.class))).thenReturn(response);

        assertThat(advisor.adviseCall(request, chain)).isSameAs(response);

        ArgumentCaptor<ChatClientRequest> captor = ArgumentCaptor.forClass(ChatClientRequest.class);
        verify(chain).nextCall(captor.capture());
        ChatClientRequest transformed = captor.getValue();
        assertThat(transformed.prompt().getInstructions()).extracting(Message::getText)
                .containsExactly("系统提示词", "历史问题", "历史回复",
                        "当前的问题\nRead the question again: 当前的问题\n");
        assertThat(transformed.prompt().getUserMessage().getMetadata()).isEqualTo(currentMessage.getMetadata());
        assertThat(transformed.prompt().getOptions().getModel()).isEqualTo("test-model");
        assertThat(transformed.prompt().getOptions().getTemperature()).isEqualTo(0.3);
        assertThat(transformed.context()).isEqualTo(request.context());
        assertThat(request.prompt().getInstructions()).containsExactlyElementsOf(messages);
        assertThat(currentMessage.getText()).isEqualTo("当前的问题");
    }

    @Test
    void repeatsLiteralQuestionAndPreservesStreamChunks() {
        // 用户输入的花括号和百分号应按原文保留，不再作为模板参数处理。
        String question = "解释 {topic}，进度 100%\n再给一个例子";
        ChatClientRequest request = new ChatClientRequest(new Prompt(question), Map.of("traceId", "test-trace"));
        ChatClientResponse first = response("第一段");
        ChatClientResponse second = response("第二段");
        StreamAdvisorChain chain = mock(StreamAdvisorChain.class);
        when(chain.nextStream(any(ChatClientRequest.class))).thenReturn(Flux.just(first, second));

        assertThat(advisor.adviseStream(request, chain).collectList().block(Duration.ofSeconds(5)))
                .containsExactly(first, second);

        ArgumentCaptor<ChatClientRequest> captor = ArgumentCaptor.forClass(ChatClientRequest.class);
        verify(chain).nextStream(captor.capture());
        assertThat(captor.getValue().prompt().getUserMessage().getText())
                .isEqualTo(question + "\nRead the question again: " + question + "\n");
        assertThat(captor.getValue().context()).isEqualTo(request.context());
        assertThat(request.prompt().getUserMessage().getText()).isEqualTo(question);
    }

    private ChatClientResponse response(String text) {
        return new ChatClientResponse(new ChatResponse(List.of(new Generation(new AssistantMessage(text)))), Map.of());
    }
}
