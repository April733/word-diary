package spring.nexusfuture.word_diary.service;

import org.springframework.web.multipart.MultipartFile;
import spring.nexusfuture.word_diary.entity.WdBook;

import java.util.List;

public interface BookService {

    /** 上传并导入书籍，返回书籍信息 */
    WdBook uploadBook(Long userId, MultipartFile file);

    /** 获取用户书架 */
    List<WdBook> listBooks(Long userId);

    /** 获取书籍详情 */
    WdBook getBook(Long bookId);

    /** 删除书籍 */
    void deleteBook(Long bookId, Long userId);

    /** 更新阅读进度 */
    void updateProgress(Long bookId, Integer chapterIndex, String cfi, java.math.BigDecimal percent);
}
