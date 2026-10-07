package com.ai.aiagent.chatmemory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class FileBasedChatMemoryTests {

    @TempDir
    Path directory;

    @Test
    void persistsSpringAiMessagesAndReloadsThemThroughCurrentInterface() {
        ChatMemory memory = new FileBasedChatMemory(directory.toString());
        UserMessage question = UserMessage.builder().text("最近工作压力很大")
                .metadata(Map.of("source", "test")).build();
        memory.add("chat-1", question);
        memory.add("chat-1", List.of(new AssistantMessage("可以先梳理压力来源")));

        ChatMemory reloaded = new FileBasedChatMemory(directory.toString());
        List<Message> messages = reloaded.get("chat-1");

        assertThat(directory.resolve("chat-1.kryo")).exists();
        assertThat(messages).extracting(Message::getMessageType, Message::getText)
                .containsExactly(tuple(MessageType.USER, "最近工作压力很大"),
                        tuple(MessageType.ASSISTANT, "可以先梳理压力来源"));
        assertThat(messages.getFirst().getMetadata()).containsEntry("source", "test");
    }

    @Test
    void isolatesConversationsAndClearsOnlyRequestedConversation() {
        ChatMemory memory = new FileBasedChatMemory(directory.toString());
        assertThat(memory.get("missing-chat")).isEmpty();
        memory.add("chat-1", new UserMessage("工作问题"));
        memory.add("chat-2", new UserMessage("家庭问题"));

        memory.clear("chat-1");

        assertThat(memory.get("chat-1")).isEmpty();
        assertThat(directory.resolve("chat-1.kryo")).doesNotExist();
        assertThat(memory.get("chat-2")).extracting(Message::getText).containsExactly("家庭问题");
        memory.clear("missing-chat");
    }

    @Test
    void returnsAllStoredMessagesWithCurrentGetSignature() {
        ChatMemory memory = new FileBasedChatMemory(directory.resolve("nested").toString());
        for (int i = 1; i <= 12; i++) {
            memory.add("chat-1", new UserMessage("消息" + i));
        }

        assertThat(memory.get("chat-1")).hasSize(12)
                .extracting(Message::getText).startsWith("消息1").endsWith("消息12");
    }
}
