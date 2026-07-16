package spring.nexusfuture.word_diary.service.impl;

import org.springframework.stereotype.Service;
import spring.nexusfuture.word_diary.common.BizException;
import spring.nexusfuture.word_diary.dto.BookNotebookLinkVO;
import spring.nexusfuture.word_diary.entity.WdBook;
import spring.nexusfuture.word_diary.entity.WdWordBook;
import spring.nexusfuture.word_diary.mapper.WdBookMapper;
import spring.nexusfuture.word_diary.mapper.WdWordBookMapper;
import spring.nexusfuture.word_diary.service.BookNotebookLinkService;

import java.util.ArrayList;
import java.util.List;

@Service
public class BookNotebookLinkServiceImpl implements BookNotebookLinkService {

    private final WdBookMapper bookMapper;
    private final WdWordBookMapper notebookMapper;

    public BookNotebookLinkServiceImpl(WdBookMapper bookMapper, WdWordBookMapper notebookMapper) {
        this.bookMapper = bookMapper;
        this.notebookMapper = notebookMapper;
    }

    @Override
    public List<BookNotebookLinkVO> listLinks(Long userId, Long bookId) {
        WdBook book = bookMapper.selectById(bookId);
        if (book == null || !book.getUserId().equals(userId)) {
            throw new BizException("书籍不存在");
        }
        List<WdWordBook> notebooks = notebookMapper.selectByBookId(userId, bookId);
        List<BookNotebookLinkVO> result = new ArrayList<>();
        for (WdWordBook nb : notebooks) {
            result.add(BookNotebookLinkVO.builder()
                    .notebookId(nb.getId())
                    .notebookName(nb.getBookName())
                    .linkName(nb.getBookName())
                    .sortNum(nb.getSortNum())
                    .build());
        }
        return result;
    }
}
