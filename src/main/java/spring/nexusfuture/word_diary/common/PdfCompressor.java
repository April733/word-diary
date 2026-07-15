package spring.nexusfuture.word_diary.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;

/**
 * PDF 压缩工具
 * 通过调用系统 Ghostscript (gs) 命令压缩 PDF 文件。
 * 对于文字为主的 PDF（电子书），使用 /ebook 级别，
 * 文字保持矢量清晰，仅压缩嵌入图片到 150dpi。
 */
@Slf4j
@Component
public class PdfCompressor {

    /**
     * 压缩 PDF 文件
     *
     * @param inputStream 原始 PDF 输入流
     * @return 压缩后的字节数组
     * @throws IOException 如果压缩失败
     */
    public byte[] compress(InputStream inputStream) throws IOException {
        File tempInput = null;
        File tempOutput = null;
        try {
            // 写入临时文件
            tempInput = File.createTempFile("pdf_upload_", ".pdf");
            tempOutput = File.createTempFile("pdf_compressed_", ".pdf");
            try (FileOutputStream fos = new FileOutputStream(tempInput)) {
                inputStream.transferTo(fos);
            }

            long originalSize = tempInput.length();
            log.info("开始压缩 PDF: 原始大小={} bytes", originalSize);

            // 调用 Ghostscript 压缩
            // /ebook: 中等压缩，图片 150dpi，适合电子书阅读
            ProcessBuilder pb = new ProcessBuilder(
                    "gs",
                    "-sDEVICE=pdfwrite",
                    "-dCompatibilityLevel=1.4",
                    "-dPDFSETTINGS=/ebook",
                    "-dNOPAUSE",
                    "-dQUIET",
                    "-dBATCH",
                    "-sOutputFile=" + tempOutput.getAbsolutePath(),
                    tempInput.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 读取 stderr/stdout 避免进程阻塞
            String gsOutput;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                gsOutput = sb.toString();
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("Ghostscript 压缩失败, exitCode={}, output={}", exitCode, gsOutput);
                throw new IOException("PDF 压缩失败: Ghostscript 返回错误码 " + exitCode);
            }

            // 读取压缩后的文件
            long compressedSize = tempOutput.length();
            byte[] compressedData;
            try (FileInputStream fis = new FileInputStream(tempOutput);
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                fis.transferTo(bos);
                compressedData = bos.toByteArray();
            }

            double ratio = (1.0 - (double) compressedSize / originalSize) * 100;
            log.info("PDF 压缩完成: {} bytes -> {} bytes, 压缩率 {:.1f}%", originalSize, compressedSize, ratio);

            // 如果压缩后反而更大，返回原始数据
            if (compressedSize >= originalSize) {
                log.info("压缩后文件未变小，返回原始数据");
                byte[] originalData;
                try (FileInputStream fis = new FileInputStream(tempInput);
                     ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                    fis.transferTo(bos);
                    originalData = bos.toByteArray();
                }
                return originalData;
            }

            return compressedData;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("PDF 压缩被中断", e);
        } finally {
            // 清理临时文件
            if (tempInput != null) tempInput.delete();
            if (tempOutput != null) tempOutput.delete();
        }
    }

    /**
     * 检查系统是否安装了 Ghostscript
     */
    public boolean isAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("gs", "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String version = reader.readLine();
                    log.info("Ghostscript 可用, 版本: {}", version);
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("Ghostscript 不可用: {}", e.getMessage());
            return false;
        }
    }
}
