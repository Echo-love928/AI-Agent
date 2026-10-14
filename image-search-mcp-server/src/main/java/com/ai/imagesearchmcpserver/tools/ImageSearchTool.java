package com.ai.imagesearchmcpserver.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ImageSearchTool {

    // pixabay API 密钥（需从官网申请）
    private final String apiKey;

    // pixabay 常规搜索接口（请以文档为准）
    private static final String API_URL = "https://pixabay.com/api/";

    public ImageSearchTool(@Value("${pixabay.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Tool(description = "search image from web")
    public String searchImage(@ToolParam(description = "Search query keyword") String query) {
        try {
            return String.join(",", searchMediumImages(query));
        } catch (Exception e) {
            return "Error search image: " + e.getMessage();
        }
    }

    /**
     * 搜索中等尺寸的图片列表
     *
     * @param query 搜索关键词，最多 100 个字符
     * @return Pixabay 返回的中等尺寸图片地址；没有结果时返回空列表
     */
    public List<String> searchMediumImages(String query) {
        if (StrUtil.isBlank(query)) {
            throw new IllegalArgumentException("Search query must not be blank");
        }
        if (query.codePointCount(0, query.length()) > 100) {
            throw new IllegalArgumentException("Search query must not exceed 100 characters");
        }
        if (StrUtil.isBlank(apiKey)) {
            throw new IllegalStateException("Pixabay API key is not configured; set PIXABAY_API_KEY");
        }

        // Pixabay 使用 key 参数认证，关键词参数为 q；Hutool 负责 URL 编码。
        Map<String, Object> params = new HashMap<>();
        params.put("key", apiKey);
        params.put("q", query);

        try (HttpResponse response = HttpUtil.createGet(API_URL)
                .form(params)
                .timeout(20_000)
                .execute()) {
            String body = response.body();
            // Pixabay 的错误正文是纯文本，必须先检查状态码再解析 JSON。
            if (!response.isOk()) {
                String error = StrUtil.blankToDefault(body, "Empty response")
                        .replace(apiKey, "[REDACTED]");
                throw new IllegalStateException("Pixabay API HTTP " + response.getStatus()
                        + ": " + error);
            }

            JSONArray hits = JSONUtil.parseObj(body).getJSONArray("hits");
            if (hits == null) {
                throw new IllegalStateException("Pixabay API response is missing hits");
            }
            return hits.stream()
                    .map(hit -> (JSONObject) hit)
                    .map(hit -> hit.getStr("webformatURL"))
                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.toList());
        }
    }
}
