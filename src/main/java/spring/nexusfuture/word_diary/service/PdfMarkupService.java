package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.entity.WdPdfMarkup;

import java.util.List;

public interface PdfMarkupService {
    WdPdfMarkup add(Long userId, Long bookId, WdPdfMarkup markup);

    WdPdfMarkup update(Long userId, Long bookId, Long id, WdPdfMarkup markup);

    void delete(Long userId, Long bookId, Long id);

    List<WdPdfMarkup> listByBook(Long userId, Long bookId);

    List<WdPdfMarkup> listByPage(Long userId, Long bookId, Integer pageNum);
}
