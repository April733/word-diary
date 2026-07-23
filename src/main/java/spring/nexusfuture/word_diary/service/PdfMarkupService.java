package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.entity.WdPdfMarkup;

import java.util.List;

/** PDF 阅读标注（高亮、批注等）CRUD。 */
public interface PdfMarkupService {

    /**
     * 新增 PDF 页面标注。
     *
     * @param userId 当前用户 ID
     * @param bookId 书籍 ID
     * @param markup 标注内容（页码、坐标、颜色等）
     * @return 持久化后的标注实体
     */
    WdPdfMarkup add(Long userId, Long bookId, WdPdfMarkup markup);

    /**
     * 更新 PDF 标注（校验归属）。
     *
     * @param userId 当前用户 ID
     * @param bookId 书籍 ID
     * @param id 标注 ID
     * @param markup 更新后的标注内容
     * @return 更新后的标注实体
     */
    WdPdfMarkup update(Long userId, Long bookId, Long id, WdPdfMarkup markup);

    /**
     * 删除 PDF 标注（校验归属）。
     *
     * @param userId 当前用户 ID
     * @param bookId 书籍 ID
     * @param id 标注 ID
     */
    void delete(Long userId, Long bookId, Long id);

    /**
     * 列出某本书的全部 PDF 标注。
     *
     * @param userId 当前用户 ID
     * @param bookId 书籍 ID
     * @return 标注列表
     */
    List<WdPdfMarkup> listByBook(Long userId, Long bookId);

    /**
     * 列出某本书指定页码的 PDF 标注。
     *
     * @param userId 当前用户 ID
     * @param bookId 书籍 ID
     * @param pageNum 页码（从 1 起）
     * @return 该页标注列表
     */
    List<WdPdfMarkup> listByPage(Long userId, Long bookId, Integer pageNum);
}
