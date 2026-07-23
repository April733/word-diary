package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.dto.request.WordBookAddReq;
import spring.nexusfuture.word_diary.dto.response.WordBookVO;
import java.util.List;

/** 单词本 CRUD 与学习计划开关。 */
public interface WordBookService {

    /**
     * 创建单词本。
     *
     * @param userId 当前用户 ID
     * @param req 名称、关联书籍等
     * @return 新建单词本 VO
     */
    WordBookVO create(Long userId, WordBookAddReq req);

    /**
     * 列出当前用户的全部单词本。
     *
     * @param userId 当前用户 ID
     * @return 单词本列表
     */
    List<WordBookVO> listByUser(Long userId);

    /**
     * 更新单词本信息（校验归属）。
     *
     * @param userId 当前用户 ID
     * @param bookId 单词本 ID
     * @param req 待更新字段
     */
    void update(Long userId, Long bookId, WordBookAddReq req);

    /**
     * 开启或关闭单词本参与每日学习计划。
     *
     * @param userId 当前用户 ID
     * @param bookId 单词本 ID
     * @param enabled 是否参与计划
     */
    void setStudyEnabled(Long userId, Long bookId, boolean enabled);

    /**
     * 删除单词本及其下全部单词（校验归属）。
     *
     * @param userId 当前用户 ID
     * @param bookId 单词本 ID
     */
    void delete(Long userId, Long bookId);

    /**
     * 按 ID 取单词本名称；不存在返回空串。
     *
     * @param notebookId 单词本 ID
     * @return 单词本名称，不存在时为 ""
     */
    String getBookName(Long notebookId);
}
