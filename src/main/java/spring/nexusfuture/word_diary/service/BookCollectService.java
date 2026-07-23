package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.dto.request.BookCollectReq;
import spring.nexusfuture.word_diary.dto.response.BookCollectResultVO;

/** 阅读器划词收藏：写入指定单词本。 */
public interface BookCollectService {

    /**
     * 将阅读中选中的词收藏到单词本（可 AI 补全释义）。
     *
     * @param userId 当前用户 ID
     * @param bookId 来源书籍 ID
     * @param req 选词、上下文、目标单词本等
     * @return 收藏结果（新增/跳过/失败词数等）
     */
    BookCollectResultVO collect(Long userId, Long bookId, BookCollectReq req);
}
