package spring.nexusfuture.word_diary.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import spring.nexusfuture.word_diary.common.BizException;
import spring.nexusfuture.word_diary.entity.WdBook;
import spring.nexusfuture.word_diary.entity.WdPdfMarkup;
import spring.nexusfuture.word_diary.mapper.WdBookMapper;
import spring.nexusfuture.word_diary.mapper.WdPdfMarkupMapper;
import spring.nexusfuture.word_diary.service.PdfMarkupService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PdfMarkupServiceImpl implements PdfMarkupService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            WdPdfMarkup.TYPE_PEN, WdPdfMarkup.TYPE_RECT, WdPdfMarkup.TYPE_TEXT);
    private static final String DEFAULT_COLOR = "#E53935";

    private final WdPdfMarkupMapper markupMapper;
    private final WdBookMapper bookMapper;

    @Override
    @Transactional
    public WdPdfMarkup add(Long userId, Long bookId, WdPdfMarkup markup) {
        assertOwnsPdf(userId, bookId);
        validate(markup);
        markup.setBookId(bookId);
        markup.setUserId(userId);
        markup.setCreateBy(userId);
        markup.setUpdateBy(userId);
        markup.setCreateTime(LocalDateTime.now());
        markup.setUpdateTime(LocalDateTime.now());
        markup.setIsDeleted(0);
        if (markup.getColor() == null || markup.getColor().isBlank()) {
            markup.setColor(DEFAULT_COLOR);
        }
        markupMapper.insert(markup);
        return markup;
    }

    @Override
    @Transactional
    public WdPdfMarkup update(Long userId, Long bookId, Long id, WdPdfMarkup markup) {
        assertOwnsPdf(userId, bookId);
        WdPdfMarkup existing = markupMapper.selectById(id);
        if (existing == null || !existing.getUserId().equals(userId) || !existing.getBookId().equals(bookId)) {
            throw new BizException("批注不存在");
        }
        existing.setGeometryJson(markup.getGeometryJson() != null ? markup.getGeometryJson() : existing.getGeometryJson());
        existing.setColor(markup.getColor() != null ? markup.getColor() : existing.getColor());
        existing.setNoteText(markup.getNoteText() != null ? markup.getNoteText() : existing.getNoteText());
        existing.setUpdateBy(userId);
        markupMapper.updateById(existing);
        return markupMapper.selectById(id);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long bookId, Long id) {
        assertOwnsPdf(userId, bookId);
        WdPdfMarkup existing = markupMapper.selectById(id);
        if (existing == null || !existing.getUserId().equals(userId) || !existing.getBookId().equals(bookId)) {
            throw new BizException("批注不存在");
        }
        markupMapper.softDelete(id, userId);
    }

    @Override
    public List<WdPdfMarkup> listByBook(Long userId, Long bookId) {
        assertOwnsPdf(userId, bookId);
        return markupMapper.selectByBook(bookId, userId);
    }

    @Override
    public List<WdPdfMarkup> listByPage(Long userId, Long bookId, Integer pageNum) {
        assertOwnsPdf(userId, bookId);
        return markupMapper.selectByPage(bookId, userId, pageNum);
    }

    private void assertOwnsPdf(Long userId, Long bookId) {
        WdBook book = bookMapper.selectById(bookId);
        if (book == null || !book.getUserId().equals(userId)) {
            throw new BizException("书籍不存在");
        }
        if (book.getFileType() == null || !"pdf".equalsIgnoreCase(book.getFileType())) {
            throw new BizException("仅支持 PDF 批注");
        }
    }

    private void validate(WdPdfMarkup markup) {
        if (markup.getPageNum() == null || markup.getPageNum() < 1) {
            throw new BizException("页码无效");
        }
        if (markup.getMarkupType() == null || !ALLOWED_TYPES.contains(markup.getMarkupType())) {
            throw new BizException("批注类型无效");
        }
        if (markup.getGeometryJson() == null || markup.getGeometryJson().isBlank()) {
            throw new BizException("缺少坐标数据");
        }
        if (WdPdfMarkup.TYPE_TEXT.equals(markup.getMarkupType())
                && (markup.getNoteText() == null || markup.getNoteText().isBlank())) {
            throw new BizException("请填写笔记内容");
        }
    }
}
