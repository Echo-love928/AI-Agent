package com.ai.aiagent.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueryRewriterTests {

    @Test
    void rewritesVerboseQuestionAndPreservesLiteralBracesInInput() {
        ChatModel model = mock(ChatModel.class);
        String prompt = "我在{项目A}里被领导批评了，现在总觉得自己很差，应该怎么办？";
        String rewritten = "职场批评后的自我否定如何调整";
        when(model.call(any(Prompt.class))).thenReturn(response(rewritten));

        String result = new QueryRewriter(model).doQueryRewrite(prompt);

        assertThat(result).isEqualTo(rewritten);
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(captor.capture());
        assertThat(captor.getValue().getUserMessage().getText())
                .contains(prompt, "vector store", "Rewritten query:");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n"})
    void rejectsBlankQueryBeforeCallingModel(String prompt) {
        ChatModel model = mock(ChatModel.class);
        QueryRewriter rewriter = new QueryRewriter(model);

        assertThatIllegalArgumentException().isThrownBy(() -> rewriter.doQueryRewrite(prompt))
                .withMessage("prompt 不能为空");
        verify(model, never()).call(any(Prompt.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\n"})
    void blankRewriteFallsBackToOriginalQuery(String rewritten) {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(response(rewritten));
        String original = "被领导批评后，我总觉得自己很差，怎么办？";

        assertThat(new QueryRewriter(model).doQueryRewrite(original)).isEqualTo(original);
    }

    @Test
    void missingGenerationFallsBackToOriginalQuery() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of()));

        assertThat(new QueryRewriter(model).doQueryRewrite("工作压力大怎么办"))
                .isEqualTo("工作压力大怎么办");
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
