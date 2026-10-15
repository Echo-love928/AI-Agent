package com.ai.aiagent.mcp;

import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.ai.mcp.customizer.McpSyncClientCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "spring.ai.mcp.client", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class McpClientConfiguration {

    @Bean
    public McpSyncClientCustomizer mcpInitializationTimeoutCustomizer(McpClientCommonProperties properties) {
        // SDK 默认的初始化超时是独立的 20 秒，Spring AI 不会自动随 request-timeout 调整它。
        return (name, spec) -> spec.initializationTimeout(properties.getRequestTimeout());
    }
}
