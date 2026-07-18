package spring.nexusfuture.word_diary.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import spring.nexusfuture.word_diary.common.BizException;
import spring.nexusfuture.word_diary.common.PdfCompressor;
import spring.nexusfuture.word_diary.entity.WdBook;
import spring.nexusfuture.word_diary.mapper.WdBookMapper;
import spring.nexusfuture.word_diary.service.BookService;
import spring.nexusfuture.word_diary.service.CosService;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookServiceImpl implements BookService {

    /** 每用户书架最多保留的书籍数（未删除） */
    public static final int MAX_BOOKS_PER_USER = 10;

    private final WdBookMapper bookMapper;
    private final CosService cosService;
    private final PdfCompressor pdfCompressor;

    @Override
    @Transactional
    public WdBook uploadBook(Long userId, MultipartFile file) {
        if (file.isEmpty()) {
            throw new BizException("文件不能为空");
        }
        int existing = bookMapper.countByUserId(userId);
        if (existing >= MAX_BOOKS_PER_USER) {
            throw new BizException("书架最多 " + MAX_BOOKS_PER_USER + " 本书，请先删除后再导入");
        }

        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf(".")).toLowerCase();
        }
        if (!ext.matches("\\.(epub|pdf)$")) {
            throw new BizException("目前仅支持 EPUB、PDF 格式电子书");
        }

        // 生成 COS 存储 key
        String fileName = UUID.randomUUID().toString() + ext;
        String cosKey = userId + "/" + fileName;

        // 确定 MIME 类型
        String contentType;
        switch (ext) {
            case ".epub":
                contentType = "application/epub+zip";
                break;
            case ".pdf":
                contentType = "application/pdf";
                break;
            case ".txt":
                contentType = "text/plain";
                break;
            default:
                contentType = "application/octet-stream";
        }

        long fileSize = file.getSize();
        long originalSize = fileSize;
        log.info("开始上传到 COS: key={}, size={} bytes ({} MB), type={}", cosKey, fileSize, String.format("%.2f", fileSize / 1024.0 / 1024.0), contentType);

        // 过大 PDF 现场压缩极慢（雅思真题等扫描件可达数分钟），直接传原文件加快接口返回
        final long skipCompressAboveBytes = 40L * 1024 * 1024;

        String storedKey;
        try {
            byte[] fileData;
            // PDF 文件尝试压缩（小文件跳过；超大文件跳过以免拖死上传）
            if (".pdf".equals(ext) && originalSize < skipCompressAboveBytes && pdfCompressor.isAvailable()) {
                try {
                    fileData = pdfCompressor.compress(file.getInputStream());
                    long compressedSize = fileData.length;
                    log.info("PDF 压缩完成: 原始 {} bytes ({} MB) -> 压缩后 {} bytes ({} MB), 压缩率 {}%",
                            originalSize, String.format("%.2f", originalSize / 1024.0 / 1024.0),
                            compressedSize, String.format("%.2f", compressedSize / 1024.0 / 1024.0),
                            String.format("%.1f", (1 - (double) compressedSize / originalSize) * 100));
                    fileSize = compressedSize;
                } catch (Exception e) {
                    log.warn("PDF 压缩失败，使用原始文件上传: {}", e.getMessage());
                    fileData = file.getBytes();
                }
            } else {
                if (".pdf".equals(ext) && originalSize >= skipCompressAboveBytes) {
                    log.info("PDF 超过 {}MB，跳过压缩直接上传 COS", skipCompressAboveBytes / 1024 / 1024);
                }
                fileData = file.getBytes();
            }

            try (java.io.InputStream in = new ByteArrayInputStream(fileData)) {
                storedKey = cosService.upload(cosKey, in, fileSize, contentType);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("COS 上传失败", e);
            throw new BizException("文件上传失败: " + e.getMessage());
        }

        log.info("COS 上传完成: storedKey={}, size={} bytes ({} MB)", storedKey, fileSize, String.format("%.2f", fileSize / 1024.0 / 1024.0));

        // 解析书名（去掉扩展名）
        String title = originalName != null && originalName.contains(".")
                ? originalName.substring(0, originalName.lastIndexOf("."))
                : originalName;

        WdBook book = WdBook.builder()
                .userId(userId)
                .title(title != null ? title : "未命名")
                .author("")
                .coverUrl("")
                .filePath(storedKey)
                .fileType(ext.replace(".", ""))
                .fileSize(fileSize)
                .totalChapters(0)
                .currentChapter(0)
                .currentCfi("")
                .progressPercent(BigDecimal.ZERO)
                .lastReadAt(LocalDateTime.now())
                .createBy(userId)
                .createTime(LocalDateTime.now())
                .updateBy(userId)
                .updateTime(LocalDateTime.now())
                .isDeleted(0)
                .build();

        bookMapper.insert(book);
        return book;
    }

    @Override
    public List<WdBook> listBooks(Long userId) {
        return bookMapper.selectByUserId(userId);
    }

    @Override
    public WdBook getBook(Long bookId) {
        WdBook book = bookMapper.selectById(bookId);
        if (book == null) {
            throw new BizException("书籍不存在");
        }
        return book;
    }

    @Override
    @Transactional
    public void deleteBook(Long bookId, Long userId) {
        WdBook book = bookMapper.selectById(bookId);
        if (book == null || !book.getUserId().equals(userId)) {
            throw new BizException("无权操作");
        }
        // 删除 COS 中的文件（异步删除，失败不影响数据库操作）
        try {
            cosService.delete(book.getFilePath());
        } catch (Exception e) {
            log.error("COS 删除文件失败，key={}", book.getFilePath(), e);
        }
        bookMapper.deleteById(bookId, userId);
    }

    @Override
    public void updateProgress(Long bookId, Integer chapterIndex, String cfi, BigDecimal percent) {
        bookMapper.updateProgress(bookId, chapterIndex, cfi, percent, LocalDateTime.now());
    }
}
