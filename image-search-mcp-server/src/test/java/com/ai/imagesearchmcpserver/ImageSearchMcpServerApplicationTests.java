package com.ai.imagesearchmcpserver;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class ImageSearchMcpServerApplicationTests {

    @Autowired
    private ToolCallbackProvider toolCallbackProvider;

    @Test
    void contextLoads() {
        ToolCallback[] callbacks = toolCallbackProvider.getToolCallbacks();
        assertEquals(1, callbacks.length);
        assertEquals("searchImage", callbacks[0].getToolDefinition().name());
    }

}
