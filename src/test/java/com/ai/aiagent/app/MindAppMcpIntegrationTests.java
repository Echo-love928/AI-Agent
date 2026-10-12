package com.ai.aiagent.app;

import com.ai.aiagent.demo.invoke.SpringAiAiInvoke;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 配置 DashScope 和地图 MCP 服务后，设置 RUN_MCP_INTEGRATION_TESTS=true 运行。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_MCP_INTEGRATION_TESTS", matches = "true")
class MindAppMcpIntegrationTests {

    @Resource
    private MindApp mindApp;

    // 该测试只验证 MCP 对话，避免初始化知识库时调用嵌入模型。
    @MockitoBean(name = "mindAppVectorStore")
    private VectorStore mindAppVectorStore;

    // 避免启动示例产生额外的模型调用。
    @MockitoBean
    private SpringAiAiInvoke springAiAiInvoke;

    @Test
    void mapServiceReturnsSearchResults() throws Exception {
        ToolCallbackProvider provider = (ToolCallbackProvider)
                ReflectionTestUtils.getField(mindApp, "toolCallbackProvider");
        ToolCallback searchTool = Arrays.stream(provider.getToolCallbacks())
                .filter(tool -> tool.getToolDefinition().name().endsWith("maps_text_search"))
                .findFirst().orElseThrow(() -> new AssertionError("MCP 服务未提供地点搜索工具"));
        String result = searchTool.call("{\"keywords\":\"公园\",\"city\":\"潮州\"}");
        Path resultFile = Path.of("tmp", "mcp-map-callback-result.json");
        Files.createDirectories(resultFile.getParent());
        Files.writeString(resultFile, result);
        assertThat(readPlaceNames(result)).as("地图 MCP 应返回真实地点").isNotEmpty();
    }

    @RepeatedTest(3)
    void doChatWithMcp() throws Exception {
        String chatId = UUID.randomUUID().toString();
        String message = "今天工作很烦，想出去走走，我住在广东潮州，想找个公园";

        ToolCallbackProvider originalProvider = (ToolCallbackProvider)
                ReflectionTestUtils.getField(mindApp, "toolCallbackProvider");
        assertThat(originalProvider).isNotNull();
        ObservedTool[] observedTools = Arrays.stream(originalProvider.getToolCallbacks())
                .map(ObservedTool::new).toArray(ObservedTool[]::new);
        assertThat(observedTools).as("配置中的 MCP 服务应提供工具").isNotEmpty();
        // 只记录真实调用，不指定 tool_choice，也不手动执行工具。
        ToolCallbackProvider observedProvider = () -> observedTools;
        ReflectionTestUtils.setField(mindApp, "toolCallbackProvider", observedProvider);
        String answer;
        try {
            answer = mindApp.doChatWithMcp(message, chatId);
        } finally {
            ReflectionTestUtils.setField(mindApp, "toolCallbackProvider", originalProvider);
            for (ObservedTool tool : observedTools) {
                for (Invocation invocation : tool.invocations) {
                    System.out.printf("MCP_CALL name=%s input=%s success=%s%n",
                            tool.getToolDefinition().name(), invocation.input(), invocation.result() != null);
                }
            }
        }

        System.out.printf("用户：%s%nAI：%s%n", message, answer);
        assertThat(answer).isNotBlank();

        List<Invocation> searches = Arrays.stream(observedTools)
                .filter(tool -> tool.getToolDefinition().name().endsWith("maps_text_search")
                        || tool.getToolDefinition().name().endsWith("maps_around_search"))
                .flatMap(tool -> tool.invocations.stream()).toList();
        ObjectMapper mapper = new ObjectMapper();
        Path traceFile = Path.of("tmp", "mcp-observed-calls-" + chatId + ".json");
        Files.createDirectories(traceFile.getParent());
        Files.writeString(traceFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(
                Arrays.stream(observedTools).filter(tool -> !tool.invocations.isEmpty())
                        .map(tool -> Map.of("name", tool.getToolDefinition().name(),
                                "invocations", tool.invocations)).toList()));
        assertThat(searches).as("模型应自动调用地图搜索工具；实际回复：%s", answer).isNotEmpty();
        assertThat(Arrays.stream(observedTools).flatMap(tool -> tool.invocations.stream()).toList())
                .as("地图查询参数应使用用户提供的潮州位置")
                .anyMatch(invocation -> invocation.input().contains("潮州") || invocation.input().contains("445100"));
        List<String> parkNames = new ArrayList<>();
        for (Invocation search : searches) {
            if (search.result() == null) {
                continue;
            }
            parkNames.addAll(readPlaceNames(search.result()));
        }
        assertThat(parkNames).as("真实地图查询应返回地点，不能只有工具调用记录").isNotEmpty();
        assertThat(parkNames).as("最终回复应引用真实查询返回的至少一个地点")
                .anyMatch(answer::contains);
        System.out.printf("MCP_VERIFIED searches=%d places=%d referenced=%s%n", searches.size(), parkNames.size(),
                parkNames.stream().filter(answer::contains).toList());
    }

    private List<String> readPlaceNames(String result) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode content = mapper.readTree(result);
        assertThat(content.isArray()).as("MCP 地图结果应为内容数组").isTrue();
        List<String> names = new ArrayList<>();
        for (JsonNode item : content) {
            // Spring AI 序列化的 TextContent 可能只有 text，没有 type 字段。
            if (item.hasNonNull("text")) {
                JsonNode payload = mapper.readTree(item.path("text").asText());
                for (JsonNode poi : payload.path("pois")) {
                    String name = poi.path("name").asText();
                    if (!name.isBlank()) {
                        names.add(name);
                    }
                }
            }
        }
        return names;
    }

    private record Invocation(String input, String result) {
    }

    private static class ObservedTool implements ToolCallback {
        private final ToolCallback delegate;
        private final List<Invocation> invocations = new ArrayList<>();

        ObservedTool(ToolCallback delegate) {
            this.delegate = delegate;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return delegate.getToolMetadata();
        }

        @Override
        public String call(String input) {
            return call(input, null);
        }

        @Override
        public String call(String input, ToolContext context) {
            String result = null;
            try {
                result = delegate.call(input, context);
                return result;
            } finally {
                invocations.add(new Invocation(input, result));
            }
        }
    }
}
