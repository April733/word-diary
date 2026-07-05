package spring.nexusfuture.word_diary.service.impl;

import org.springframework.stereotype.Service;
import spring.nexusfuture.word_diary.common.BizException;
import spring.nexusfuture.word_diary.dto.WordBookAddReq;
import spring.nexusfuture.word_diary.dto.WordBookVO;
import spring.nexusfuture.word_diary.entity.WdWordBook;
import spring.nexusfuture.word_diary.mapper.WdWordBookMapper;
import spring.nexusfuture.word_diary.mapper.WdWordMapper;
import spring.nexusfuture.word_diary.service.WordBookService;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WordBookServiceImpl implements WordBookService {

    private final WdWordBookMapper bookMapper;
    private final WdWordMapper wordMapper;

    public WordBookServiceImpl(WdWordBookMapper bookMapper, WdWordMapper wordMapper) {
        this.bookMapper = bookMapper;
        this.wordMapper = wordMapper;
    }

    @Override
    public WordBookVO create(Long userId, WordBookAddReq req) {
        WdWordBook book = WdWordBook.builder()
                .userId(userId)
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
        if (req.getSortNum() != null) book.setSortNum(req.getSortNum());
        bookMapper.update(book);
    }

    @Override
    public void delete(Long userId, Long bookId) {
        WdWordBook book = bookMapper.selectById(bookId);
        if (book == null || !book.getUserId().equals(userId)) {
            throw new BizException("单词本不存在");
        }
        bookMapper.deleteById(bookId);
    }

    private WordBookVO toVO(WdWordBook book) {
        long count = wordMapper.selectByNotebookId(book.getId()).size();
        return WordBookVO.builder()
                .id(book.getId())
                .bookName(book.getBookName())
                .bookDesc(book.getBookDesc())
                .sortNum(book.getSortNum())
                .status(book.getStatus())
                .wordCount(count)
                .build();
    }
}
