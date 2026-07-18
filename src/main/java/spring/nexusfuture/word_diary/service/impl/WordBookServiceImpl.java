package spring.nexusfuture.word_diary.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import spring.nexusfuture.word_diary.common.BizException;
import spring.nexusfuture.word_diary.dto.WordBookAddReq;
import spring.nexusfuture.word_diary.dto.WordBookVO;
import spring.nexusfuture.word_diary.entity.WdBook;
import spring.nexusfuture.word_diary.entity.WdWordBook;
import spring.nexusfuture.word_diary.mapper.WdBookMapper;
import spring.nexusfuture.word_diary.mapper.WdLearnPlanMapper;
import spring.nexusfuture.word_diary.mapper.WdStudyQueueMapper;
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
    private final WdLearnPlanMapper planMapper;
    private final WdStudyQueueMapper queueMapper;

    public WordBookServiceImpl(WdWordBookMapper bookMapper, WdWordMapper wordMapper,
                               WdBookMapper readingBookMapper, WdLearnPlanMapper planMapper,
                               WdStudyQueueMapper queueMapper) {
        this.bookMapper = bookMapper;
        this.wordMapper = wordMapper;
        this.readingBookMapper = readingBookMapper;
        this.planMapper = planMapper;
        this.queueMapper = queueMapper;
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
        if (!enabled) {
            // 移出今日队列（含已完成遍次），并清掉未学新词排期，避免占每日名额 / 被补第二遍加回
            queueMapper.deleteAllByNotebookId(bookId);
            planMapper.clearNewScheduleByNotebookId(bookId);
        } else {
            // 重新开启：清掉旧排期，下次打开每日学习时按上限重新分配今日名额
            planMapper.clearNewScheduleByNotebookId(bookId);
        }
    }

    @Override
    @Transactional
    public void delete(Long userId, Long bookId) {
        WdWordBook book = bookMapper.selectById(bookId);
        if (book == null || !book.getUserId().equals(userId)) {
            throw new BizException("单词本不存在");
        }
        // 先清今日队列，再软删计划与单词，避免幽灵新词
        queueMapper.deleteAllByNotebookId(bookId);
        planMapper.softDeleteByNotebookId(bookId);
        wordMapper.softDeleteByNotebookId(bookId);
        bookMapper.deleteById(bookId);
    }

    private WordBookVO toVO(WdWordBook book) {
        long count = wordMapper.selectByNotebookId(book.getId()).size();
        return WordBookVO.builder()
                .id(book.getId())
                .bookId(book.getBookId())
                .bookName(book.getBookName())
                .bookDesc(book.getBookDesc())
                .sortNum(book.getSortNum())
                .status(book.getStatus())
                .wordCount(count)
                .build();
    }
}
