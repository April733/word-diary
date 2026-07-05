package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.dto.WordAddReq;
import spring.nexusfuture.word_diary.dto.WordVO;
import java.util.List;

public interface WordService {
    void addWord(Long userId, Long notebookId, WordAddReq req);
    List<WordVO> listByNotebook(Long userId, Long notebookId);
    void updateWord(Long userId, Long wordId, WordAddReq req);
    void delete(Long userId, Long wordId);
}
