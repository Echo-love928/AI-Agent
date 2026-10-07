package com.ai.aiagent.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

/**
 * 自定义 Re2 Advisor
 * 可提高大型语言模型的推理能力
 */
public class ReReadingAdvisor implements CallAdvisor, StreamAdvisor {

    private ChatClientRequest before(ChatClientRequest request) {
        String userText = request.prompt().getUserMessage().getText();
        String reReadingText = """
                %s
                Read the question again: %s
                """.formatted(userText, userText);

        return request.mutate()
                .prompt(request.prompt().augmentUserMessage(reReadingText))
                .build();
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        return chain.nextCall(this.before(request));
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return chain.nextStream(this.before(request));
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }
}
