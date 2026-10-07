package com.ai.aiagent.advisor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MyLoggerAdvisorTests {

    private final MyLoggerAdvisor advisor = new MyLoggerAdvisor();
    private final Logger logger = (Logger) LoggerFactory.getLogger(MyLoggerAdvisor.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level previousLevel;

    @BeforeEach
    void captureLogs() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(previousLevel);
    }

    @Test
    void logsOnlyCurrentUserMessageAndPreservesCallResponse() {
        ChatClientRequest request = new ChatClientRequest(new Prompt(List.of(
                new UserMessage("之前的问题"), new AssistantMessage("之前的回复"),
                new UserMessage("当前的问题"))), Map.of("conversationId", "test-chat"));
        ChatClientResponse response = response("当前的回复");
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(request)).thenReturn(response);

        assertThat(advisor.adviseCall(request, chain)).isSameAs(response);

        verify(chain).nextCall(request);
        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("AI Request: 当前的问题", "AI Response: 当前的回复");
    }

    @Test
    void preservesStreamChunksAndLogsFullReplyOnce() {
        ChatClientRequest request = new ChatClientRequest(new Prompt("当前的问题"), Map.of());
        StreamAdvisorChain chain = mock(StreamAdvisorChain.class);
        when(chain.nextStream(request)).thenReturn(Flux.just(response("你好"), response("，世界")));

        List<ChatClientResponse> chunks = advisor.adviseStream(request, chain)
                .collectList().block(Duration.ofSeconds(5));

        verify(chain).nextStream(request);
        assertThat(chunks).extracting(chunk -> chunk.chatResponse().getResult().getOutput().getText())
                .containsExactly("你好", "，世界");
        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("AI Request: 当前的问题", "AI Response: 你好，世界");
    }

    @Test
    void toleratesAbsentOrEmptyChatResponse() {
        ChatClientRequest request = new ChatClientRequest(new Prompt("当前的问题"), Map.of());
        ChatClientResponse absent = new ChatClientResponse(null, Map.of());
        ChatClientResponse empty = new ChatClientResponse(new ChatResponse(List.of()), Map.of());
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(request)).thenReturn(absent, empty);

        assertThat(advisor.adviseCall(request, chain)).isSameAs(absent);
        assertThat(advisor.adviseCall(request, chain)).isSameAs(empty);
        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("AI Request: 当前的问题", "AI Request: 当前的问题");
    }

    private ChatClientResponse response(String text) {
        return new ChatClientResponse(new ChatResponse(List.of(new Generation(new AssistantMessage(text)))),
                Map.of("conversationId", "test-chat"));
    }
}
