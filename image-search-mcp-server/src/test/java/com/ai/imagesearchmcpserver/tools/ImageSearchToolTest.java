package com.ai.imagesearchmcpserver.tools;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ImageSearchToolTest {

    @Resource
    private ImageSearchTool imageSearchTool;

    @Test
    void searchImage() {
        String result = imageSearchTool.searchImage("computer");
        assertNotNull(result);
        assertFalse(result.startsWith("Error search image:"), result);
        assertFalse(result.isBlank(), "没有返回图片地址");
        String[] imageUrls = result.split(",");
        System.out.println("搜索到 " + imageUrls.length + " 张图片：");
        for (String imageUrl : imageUrls) {
            assertTrue(imageUrl.startsWith("https://"), "图片地址无效: " + imageUrl);
            System.out.println(imageUrl);
        }
    }
}
