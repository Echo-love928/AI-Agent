package com.ai.aiagent.dto;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/** 仅允许客户端传入当前对话的用户和最终助手消息，不接受系统提示或工具消息。 */
public record ManusChatRequest(String message, List<Turn> history) {
    public record Turn(String role, String content) { }

    public List<Message> validatedHistory() {
        if (message == null || message.isBlank() || message.length() > 6000) {
            throw invalid("问题不能为空且不能超过 6000 字符。");
        }
        List<Turn> turns = history == null ? List.of() : history;
        if (turns.size() > 20 || turns.size() % 2 != 0) throw invalid("会话历史最多包含 10 轮完整对话。");
        List<Message> messages = new ArrayList<>();
        int total = 0;
        for (int index = 0; index < turns.size(); index++) {
            Turn turn = turns.get(index);
            String expectedRole = index % 2 == 0 ? "user" : "assistant";
            if (turn == null || !expectedRole.equals(turn.role()) || turn.content() == null
                    || turn.content().isBlank() || turn.content().length() > 12000) {
                throw invalid("会话历史必须由完整的用户问题和助手回答组成。");
            }
            total += turn.content().length();
            if (total > 60000) throw invalid("会话历史内容过长。");
            messages.add(index % 2 == 0 ? new UserMessage(turn.content()) : new AssistantMessage(turn.content()));
        }
        return messages;
    }

    private static ResponseStatusException invalid(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
