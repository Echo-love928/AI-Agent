package com.ai.imagesearchmcpserver.tools;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

class ImageSearchToolUnitTest {

    private final ImageSearchTool tool = new ImageSearchTool("test-pixabay-key");

    @Test
    void usesPixabayParametersAndMediumImageUrls() {
        withResponse(200, """
                {"hits":[
                  {"webformatURL":"https://pixabay.com/get/computer_640.jpg"},
                  {"webformatURL":""},
                  {"previewURL":"https://example.com/preview.jpg"}
                ]}
                """, request -> {
            assertEquals("https://pixabay.com/get/computer_640.jpg",
                    tool.searchImage("电脑 desk"));
            verify(request).form(argThat((Map<String, Object> params) ->
                    "电脑 desk".equals(params.get("q"))
                            && params.get("key") instanceof String key && !key.isBlank()
                            && !params.containsKey("query")));
            verify(request, never()).addHeaders(anyMap());
            verify(request).timeout(20_000);
        });
    }

    @Test
    void reportsAuthenticationErrorInsteadOfParsingPlainText() {
        withResponse(400, "[ERROR 400] Invalid or missing API key", request ->
                assertEquals("Error search image: Pixabay API HTTP 400: "
                                + "[ERROR 400] Invalid or missing API key",
                        tool.searchImage("computer")));
    }

    @Test
    void reportsRateLimitError() {
        withResponse(429, "API rate limit exceeded", request ->
                assertEquals("Error search image: Pixabay API HTTP 429: API rate limit exceeded",
                        tool.searchImage("computer")));
    }

    @Test
    void returnsEmptyListWhenNoImagesMatch() {
        withResponse(200, "{\"total\":0,\"totalHits\":0,\"hits\":[]}", request ->
                assertEquals(List.of(), tool.searchMediumImages("no-match")));
    }

    @Test
    void rejectsResponseWithoutHits() {
        withResponse(200, "{\"photos\":[]}", request ->
                assertEquals("Error search image: Pixabay API response is missing hits",
                        tool.searchImage("computer")));
    }

    @Test
    void rejectsMissingApiKeyBeforeMakingARequest() {
        try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {
            assertEquals("Error search image: Pixabay API key is not configured; set PIXABAY_API_KEY",
                    new ImageSearchTool("").searchImage("computer"));
            http.verifyNoInteractions();
        }
    }

    @Test
    void rejectsInvalidQueriesBeforeMakingARequest() {
        try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {
            assertThrows(IllegalArgumentException.class, () -> tool.searchMediumImages(null));
            assertThrows(IllegalArgumentException.class, () -> tool.searchMediumImages(" "));
            assertThrows(IllegalArgumentException.class,
                    () -> tool.searchMediumImages("a".repeat(101)));
            http.verifyNoInteractions();
        }
    }

    private void withResponse(int status, String body, Consumer<HttpRequest> assertions) {
        HttpRequest request = mock(HttpRequest.class);
        HttpResponse response = mock(HttpResponse.class);
        when(request.form(anyMap())).thenReturn(request);
        when(request.timeout(20_000)).thenReturn(request);
        when(request.execute()).thenReturn(response);
        when(response.isOk()).thenReturn(status >= 200 && status < 300);
        when(response.getStatus()).thenReturn(status);
        when(response.body()).thenReturn(body);

        try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.createGet("https://pixabay.com/api/")).thenReturn(request);
            assertions.accept(request);
            verify(response).close();
        }
    }
}
