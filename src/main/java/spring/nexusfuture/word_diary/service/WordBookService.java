package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.dto.WordBookAddReq;
import spring.nexusfuture.word_diary.dto.WordBookVO;
import java.util.List;

public interface WordBookService {
    WordBookVO create(Long userId, WordBookAddReq req);
    List<WordBookVO> listByUser(Long userId);
    void update(Long userId, Long bookId, WordBookAddReq req);
    void delete(Long userId, Long bookId);
}
