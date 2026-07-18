package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.dto.AiParseResult;
import spring.nexusfuture.word_diary.dto.WordAddReq;
import spring.nexusfuture.word_diary.dto.WordVO;
import java.util.List;

public interface WordService {
    void addWord(Long userId, Long notebookId, WordAddReq req);
    void batchAddWords(Long userId, Long notebookId, List<AiParseResult> words);
    List<WordVO> listByNotebook(Long userId, Long notebookId);
    List<WordVO> listTrash(Long userId, Long notebookId);
    void updateWord(Long userId, Long wordId, WordAddReq req);
    /** 移入垃圾桶，保留 7 天 */
    void delete(Long userId, Long wordId);
    void restore(Long userId, Long notebookId, Long wordId);
    /** 清理超过 7 天的垃圾桶词条，返回删除条数 */
    int purgeExpiredTrash();
    /** 仅补全空缺字段，不覆盖已有内容 */
    int fillGaps(Long userId, Long notebookId, List<Long> wordIds);
}
