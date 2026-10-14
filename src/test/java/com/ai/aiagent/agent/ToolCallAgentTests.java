package com.ai.aiagent.agent;

import com.ai.aiagent.agent.model.AgentState;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCallAgentTests {

    @Test
    void manusPreservesConfiguredModelOptions() {
        DashScopeChatOptions defaults = DashScopeChatOptions.builder()
                .model("configured-model").multiModel(true).temperature(0.2).build();
        ChatModel model = new ChatModel() {
            @Override
            public ChatOptions getDefaultOptions() {
                return defaults;
            }

            @Override
            public ChatResponse call(Prompt prompt) {
                assertThat(prompt.getOptions()).isInstanceOf(DashScopeChatOptions.class);
                DashScopeChatOptions options = (DashScopeChatOptions) prompt.getOptions();
                assertThat(options.getModel()).isEqualTo("configured-model");
                assertThat(options.getMultiModel()).isTrue();
                assertThat(options.getTemperature()).isEqualTo(0.2);
                assertThat(options.getInternalToolExecutionEnabled()).isFalse();
                assertThat(options.getToolCallbacks()).hasSize(2);
                return new ChatResponse(List.of(new Generation(new AssistantMessage("done"))));
            }
        };
        Manus manus = new Manus(ToolCallbacks.from(new TestTools()), model);

        assertThat(manus.run("finish")).contains("done");
        assertThat(defaults.getInternalToolExecutionEnabled()).isNull();
        assertThat(defaults.getToolCallbacks()).isEmpty();
    }

    @Test
    void executesRegisteredToolsAndPreservesHistoryUntilTermination() {
        TestTools tools = new TestTools();
        AtomicInteger calls = new AtomicInteger();
        ToolCallAgent agent = agent(tools, prompt -> {
            ToolCallingChatOptions options = (ToolCallingChatOptions) prompt.getOptions();
            assertThat(options.getInternalToolExecutionEnabled()).isFalse();
            assertThat(options.getToolCallbacks()).hasSize(2);
            if (calls.getAndIncrement() == 0) {
                return toolResponse("echo", "{\"text\":\"hello\"}");
            }
            assertThat(prompt.getInstructions()).anySatisfy(message -> {
                assertThat(message).isInstanceOf(ToolResponseMessage.class);
                ToolResponseMessage toolMessage = (ToolResponseMessage) message;
                assertThat(toolMessage.getResponses().getFirst().responseData()).contains("hello");
            });
            return toolResponse("doTerminate", "{}");
        });

        String answer = agent.run("test tools");

        assertThat(calls.get()).isEqualTo(2);
        assertThat(tools.echoCalls).isEqualTo(1);
        assertThat(tools.terminateCalls).isEqualTo(1);
        assertThat(agent.getState()).isEqualTo(AgentState.FINISHED);
        assertThat(answer).contains("hello", "doTerminate").doesNotContain("Reached max steps");
        assertThat(agent.getMessageList().stream().filter(ToolResponseMessage.class::isInstance)).hasSize(2);
    }

    @Test
    void returnsFinalAnswerWithoutRepeatingTheModelCall() {
        AtomicInteger calls = new AtomicInteger();
        ToolCallAgent agent = agent(new TestTools(), prompt -> {
            calls.incrementAndGet();
            return new ChatResponse(List.of(new Generation(new AssistantMessage("任务已完成"))));
        });
        agent.setMaxSteps(1);

        assertThat(agent.run("finish")).contains("任务已完成").doesNotContain("Reached max steps");
        assertThat(calls.get()).isEqualTo(1);
        assertThat(agent.getState()).isEqualTo(AgentState.FINISHED);
    }

    @Test
    void stopsOnModelFailureInsteadOfReportingSuccessfulThinking() {
        AtomicInteger calls = new AtomicInteger();
        ToolCallAgent agent = agent(new TestTools(), prompt -> {
            calls.incrementAndGet();
            throw new IllegalStateException("model unavailable");
        });

        assertThat(agent.run("test failure")).contains("执行错误", "model unavailable");
        assertThat(calls.get()).isEqualTo(1);
        assertThat(agent.getState()).isEqualTo(AgentState.ERROR);
    }

    @Test
    void rejectsEmptyModelResponses() {
        ToolCallAgent agent = agent(new TestTools(), prompt -> new ChatResponse(List.of()));

        assertThat(agent.run("test empty response")).contains("执行错误");
        assertThat(agent.getState()).isEqualTo(AgentState.ERROR);
    }

    @Test
    void stopsWhenTheRequestedToolDoesNotExist() {
        AtomicInteger calls = new AtomicInteger();
        ToolCallAgent agent = agent(new TestTools(), prompt -> {
            calls.incrementAndGet();
            return toolResponse("unknownTool", "{}");
        });

        assertThat(agent.run("test missing tool")).contains("执行错误", "unknownTool");
        assertThat(calls.get()).isEqualTo(1);
        assertThat(agent.getState()).isEqualTo(AgentState.ERROR);
    }

    @Test
    void reachingTheStepLimitIsNotSuccessfulCompletion() {
        ToolCallAgent agent = agent(new TestTools(), prompt -> toolResponse("echo", "{\"text\":\"again\"}"));
        agent.setMaxSteps(1);

        assertThat(agent.run("keep going")).contains("执行错误", "Reached max steps");
        assertThat(agent.getState()).isEqualTo(AgentState.ERROR);
    }

    @Test
    void actBeforeThinkingDoesNotDereferenceANullResponse() {
        ToolCallAgent agent = agent(new TestTools(), prompt -> null);

        assertThat(agent.act()).isEqualTo("没有工具调用");
    }

    private ToolCallAgent agent(TestTools tools, ChatModel model) {
        ToolCallAgent agent = new ToolCallAgent(ToolCallbacks.from(tools));
        agent.setName("test-agent");
        agent.setSystemPrompt("Use the supplied tools.");
        agent.setChatClient(ChatClient.builder(model).build());
        return agent;
    }

    private static ChatResponse toolResponse(String name, String arguments) {
        AssistantMessage message = AssistantMessage.builder().content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-" + name, "function", name, arguments)))
                .build();
        return new ChatResponse(List.of(new Generation(message)));
    }

    public static class TestTools {
        int echoCalls;
        int terminateCalls;

        @Tool(description = "Echo the supplied text")
        public String echo(String text) {
            echoCalls++;
            return text;
        }

        @Tool(description = "Finish the task")
        public String doTerminate() {
            terminateCalls++;
            return "finished";
        }
    }
}
