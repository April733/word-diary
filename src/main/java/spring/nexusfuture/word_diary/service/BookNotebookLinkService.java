package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.dto.response.BookNotebookLinkVO;

import java.util.List;

/** 本书单词本列表（归属由 wd_word_book.book_id 在创建时决定，无事后关联）。 */
public interface BookNotebookLinkService {

    /**
     * 列出与指定书籍关联的单词本（含词数等摘要）。
     *
     * @param userId 当前用户 ID
     * @param bookId 书籍 ID
     * @return 单词本关联列表
     */
    List<BookNotebookLinkVO> listLinks(Long userId, Long bookId);
}
