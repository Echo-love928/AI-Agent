package com.ai.aiagent.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration;
import org.springframework.ai.mcp.client.common.autoconfigure.configurer.McpSyncClientConfigurer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpClientConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(McpClientAutoConfiguration.class))
            .withUserConfiguration(McpClientConfiguration.class);

    @Test
    void allowsSlowHandshakeWithinConfiguredRequestTimeout() {
        contextRunner.withPropertyValues("spring.ai.mcp.client.request-timeout=2s").run(context -> {
            assertThat(context).hasNotFailed();
            // 缩短时间复现原问题：仅设置请求超时，初始化仍按独立的较短时限失败。
            try (var client = McpClient.sync(new DelayedTransport(Duration.ofMillis(300)))
                    .requestTimeout(Duration.ofSeconds(2))
                    .initializationTimeout(Duration.ofMillis(50)).build()) {
                assertThatThrownBy(client::initialize).hasRootCauseInstanceOf(TimeoutException.class);
            }
            var spec = McpClient.sync(new DelayedTransport(Duration.ofMillis(300)))
                    .requestTimeout(Duration.ofSeconds(2))
                    .initializationTimeout(Duration.ofMillis(50));
            context.getBean(McpSyncClientConfigurer.class).configure("slow-server", spec);

            try (var client = spec.build()) {
                assertThat(client.initialize().serverInfo().name()).isEqualTo("delayed-server");
            }
        });
    }

    @Test
    void stillTimesOutWhenHandshakeExceedsConfiguredLimit() {
        contextRunner.withPropertyValues("spring.ai.mcp.client.request-timeout=100ms").run(context -> {
            assertThat(context).hasNotFailed();
            var spec = McpClient.sync(new DelayedTransport(Duration.ofSeconds(5)))
                    .requestTimeout(Duration.ofSeconds(10));
            context.getBean(McpSyncClientConfigurer.class).configure("unresponsive-server", spec);

            try (var client = spec.build()) {
                assertThatThrownBy(client::initialize).hasRootCauseInstanceOf(TimeoutException.class);
            }
        });
    }

    private static class DelayedTransport implements McpClientTransport {

        private final Duration delay;
        private Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler;

        DelayedTransport(Duration delay) {
            this.delay = delay;
        }

        @Override
        public Mono<Void> connect(
                Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler) {
            this.handler = handler;
            return Mono.empty();
        }

        @Override
        public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
            if (message instanceof McpSchema.JSONRPCRequest request
                    && McpSchema.METHOD_INITIALIZE.equals(request.method())) {
                var result = new McpSchema.InitializeResult("2024-11-05",
                        McpSchema.ServerCapabilities.builder().build(),
                        new McpSchema.Implementation("delayed-server", "1.0"), null);
                return handler.apply(Mono.delay(delay).map(ignored ->
                        new McpSchema.JSONRPCResponse("2.0", request.id(), result, null))).then();
            }
            return Mono.empty();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
            return (T) data;
        }

        @Override
        public Mono<Void> closeGracefully() {
            return Mono.empty();
        }
    }
}
