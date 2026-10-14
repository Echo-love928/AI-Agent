package com.ai.aiagent.app;

import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStdioClientProperties;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class McpStdioConfigurationTests {

    @Test
    void parsesLocalServerConfiguration() {
        ClassPathResource resource = new ClassPathResource("mcp-servers.json");
        assumeTrue(resource.exists(), "本地 MCP 配置未提供");
        assertServerConfiguration(resource);
    }

    @Test
    void parsesExampleServerConfiguration() {
        assertServerConfiguration(new ClassPathResource("mcp-servers.example.json"));
    }

    private void assertServerConfiguration(ClassPathResource resource) {
        McpStdioClientProperties properties = new McpStdioClientProperties();
        properties.setServersConfiguration(resource);
        var servers = properties.toServerParameters();
        assertThat(servers).containsKeys("amap-maps", "image-search-mcp-server")
                .doesNotContainKey("mcpServers");
        assertThat(servers.get("image-search-mcp-server").getCommand()).isEqualTo("java");
        assertThat(servers.get("image-search-mcp-server").getArgs())
                .contains("-jar", "image-search-mcp-server/target/image-search-mcp-server-0.0.1-SNAPSHOT.jar");
    }
}
