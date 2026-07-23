package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.dto.request.StudyBrowseAnswerReq;
import spring.nexusfuture.word_diary.dto.response.StudyBrowsePageVO;

/** 单词本浏览学习：分页刷词与即时反馈（不走每日计划）。 */
public interface StudyBrowseService {

    /**
     * 分页列出单词本中的词，供浏览模式学习。
     *
     * @param userId 当前用户 ID
     * @param notebookId 单词本 ID
     * @param page 页码（从 1 起）
     * @param pageSize 每页条数
     * @return 分页词表及统计
     */
    StudyBrowsePageVO listWords(Long userId, Long notebookId, int page, int pageSize);

    /**
     * 提交浏览模式作答，更新词的 SRS 状态。
     *
     * @param userId 当前用户 ID
     * @param req 作答请求（wordId、feedback 等）
     */
    void submitAnswer(Long userId, StudyBrowseAnswerReq req);
}
