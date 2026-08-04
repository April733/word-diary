package spring.nexusfuture.word_diary.service;

import org.springframework.web.multipart.MultipartFile;
import spring.nexusfuture.word_diary.dto.request.BookUploadCompleteReq;
import spring.nexusfuture.word_diary.dto.request.BookUploadInitReq;
import spring.nexusfuture.word_diary.dto.response.BookShareCodeVO;
import spring.nexusfuture.word_diary.dto.response.BookUploadInitVO;
import spring.nexusfuture.word_diary.entity.WdBook;

import java.util.List;

/** 用户书架：上传、列表、进度与删除。 */
public interface BookService {

    /** 经后端中转上传（小文件 / COS CORS 未放行时兜底） */
    WdBook uploadBook(Long userId, MultipartFile file);

    /** 申请 COS 直传凭证（浏览器 PUT） */
    BookUploadInitVO initDirectUpload(Long userId, BookUploadInitReq req);

    /** COS 直传完成后落库 */
    WdBook completeDirectUpload(Long userId, BookUploadCompleteReq req);

    /** 生成书籍分享码 */
    BookShareCodeVO createShareCode(Long userId, Long bookId);

    /** 通过分享码导入共享书籍 */
    WdBook importSharedBook(Long userId, String shareCode);

    List<WdBook> listBooks(Long userId);

    WdBook getBook(Long bookId);

    void deleteBook(Long bookId, Long userId);

    void updateProgress(Long bookId, Long userId, Integer chapterIndex, String cfi, java.math.BigDecimal percent);
}
