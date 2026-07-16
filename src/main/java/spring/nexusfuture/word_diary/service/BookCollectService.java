package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.dto.BookCollectReq;
import spring.nexusfuture.word_diary.dto.BookCollectResultVO;

public interface BookCollectService {
    BookCollectResultVO collect(Long userId, Long bookId, BookCollectReq req);
}
