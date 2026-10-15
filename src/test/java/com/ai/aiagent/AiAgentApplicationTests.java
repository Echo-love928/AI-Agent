package com.ai.aiagent;

import com.ai.aiagent.demo.invoke.SpringAiAiInvoke;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-key")
@AutoConfigureMockMvc
class AiAgentApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    // 替换启动时的知识库 Bean，避免上下文测试调用真实嵌入模型。
    @MockitoBean(name = "mindAppVectorStore")
    private VectorStore mindAppVectorStore;

    // 启动演示会调用聊天模型，上下文测试只验证装配，不执行演示。
    @MockitoBean
    private SpringAiAiInvoke springAiAiInvoke;

    @Test
    void contextLoads() {
    }

    @Test
    void exposesSwaggerDocumentationAfterMcpStartup() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/ai/mind_app/chat/sync']").exists())
                .andExpect(jsonPath("$.paths['/ai/mind_app/chat/sse']").exists());
        mockMvc.perform(get("/doc.html")).andExpect(status().isOk());
    }

}
