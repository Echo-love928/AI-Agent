package com.ai.aiagent.app;

import com.ai.aiagent.constant.FileConstant;
import com.ai.aiagent.demo.invoke.SpringAiAiInvoke;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 配置真实 API Key，并设置 RUN_TOOL_INTEGRATION_TESTS=true 后运行。
 * 记录真实工具回调，分别检查“是否触发”和“是否执行成功”。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_TOOL_INTEGRATION_TESTS", matches = "true")
class MindAppToolsIntegrationTests {

    @Autowired
    private MindApp mindApp;

    @Resource
    private ToolCallback[] allTools;

    @MockitoBean(name = "mindAppVectorStore")
    private VectorStore mindAppVectorStore;

    @MockitoBean
    private SpringAiAiInvoke springAiAiInvoke;

    private ObservedTool[] observedTools;

    @BeforeEach
    void observeRealTools() {
        observedTools = Arrays.stream(allTools).map(ObservedTool::new).toArray(ObservedTool[]::new);
        ReflectionTestUtils.setField(mindApp, "allTools", observedTools);
    }

    @AfterEach
    void restoreTools() {
        ReflectionTestUtils.setField(mindApp, "allTools", allTools);
    }

    static Stream<Arguments> toolPrompts() {
        return Stream.of(
                Arguments.of("searchWeb", """
                        周末想和伴侣在上海散步减压。请实际调用 searchWeb 工具，
                        query 为“上海 情侣 安静 散步 公园”，再根据搜索结果推荐三个地点。
                        """, ""),
                Arguments.of("scrapeWebPage", """
                        我想了解一个学习社区作为下班后转换注意力的选择。
                        请实际调用 scrapeWebPage 工具，url 为“https://www.codefather.cn”，
                        再根据网页内容简要介绍网站，不要凭记忆回答。
                        """, "<html"),
                Arguments.of("downloadResource", """
                        请实际调用 downloadResource 工具，
                        url 为“https://www.codefather.cn/logo.png”，
                        fileName 为“mind-resource-%s.png”，并报告下载结果。
                        """, "Resource downloaded successfully"),
                Arguments.of("executeTerminalCommand", """
                        请实际调用 executeTerminalCommand 工具，command 为以下完整命令：
                        python -c "import json; scores=[7,5,4]; print(json.dumps({'average_stress':sum(scores)/len(scores)}))"
                        这是虚构压力数据的统计测试，请返回真实终端输出，不要只写代码。
                        """, "average_stress"),
                Arguments.of("writeFile", """
                        我同意保存以下虚构测试档案：昵称：测试小明；困扰：工作压力；目标：每天休息十分钟。
                        请实际调用 writeFile 工具，fileName 为“mind-profile-%s.txt”，
                        content 为上述档案全文，并报告保存结果。
                        """, "File written successfully"),
                Arguments.of("readFile", """
                        请实际调用 readFile 工具，fileName 为“mind-profile-%s.txt”，
                        读取已保存的虚构测试档案并告诉我其中的内容，不要猜测。
                        """, "测试小明"),
                Arguments.of("generatePDF", """
                        请实际调用 generatePDF 工具，fileName 为“weekend-plan-%s.pdf”，
                        content 为“周末减压计划：上午在公园散步半小时；中午与伴侣吃午饭；
                        下午休息一小时；晚上记录心情。准备清单：饮用水、舒适的鞋、笔记本。”
                        请生成文件并报告保存结果，不要仅在回复中展示计划。
                        """, "PDF generated successfully"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("toolPrompts")
    void doChatWithTools(String toolName, String prompt, String expectedResult) throws Exception {
        String chatId = UUID.randomUUID().toString();
        if ("readFile".equals(toolName)) {
            Path file = Path.of(FileConstant.FILE_SAVE_DIR, "file", "mind-profile-" + chatId + ".txt");
            Files.createDirectories(file.getParent());
            Files.writeString(file, "昵称：测试小明；困扰：工作压力；目标：每天休息十分钟。", StandardCharsets.UTF_8);
        }

        String answer;
        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> exactArguments = switch (toolName) {
            case "writeFile" -> Map.of("fileName", "mind-profile-" + chatId + ".txt", "content",
                    "昵称：测试小明；困扰：工作压力；目标：每天休息十分钟。");
            case "readFile" -> Map.of("fileName", "mind-profile-" + chatId + ".txt");
            case "executeTerminalCommand" -> Map.of("command",
                    "\"" + System.getProperty("tool.test.python", "python")
                            + "\" -c \"import json; scores=[7,5,4]; print(json.dumps({'average_stress':sum(scores)/len(scores)}))\"");
            default -> Map.of();
        };
        String message = prompt.formatted(chatId)
                + "\n请仅调用上述指定工具，逐字保留参数，不得改名或改用其他命令。";
        if (!exactArguments.isEmpty()) {
            message += "\n最终参数以此 JSON 为准：" + mapper.writeValueAsString(exactArguments);
        }
        try {
            answer = mindApp.doChatWithTools(message, chatId);
        } finally {
            for (ObservedTool tool : observedTools) {
                if (tool.calls > 0) {
                    String result = tool.result == null ? "<exception>" : tool.result;
                    System.out.printf("TOOL_CALL name=%s count=%d input=%s result=%s%n", tool.getToolDefinition().name(),
                            tool.calls, tool.input, result.substring(0, Math.min(300, result.length())));
                }
            }
        }

        ObservedTool tool = Arrays.stream(observedTools)
                .filter(callback -> toolName.equals(callback.getToolDefinition().name()))
                .findFirst().orElseThrow();
        assertThat(tool.calls).as("模型是否实际调用 %s；回复：%s", toolName, answer).isPositive();
        assertThat(answer).isNotBlank();
        for (Map.Entry<String, String> entry : exactArguments.entrySet()) {
            assertThat(mapper.readTree(tool.input).path(entry.getKey()).asText())
                    .as("%s 的 %s 参数", toolName, entry.getKey()).isEqualTo(entry.getValue());
        }
        String result = tool.result.startsWith("\"")
                ? mapper.readValue(tool.result, String.class) : tool.result;
        assertThat(result).as("%s 的真实执行结果", toolName).isNotBlank()
                .doesNotStartWith("Error")
                .doesNotContain("Command execution failed")
                .contains(expectedResult);
        Path artifact = switch (toolName) {
            case "writeFile" -> Path.of(FileConstant.FILE_SAVE_DIR, "file", "mind-profile-" + chatId + ".txt");
            case "downloadResource" -> Path.of(FileConstant.FILE_SAVE_DIR, "download", "mind-resource-" + chatId + ".png");
            case "generatePDF" -> Path.of(FileConstant.FILE_SAVE_DIR, "pdf", "weekend-plan-" + chatId + ".pdf");
            default -> null;
        };
        if (artifact != null) {
            assertThat(artifact).isRegularFile();
            assertThat(Files.size(artifact)).isPositive();
            System.out.printf("TOOL_ARTIFACT name=%s path=%s%n", toolName, artifact);
        }
    }

    private static class ObservedTool implements ToolCallback {
        private final ToolCallback delegate;
        private int calls;
        private String input;
        private String result;

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
            calls++;
            this.input = input;
            result = delegate.call(input);
            return result;
        }

        @Override
        public String call(String input, ToolContext context) {
            calls++;
            this.input = input;
            result = delegate.call(input, context);
            return result;
        }
    }
}
