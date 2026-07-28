package spring.nexusfuture.word_diary.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import spring.nexusfuture.word_diary.common.BizException;
import spring.nexusfuture.word_diary.common.PdfCompressor;
import spring.nexusfuture.word_diary.dto.request.BookUploadCompleteReq;
import spring.nexusfuture.word_diary.dto.request.BookUploadInitReq;
import spring.nexusfuture.word_diary.dto.response.BookUploadInitVO;
import spring.nexusfuture.word_diary.entity.WdBook;
import spring.nexusfuture.word_diary.mapper.WdBookMapper;
import spring.nexusfuture.word_diary.service.BookService;
import spring.nexusfuture.word_diary.service.CosService;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookServiceImpl implements BookService {

    /** 每用户书架最多保留的书籍数（未删除） */
    public static final int MAX_BOOKS_PER_USER = 10;

    /** 超过此大小跳过 Ghostscript 压缩（压缩易拖死上传导致 504） */
    private static final long SKIP_COMPRESS_ABOVE_BYTES = 8L * 1024 * 1024;

    private static final long MAX_BOOK_BYTES = 100L * 1024 * 1024;

    private final WdBookMapper bookMapper;
    private final CosService cosService;
    private final PdfCompressor pdfCompressor;

    @Override
    @Transactional
    public WdBook uploadBook(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("文件不能为空");
        }
        ensureSlot(userId);

        String originalName = file.getOriginalFilename();
        String ext = extOf(originalName);
        assertSupportedExt(ext);
        if (file.getSize() > MAX_BOOK_BYTES) {
            throw new BizException("单文件不能超过 100MB");
        }

        String contentType = contentTypeOf(ext);
        String cosKey = userId + "/" + UUID.randomUUID() + ext;
        long fileSize = file.getSize();
        long originalSize = fileSize;
        log.info("开始上传到 COS(经后端): key={}, size={} bytes ({} MB), type={}",
                cosKey, fileSize, String.format(Locale.ROOT, "%.2f", fileSize / 1024.0 / 1024.0), contentType);

        String storedKey;
        try {
            boolean tryCompress = ".pdf".equals(ext)
                    && originalSize < SKIP_COMPRESS_ABOVE_BYTES
                    && pdfCompressor.isAvailable();
            if (tryCompress) {
                try {
                    byte[] fileData = pdfCompressor.compress(file.getInputStream());
                    long compressedSize = fileData.length;
                    log.info("PDF 压缩完成: {} -> {} bytes", originalSize, compressedSize);
                    fileSize = compressedSize;
                    try (InputStream in = new ByteArrayInputStream(fileData)) {
                        storedKey = cosService.upload(cosKey, in, fileSize, contentType);
                    }
                } catch (Exception e) {
                    log.warn("PDF 压缩失败，流式上传原文件: {}", e.getMessage());
                    try (InputStream in = file.getInputStream()) {
                        storedKey = cosService.upload(cosKey, in, originalSize, contentType);
                        fileSize = originalSize;
                    }
                }
            } else {
                if (".pdf".equals(ext) && originalSize >= SKIP_COMPRESS_ABOVE_BYTES) {
                    log.info("PDF 超过 {}MB，跳过压缩直传 COS", SKIP_COMPRESS_ABOVE_BYTES / 1024 / 1024);
                }
                // 流式上传，避免大文件整包进堆内存
                try (InputStream in = file.getInputStream()) {
                    storedKey = cosService.upload(cosKey, in, originalSize, contentType);
                }
                fileSize = originalSize;
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("COS 上传失败", e);
            throw new BizException("文件上传失败: " + e.getMessage());
        }

        log.info("COS 上传完成: storedKey={}, size={} bytes", storedKey, fileSize);
        return insertBook(userId, originalName, storedKey, ext, fileSize);
    }

    @Override
    public BookUploadInitVO initDirectUpload(Long userId, BookUploadInitReq req) {
        ensureSlot(userId);
        if (req == null || req.getFileName() == null || req.getFileName().isBlank()) {
            throw new BizException("请选择文件");
        }
        long size = req.getFileSize() != null ? req.getFileSize() : 0L;
        if (size <= 0) {
            throw new BizException("文件不能为空");
        }
        if (size > MAX_BOOK_BYTES) {
            throw new BizException("单文件不能超过 100MB");
        }
        String ext = extOf(req.getFileName());
        assertSupportedExt(ext);
        String contentType = contentTypeOf(ext);
        String cosKey = userId + "/" + UUID.randomUUID() + ext;
        String uploadUrl = cosService.generatePresignedPutUrl(cosKey, contentType);
        return BookUploadInitVO.builder()
                .uploadUrl(uploadUrl)
                .cosKey(cosKey)
                .contentType(contentType)
                .fileType(ext.replace(".", ""))
                .build();
    }

    @Override
    @Transactional
    public WdBook completeDirectUpload(Long userId, BookUploadCompleteReq req) {
        ensureSlot(userId);
        if (req == null || req.getCosKey() == null || req.getCosKey().isBlank()) {
            throw new BizException("缺少文件路径");
        }
        String cosKey = req.getCosKey().trim();
        // 仅允许本人目录，防止伪造他人 key
        String expectedPrefix = userId + "/";
        String bare = cosKey.startsWith("books/") ? cosKey.substring("books/".length()) : cosKey;
        if (!bare.startsWith(expectedPrefix)) {
            throw new BizException("无权确认该文件");
        }
        long reported = req.getFileSize() != null ? req.getFileSize() : 0L;
        long remoteSize = cosService.getFileSize(cosKey);
        if (remoteSize <= 0) {
            throw new BizException("未找到已上传文件，请重试");
        }
        if (reported > 0 && remoteSize < reported * 0.5) {
            throw new BizException("文件上传不完整，请重试");
        }
        String originalName = req.getOriginalName();
        String ext = extOf(originalName != null ? originalName : bare);
        assertSupportedExt(ext);
        return insertBook(userId, originalName, cosKey, ext, remoteSize > 0 ? remoteSize : reported);
    }

    private void ensureSlot(Long userId) {
        int existing = bookMapper.countByUserId(userId);
        if (existing >= MAX_BOOKS_PER_USER) {
            throw new BizException("书架最多 " + MAX_BOOKS_PER_USER + " 本书，请先删除后再导入");
        }
    }

    private static String extOf(String originalName) {
        if (originalName == null || !originalName.contains(".")) {
            return "";
        }
        return originalName.substring(originalName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
    }

    private static void assertSupportedExt(String ext) {
        if (!ext.matches("\\.(epub|pdf)$")) {
            throw new BizException("目前仅支持 EPUB、PDF 格式电子书");
        }
    }

    private static String contentTypeOf(String ext) {
        return switch (ext) {
            case ".epub" -> "application/epub+zip";
            case ".pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }

    private WdBook insertBook(Long userId, String originalName, String storedKey, String ext, long fileSize) {
        // CosService.upload 返回 fullKey（含 books/）；直传 complete 可能传相对 key
        String filePath = storedKey;
        if (!filePath.startsWith("books/") && !filePath.contains("://")) {
            // 与 CosServiceImpl.upload 返回值对齐：统一存带 prefix 的 key
            // 但 download/delete/buildKey 对无 prefix 也会补上，所以相对 key 也可。
            // 为与历史数据一致，若 upload 返回 fullKey，直传也尽量存 fullKey。
            filePath = "books/" + storedKey.replaceFirst("^books/", "");
        }

        String title = originalName != null && originalName.contains(".")
                ? originalName.substring(0, originalName.lastIndexOf('.'))
                : originalName;

        WdBook book = WdBook.builder()
                .userId(userId)
                .title(title != null && !title.isBlank() ? title : "未命名")
                .author("")
                .coverUrl("")
                .filePath(filePath)
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
        try {
            cosService.delete(book.getFilePath());
        } catch (Exception e) {
            log.error("COS 删除文件失败，key={}", book.getFilePath(), e);
        }
        bookMapper.deleteById(bookId, userId);
    }

    @Override
    public void updateProgress(Long bookId, Long userId, Integer chapterIndex, String cfi, BigDecimal percent) {
        WdBook book = bookMapper.selectById(bookId);
        if (book == null || !book.getUserId().equals(userId)) {
            throw new BizException("无权操作");
        }
        bookMapper.updateProgress(bookId, chapterIndex, cfi, percent, LocalDateTime.now());
    }
}
