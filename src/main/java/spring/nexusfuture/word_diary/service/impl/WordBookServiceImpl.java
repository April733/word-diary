package spring.nexusfuture.word_diary.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import spring.nexusfuture.word_diary.common.BizException;
import spring.nexusfuture.word_diary.dto.request.WordBookAddReq;
import spring.nexusfuture.word_diary.dto.response.WordBookVO;
import spring.nexusfuture.word_diary.entity.WdBook;
import spring.nexusfuture.word_diary.entity.WdWordBook;
import spring.nexusfuture.word_diary.mapper.WdBookMapper;
import spring.nexusfuture.word_diary.mapper.WdWordBookMapper;
import spring.nexusfuture.word_diary.mapper.WdWordMapper;
import spring.nexusfuture.word_diary.service.WordBookService;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WordBookServiceImpl implements WordBookService {

    private final WdWordBookMapper bookMapper;
    private final WdWordMapper wordMapper;
    private final WdBookMapper readingBookMapper;

    public WordBookServiceImpl(WdWordBookMapper bookMapper, WdWordMapper wordMapper,
                               WdBookMapper readingBookMapper) {
        this.bookMapper = bookMapper;
        this.wordMapper = wordMapper;
        this.readingBookMapper = readingBookMapper;
    }

    @Override
    public WordBookVO create(Long userId, WordBookAddReq req) {
        Long readingBookId = req.getBookId();
        if (readingBookId != null) {
            WdBook readingBook = readingBookMapper.selectById(readingBookId);
            if (readingBook == null || !readingBook.getUserId().equals(userId)) {
                throw new BizException("书籍不存在");
            }
        }
        WdWordBook book = WdWordBook.builder()
                .userId(userId)
                .bookId(readingBookId)
                .sourceType(req.getSourceType() != null ? req.getSourceType() : "阅读")
                .bookName(req.getBookName())
                .bookDesc(req.getBookDesc())
                .sortNum(req.getSortNum() != null ? req.getSortNum() : 0)
                .status(1)
                .build();
        bookMapper.insert(book);
        return toVO(book);
    }

    @Override
    public List<WordBookVO> listByUser(Long userId) {
        List<WdWordBook> books = bookMapper.selectByUserId(userId);
        return books.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public void update(Long userId, Long bookId, WordBookAddReq req) {
        WdWordBook book = bookMapper.selectById(bookId);
        if (book == null || !book.getUserId().equals(userId)) {
            throw new BizException("单词本不存在");
        }
        book.setBookName(req.getBookName());
        book.setBookDesc(req.getBookDesc());
        if (req.getSourceType() != null) book.setSourceType(req.getSourceType());
        if (req.getSortNum() != null) book.setSortNum(req.getSortNum());
        bookMapper.update(book);
    }

    @Override
    @Transactional
    public void setStudyEnabled(Long userId, Long bookId, boolean enabled) {
        WdWordBook book = bookMapper.selectById(bookId);
        if (book == null || !book.getUserId().equals(userId)) {
            throw new BizException("单词本不存在");
        }
        int status = enabled ? 1 : 0;
        if (book.getStatus() != null && book.getStatus() == status) {
            return;
        }
        bookMapper.updateStatus(bookId, status);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long bookId) {
        WdWordBook book = bookMapper.selectById(bookId);
        if (book == null || !book.getUserId().equals(userId)) {
            throw new BizException("单词本不存在");
        }
        wordMapper.softDeleteByNotebookId(bookId);
        bookMapper.deleteById(bookId);
    }

    private WordBookVO toVO(WdWordBook book) {
        long count = wordMapper.selectByNotebookId(book.getId()).size();
        return WordBookVO.builder()
                .id(book.getId())
                .bookId(book.getBookId())
                .sourceType(book.getSourceType())
                .bookName(book.getBookName())
                .bookDesc(book.getBookDesc())
                .sortNum(book.getSortNum())
                .status(book.getStatus())
                .wordCount(count)
                .build();
    }

    @Override
    public String getBookName(Long notebookId) {
        if (notebookId == null) {
            return "";
        }
        WdWordBook book = bookMapper.selectById(notebookId);
        return book != null && book.getBookName() != null ? book.getBookName() : "";
    }
}
