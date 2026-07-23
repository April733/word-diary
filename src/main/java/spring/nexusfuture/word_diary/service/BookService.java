package spring.nexusfuture.word_diary.service;

import org.springframework.web.multipart.MultipartFile;
import spring.nexusfuture.word_diary.entity.WdBook;

import java.util.List;

/** 用户书架：上传、列表、进度与删除。 */
public interface BookService {

    /**
     * 上传并导入书籍文件，解析后入库。
     *
     * @param userId 当前用户 ID
     * @param file 书籍文件（EPUB/PDF 等）
     * @return 导入后的书籍实体
     */
    WdBook uploadBook(Long userId, MultipartFile file);

    /**
     * 获取当前用户书架列表。
     *
     * @param userId 当前用户 ID
     * @return 书籍列表
     */
    List<WdBook> listBooks(Long userId);

    /**
     * 获取书籍详情。
     *
     * @param bookId 书籍 ID
     * @return 书籍实体；不存在时抛业务异常
     */
    WdBook getBook(Long bookId);

    /**
     * 删除书籍（校验归属）。
     *
     * @param bookId 书籍 ID
     * @param userId 当前用户 ID
     */
    void deleteBook(Long bookId, Long userId);

    /**
     * 更新阅读进度（校验书籍归属）。
     *
     * @param bookId 书籍 ID
     * @param userId 当前用户 ID
     * @param chapterIndex 当前章节索引
     * @param cfi EPUB 定位符，可为 null
     * @param percent 阅读进度百分比
     */
    void updateProgress(Long bookId, Long userId, Integer chapterIndex, String cfi, java.math.BigDecimal percent);
}
