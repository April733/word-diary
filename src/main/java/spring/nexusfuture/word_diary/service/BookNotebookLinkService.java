package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.dto.BookNotebookLinkVO;

import java.util.List;

/** 本书单词本列表（归属由 wd_word_book.book_id 在创建时决定，无事后关联）。 */
public interface BookNotebookLinkService {
    List<BookNotebookLinkVO> listLinks(Long userId, Long bookId);
}
