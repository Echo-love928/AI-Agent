package com.ai.aiagent.rag;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MindAppDocumentLoaderTests {

    @Test
    void resourceReadFailureDoesNotReturnPartialKnowledgeBase() throws IOException {
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        Resource unreadable = mock(Resource.class);
        when(unreadable.getFilename()).thenReturn("unreadable.md");
        when(unreadable.getInputStream()).thenThrow(new IOException("读取失败"));
        when(resolver.getResources(anyString())).thenReturn(new Resource[]{
                new ByteArrayResource("# 正常文档\n正文".getBytes(StandardCharsets.UTF_8)) {
                    @Override
                    public String getFilename() {
                        return "valid.md";
                    }
                }, unreadable
        });

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> new MindAppDocumentLoader(resolver).loadMarkdowns())
                .withRootCauseInstanceOf(IOException.class);
    }

    @Test
    void resourceScanFailureAbortsInitializationInsteadOfReturningEmptyDocuments() throws IOException {
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        when(resolver.getResources(anyString())).thenThrow(new IOException("扫描失败"));

        assertThatExceptionOfType(UncheckedIOException.class)
                .isThrownBy(() -> new MindAppDocumentLoader(resolver).loadMarkdowns())
                .withMessage("Markdown 文档加载失败");
    }
}
