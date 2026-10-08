package com.ai.aiagent;

import com.ai.aiagent.demo.invoke.SpringAiAiInvoke;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-key")
class AiAgentApplicationTests {

    // 替换启动时的知识库 Bean，避免上下文测试调用真实嵌入模型。
    @MockitoBean(name = "mindAppVectorStore")
    private VectorStore mindAppVectorStore;

    // 启动演示会调用聊天模型，上下文测试只验证装配，不执行演示。
    @MockitoBean
    private SpringAiAiInvoke springAiAiInvoke;

    @Test
    void contextLoads() {
    }

}
