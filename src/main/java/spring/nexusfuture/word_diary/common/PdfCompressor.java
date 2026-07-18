package spring.nexusfuture.word_diary.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * PDF 压缩工具
 * 通过调用系统 Ghostscript (gs) 命令压缩 PDF 文件。
 * 对于文字为主的 PDF（电子书），使用 /ebook 级别，
 * 文字保持矢量清晰，仅压缩嵌入图片到 150dpi。
 */
@Slf4j
@Component
public class PdfCompressor {

    /** 超过此时长未完成则放弃压缩，避免上传接口长时间挂起 */
    private static final long COMPRESS_TIMEOUT_SEC = 60;

    private final AtomicReference<Boolean> availableCache = new AtomicReference<>();

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
            StringBuilder sb = new StringBuilder();
            Thread gobbler = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append('\n');
                    }
                } catch (IOException ignored) {
                }
            }, "gs-output-gobbler");
            gobbler.setDaemon(true);
            gobbler.start();

            boolean finished = process.waitFor(COMPRESS_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("PDF 压缩超时({}s)，改用原文件", COMPRESS_TIMEOUT_SEC);
                return readAll(tempInput);
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("Ghostscript 压缩失败, exitCode={}, output={}", exitCode, sb);
                throw new IOException("PDF 压缩失败: Ghostscript 返回错误码 " + exitCode);
            }

            long compressedSize = tempOutput.length();
            byte[] compressedData = readAll(tempOutput);

            double ratio = (1.0 - (double) compressedSize / originalSize) * 100;
            log.info("PDF 压缩完成: {} bytes -> {} bytes, 压缩率 {:.1f}%", originalSize, compressedSize, ratio);

            if (compressedSize >= originalSize) {
                log.info("压缩后文件未变小，返回原始数据");
                return readAll(tempInput);
            }

            return compressedData;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("PDF 压缩被中断", e);
        } finally {
            if (tempInput != null) tempInput.delete();
            if (tempOutput != null) tempOutput.delete();
        }
    }

    private static byte[] readAll(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            fis.transferTo(bos);
            return bos.toByteArray();
        }
    }

    /**
     * 检查系统是否安装了 Ghostscript（结果缓存，避免每次上传都起进程）
     */
    public boolean isAvailable() {
        Boolean cached = availableCache.get();
        if (cached != null) {
            return cached;
        }
        boolean ok = false;
        try {
            ProcessBuilder pb = new ProcessBuilder("gs", "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String version = reader.readLine();
                    log.info("Ghostscript 可用, 版本: {}", version);
                }
                ok = true;
            }
        } catch (Exception e) {
            log.warn("Ghostscript 不可用: {}", e.getMessage());
        }
        availableCache.set(ok);
        return ok;
    }
}
