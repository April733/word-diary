package spring.nexusfuture.word_diary.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import spring.nexusfuture.word_diary.common.BizException;
import spring.nexusfuture.word_diary.common.LearnStatus;
import spring.nexusfuture.word_diary.dto.BookCollectReq;
import spring.nexusfuture.word_diary.dto.BookCollectResultVO;
import spring.nexusfuture.word_diary.dto.CollectEntryDTO;
import spring.nexusfuture.word_diary.entity.*;
import spring.nexusfuture.word_diary.mapper.*;
import spring.nexusfuture.word_diary.service.BookCollectService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class BookCollectServiceImpl implements BookCollectService {

    private static final int MAX_EXAMPLE_LEN = 2000;
    private static final String EXAMPLE_SEP = "\n---\n";

    private final WdBookMapper bookMapper;
    private final WdWordBookMapper notebookMapper;
    private final WdWordMapper wordMapper;
    private final WdLearnPlanMapper planMapper;

    public BookCollectServiceImpl(WdBookMapper bookMapper, WdWordBookMapper notebookMapper,
                                  WdWordMapper wordMapper, WdLearnPlanMapper planMapper) {
        this.bookMapper = bookMapper;
        this.notebookMapper = notebookMapper;
        this.wordMapper = wordMapper;
        this.planMapper = planMapper;
    }

    @Override
    @Transactional
    public BookCollectResultVO collect(Long userId, Long bookId, BookCollectReq req) {
        WdBook book = bookMapper.selectById(bookId);
        if (book == null || !book.getUserId().equals(userId)) {
            throw new BizException("书籍不存在");
        }
        if (req == null || req.getNotebookId() == null || req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException("请选择单词本和要收录的词条");
        }
        WdWordBook notebook = notebookMapper.selectById(req.getNotebookId());
        if (notebook == null || !notebook.getUserId().equals(userId)) {
            throw new BizException("单词本不存在");
        }
        if (notebook.getBookId() == null || !notebook.getBookId().equals(bookId)) {
            throw new BizException("请先为本书创建或加入单词本");
        }

        int added = 0, skipped = 0, updated = 0;
        List<String> messages = new ArrayList<>();
        // 同一请求内按原型去重（忽略大小写）；单词本内已有词不重复插入
        Set<String> seenInBatch = new HashSet<>();

        for (CollectEntryDTO item : req.getItems()) {
            if (item.getWordProto() == null || item.getWordProto().isBlank()) {
                skipped++;
                continue;
            }
            if (item.getPartSpeechMean() == null || item.getPartSpeechMean().isBlank()) {
                throw new BizException("词条「" + item.getWordProto() + "」缺少释义");
            }
            String proto = item.getWordProto().trim();
            String dedupeKey = proto.toLowerCase(Locale.ROOT);
            if (!seenInBatch.add(dedupeKey)) {
                skipped++;
                messages.add(proto + "：本次重复，已跳过");
                continue;
            }
            WdWord existing = wordMapper.selectByNotebookAndWordProto(req.getNotebookId(), proto);
            if (existing != null) {
                // 已存在：不新建重复行；仅在明确要求时追加例句
                if (Boolean.TRUE.equals(item.getAppendExample()) && hasText(item.getSentenceExample())) {
                    String merged = mergeExample(existing.getSentenceExample(), item.getSentenceExample());
                    if (!merged.equals(existing.getSentenceExample() == null ? "" : existing.getSentenceExample())) {
                        existing.setSentenceExample(merged);
                        if (hasText(req.getSourceSnippet())) {
                            existing.setSourceSnippet(req.getSourceSnippet());
                        }
                        wordMapper.update(existing);
                        updated++;
                        messages.add(proto + "：已存在，已追加例句");
                    } else {
                        skipped++;
                        messages.add(proto + "：已存在，已跳过");
                    }
                } else {
                    skipped++;
                    messages.add(proto + "：已存在，已跳过");
                }
                continue;
            }

            WdWord word = WdWord.builder()
                    .notebookId(req.getNotebookId())
                    .userId(userId)
                    .wordProto(proto)
                    .partSpeechMean(item.getPartSpeechMean().trim())
                    .usPhonetic(emptyToNull(item.getUsPhonetic()))
                    .ukPhonetic(emptyToNull(item.getUkPhonetic()))
                    .sentenceExample(emptyToNull(item.getSentenceExample()))
                    .rootAffix(emptyToNull(item.getRootAffix()))
                    .entryType(normalizeEntryType(item.getEntryType()))
                    .sourceBookId(bookId)
                    .sourceSnippet(req.getSourceSnippet())
                    .sourceLocation(req.getSourceLocation())
                    .isPublic(0)
                    .build();
            wordMapper.insert(word);

            WdLearnPlan plan = WdLearnPlan.builder()
                    .userId(userId)
                    .notebookId(req.getNotebookId())
                    .wordId(word.getId())
                    .learnStatus(LearnStatus.NOT_LEARNED)
                    .currentLevel(0)
                    .reviewTimes(0)
                    .build();
            planMapper.insert(plan);
            added++;
        }

        return BookCollectResultVO.builder()
                .added(added)
                .skipped(skipped)
                .updated(updated)
                .messages(messages)
                .build();
    }

    private static String normalizeEntryType(String type) {
        if (type != null && "phrase".equalsIgnoreCase(type.trim())) {
            return "phrase";
        }
        return "word";
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String emptyToNull(String s) {
        return hasText(s) ? s.trim() : null;
    }

    private static String mergeExample(String existing, String incoming) {
        if (!hasText(existing)) return incoming.trim();
        if (!hasText(incoming)) return existing;
        if (existing.contains(incoming.trim())) return existing;
        String merged = existing + EXAMPLE_SEP + incoming.trim();
        return merged.length() > MAX_EXAMPLE_LEN ? merged.substring(0, MAX_EXAMPLE_LEN) : merged;
    }
}
