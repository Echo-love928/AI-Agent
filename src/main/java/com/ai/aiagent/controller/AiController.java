package com.ai.aiagent.controller;

import com.ai.aiagent.agent.Manus;
import com.ai.aiagent.app.MindApp;
import com.ai.aiagent.dto.ManusChatRequest;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;

@RestController
@RequestMapping("/ai")
public class AiController {

    private static final String SSE_UTF8 = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8";

    @Resource
    private MindApp MindApp;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    @GetMapping("/mind_app/chat/sync")
    public String doChatWithMindAppSync(String message, String chatId) {
        return MindApp.doChat(message, chatId);
    }

    @GetMapping(value = "/mind_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithMindAppSSE(String message, String chatId) {
        return MindApp.doChatByStream(message, chatId);
    }

    /**
     * 流式调用 Manus 超级智能体
     *
     * @param message
     * @return
     */
    @GetMapping(value = "/manus/chat", produces = SSE_UTF8)
    public SseEmitter doChatWithManus(String message) {
        Manus Manus = new Manus(allTools, dashscopeChatModel);
        return Manus.runStream(message);
    }

    /** 新前端通过请求体传递完整对话，避免 GET 查询参数长度限制。 */
    @PostMapping(value = "/manus/chat", produces = SSE_UTF8)
    public SseEmitter doChatWithManus(@RequestBody ManusChatRequest request) {
        var history = request.validatedHistory();
        Manus manus = new Manus(allTools, dashscopeChatModel);
        manus.setMessageList(history);
        return manus.runStream(request.message());
    }


}
