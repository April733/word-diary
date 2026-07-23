package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.dto.response.AiParseResult;
import spring.nexusfuture.word_diary.dto.request.WordAddReq;
import spring.nexusfuture.word_diary.dto.response.WordVO;
import java.util.List;

/** 单词 CRUD、批量导入与字段补全。 */
public interface WordService {

    /**
     * 向单词本添加单个单词。
     *
     * @param userId 当前用户 ID
     * @param notebookId 单词本 ID
     * @param req 单词内容与释义等
     */
    void addWord(Long userId, Long notebookId, WordAddReq req);

    /**
     * 批量添加 AI 解析后的单词到单词本。
     *
     * @param userId 当前用户 ID
     * @param notebookId 单词本 ID
     * @param words AI 解析结果列表
     */
    void batchAddWords(Long userId, Long notebookId, List<AiParseResult> words);

    /**
     * 列出单词本中的全部单词。
     *
     * @param userId 当前用户 ID
     * @param notebookId 单词本 ID
     * @return 单词 VO 列表
     */
    List<WordVO> listByNotebook(Long userId, Long notebookId);

    /**
     * 更新单词内容（校验归属）。
     *
     * @param userId 当前用户 ID
     * @param wordId 单词 ID
     * @param req 待更新字段
     */
    void updateWord(Long userId, Long wordId, WordAddReq req);

    /**
     * 删除单词（不可恢复，校验归属）。
     *
     * @param userId 当前用户 ID
     * @param wordId 单词 ID
     */
    void delete(Long userId, Long wordId);

    /**
     * 仅补全空缺字段，不覆盖已有内容。
     *
     * @param userId 当前用户 ID
     * @param notebookId 单词本 ID
     * @param wordIds 待补全的单词 ID 列表
     * @return 实际补全成功的词数
     */
    int fillGaps(Long userId, Long notebookId, List<Long> wordIds);
}
