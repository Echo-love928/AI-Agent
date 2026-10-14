package com.ai.aiagent.agent;

import jakarta.annotation.Resource;
import com.ai.aiagent.agent.model.AgentState;
import com.ai.aiagent.constant.FileConstant;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ManusTest {

    @Resource
    private Manus manus;

    @Test
    void run() throws Exception {
        String fileName = "manus-stress-plan-" + UUID.randomUUID() + ".pdf";
        Path pdfPath = Path.of(FileConstant.FILE_SAVE_DIR, "pdf", fileName);
        String userPrompt = """
                我最近工作压力有点大，想出去找个公园散散步，我居住在广东潮州，
                帮我制定一份能够缓解职场压力的计划，
                请实际调用 generatePDF 工具，以PDF格式输出完整计划，文件名为 %s。
                完成后告知文件路径，并调用 doTerminate 结束任务。
                """.formatted(fileName);
        String answer = manus.run(userPrompt);
        System.out.println(answer);
        assertThat(manus.getState()).as(answer).isEqualTo(AgentState.FINISHED);
        assertThat(answer).isNotBlank().doesNotContain("执行错误", "Reached max steps", "Error generating PDF");
        assertThat(Files.isRegularFile(pdfPath)).as("应实际生成新的 PDF: %s", pdfPath).isTrue();
        try (PdfDocument pdf = new PdfDocument(new PdfReader(pdfPath.toString()))) {
            assertThat(pdf.getNumberOfPages()).isPositive();
            StringBuilder content = new StringBuilder();
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                content.append(PdfTextExtractor.getTextFromPage(pdf.getPage(page)));
            }
            assertThat(content.toString()).contains("潮州", "压力");
        }
        System.out.println("Generated PDF: " + pdfPath);
    }
}
