package spring.nexusfuture.word_diary.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import spring.nexusfuture.word_diary.common.BizException;
import spring.nexusfuture.word_diary.common.WordStudyStatus;
import spring.nexusfuture.word_diary.dto.request.BookCollectReq;
import spring.nexusfuture.word_diary.dto.response.BookCollectResultVO;
import spring.nexusfuture.word_diary.dto.response.CollectEntryDTO;
import spring.nexusfuture.word_diary.entity.*;
import spring.nexusfuture.word_diary.mapper.*;
import spring.nexusfuture.word_diary.service.BookCollectService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class BookCollectServiceImpl implements BookCollectService {

    /** 书籍收录例句统一带此前缀，展示端无需再判断来源 */
    public static final String ORIGINAL_PREFIX = "【原文】";

    private final WdBookMapper bookMapper;
    private final WdWordBookMapper notebookMapper;
    private final WdWordMapper wordMapper;

    public BookCollectServiceImpl(WdBookMapper bookMapper, WdWordBookMapper notebookMapper,
                                  WdWordMapper wordMapper) {
        this.bookMapper = bookMapper;
        this.notebookMapper = notebookMapper;
        this.wordMapper = wordMapper;
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
        Set<String> seenInBatch = new HashSet<>();
        String fullSnippet = req.getSourceSnippet();

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
                boolean doOverwrite = Boolean.TRUE.equals(item.getOverwrite())
                        || Boolean.TRUE.equals(req.getOverwriteExisting());
                if (!doOverwrite) {
                    skipped++;
                    messages.add(proto + "：已存在，已跳过");
                    continue;
                }
                applyOverwrite(existing, item, bookId, fullSnippet, req.getSourceLocation());
                wordMapper.update(existing);
                updated++;
                messages.add(proto + "：已覆盖");
                continue;
            }

            // 每个词条只存「包含该词」的那一句原文，避免多选粘连整段
            String sentence = pickSentenceForLemma(fullSnippet, proto);
            String example = toOriginalExample(sentence);

            WdWord word = WdWord.builder()
                    .notebookId(req.getNotebookId())
                    .userId(userId)
                    .wordProto(proto)
                    .partSpeechMean(item.getPartSpeechMean().trim())
                    .usPhonetic(emptyToNull(item.getUsPhonetic()))
                    .ukPhonetic(emptyToNull(item.getUkPhonetic()))
                    .sentenceExample(example)
                    .rootAffix(emptyToNull(item.getRootAffix()))
                    .entryType(normalizeEntryType(item.getEntryType()))
                    .sourceBookId(bookId)
                    .sourceSnippet(emptyToNull(sentence))
                    .sourceLocation(emptyToNull(req.getSourceLocation()))
                    .isPublic(0)
                    .status(WordStudyStatus.UN_STUDY.getCode())
                    .statusData(java.time.LocalDateTime.now())
                    .build();
            wordMapper.insert(word);
            added++;
        }

        return BookCollectResultVO.builder()
                .added(added)
                .skipped(skipped)
                .updated(updated)
                .messages(messages)
                .build();
    }

    /** 用新收录内容覆盖已有词条的释义、音标、词根、例句等 */
    static void applyOverwrite(WdWord existing, CollectEntryDTO item, Long bookId,
                               String fullSnippet, String sourceLocation) {
        String sentence = pickSentenceForLemma(fullSnippet, existing.getWordProto());
        String example = toOriginalExample(sentence);
        existing.setPartSpeechMean(item.getPartSpeechMean().trim());
        existing.setUsPhonetic(emptyToNull(item.getUsPhonetic()));
        existing.setUkPhonetic(emptyToNull(item.getUkPhonetic()));
        existing.setRootAffix(emptyToNull(item.getRootAffix()));
        existing.setEntryType(normalizeEntryType(item.getEntryType()));
        existing.setSentenceExample(example);
        existing.setSourceSnippet(emptyToNull(sentence));
        if (bookId != null) {
            existing.setSourceBookId(bookId);
        }
        if (hasText(sourceLocation)) {
            existing.setSourceLocation(sourceLocation.trim());
        }
    }

    /** 原文 →「【原文】…」；无原文则 null */
    static String toOriginalExample(String snippet) {
        if (!hasText(snippet)) return null;
        String t = snippet.trim();
        if (t.startsWith(ORIGINAL_PREFIX)) return t;
        return ORIGINAL_PREFIX + t;
    }

    static String stripOriginalPrefix(String text) {
        if (!hasText(text)) return "";
        String t = text.trim();
        if (t.startsWith(ORIGINAL_PREFIX)) {
            return t.substring(ORIGINAL_PREFIX.length()).trim();
        }
        return t;
    }

    /**
     * 从选中原文中取出包含该词条的那一句；找不到则回退整段。
     */
    static String pickSentenceForLemma(String snippet, String wordProto) {
        String full = repairSentenceBoundaries(stripOriginalPrefix(snippet));
        if (!hasText(full)) return null;
        if (!hasText(wordProto)) return full.trim();

        String lemma = wordProto.trim().toLowerCase(Locale.ROOT);
        String[] parts = full.split("(?<=[.!?…])\\s+");
        for (String part : parts) {
            if (!hasText(part)) continue;
            if (sentenceContainsLemma(part, lemma)) {
                return part.trim();
            }
        }
        return full.trim();
    }

    /**
     * 修复 OCR/框选句界：e / ° / • 等条目符号被粘进正文。
     * 例：construction e. The → construction. The；tunnels ° on → tunnels. on
     */
    static String repairSentenceBoundaries(String raw) {
        if (!hasText(raw)) return "";
        String t = raw.trim().replaceAll("\\s+", " ");
        t = t.replaceAll("\\s+[eE]\\.\\s+(?=[A-Z“\"‘'])", ". ");
        t = t.replaceAll("\\s+[eE°•·◦▪]\\s+(?=[A-Za-z“\"‘'])", ". ");
        t = t.replaceAll("([a-zà-ÿ])[eE]\\.\\s+(?=[A-Z“\"‘'])", "$1. ");
        t = t.replaceAll(" {2,}", " ").replaceAll("\\.\\s*\\.", ".").trim();
        return t;
    }

    private static boolean sentenceContainsLemma(String sentence, String lemma) {
        String s = sentence.toLowerCase(Locale.ROOT);
        if (lemma.contains(" ") || lemma.contains("-")) {
            return s.contains(lemma);
        }
        Pattern re = Pattern.compile("(?:^|[^a-z0-9'])" + Pattern.quote(lemma) + "(?:[^a-z0-9']|$)",
                Pattern.CASE_INSENSITIVE);
        return re.matcher(sentence).find() || s.contains(lemma);
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
}
