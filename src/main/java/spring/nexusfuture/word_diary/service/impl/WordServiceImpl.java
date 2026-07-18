package spring.nexusfuture.word_diary.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import spring.nexusfuture.word_diary.common.BizException;
import spring.nexusfuture.word_diary.common.LearnStatus;
import spring.nexusfuture.word_diary.dto.AiParseResult;
import spring.nexusfuture.word_diary.dto.WordAddReq;
import spring.nexusfuture.word_diary.dto.WordVO;
import spring.nexusfuture.word_diary.entity.WdLearnPlan;
import spring.nexusfuture.word_diary.entity.WdWord;
import spring.nexusfuture.word_diary.entity.WdWordBook;
import spring.nexusfuture.word_diary.mapper.WdLearnPlanMapper;
import spring.nexusfuture.word_diary.mapper.WdStudyQueueMapper;
import spring.nexusfuture.word_diary.mapper.WdWordBookMapper;
import spring.nexusfuture.word_diary.mapper.WdWordMapper;
import spring.nexusfuture.word_diary.service.AiService;
import spring.nexusfuture.word_diary.service.WordService;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WordServiceImpl implements WordService {

    private static final int TRASH_RETAIN_DAYS = 7;

    private final WdWordMapper wordMapper;
    private final WdWordBookMapper bookMapper;
    private final WdLearnPlanMapper planMapper;
    private final WdStudyQueueMapper queueMapper;
    private final AiService aiService;

    public WordServiceImpl(WdWordMapper wordMapper, WdWordBookMapper bookMapper,
                           WdLearnPlanMapper planMapper, WdStudyQueueMapper queueMapper,
                           AiService aiService) {
        this.wordMapper = wordMapper;
        this.bookMapper = bookMapper;
        this.planMapper = planMapper;
        this.queueMapper = queueMapper;
        this.aiService = aiService;
    }

    @Override
    @Transactional
    public void addWord(Long userId, Long notebookId, WordAddReq req) {
        WdWordBook book = bookMapper.selectById(notebookId);
        if (book == null || !book.getUserId().equals(userId)) {
            throw new BizException("单词本不存在");
        }
        if (req.getWordProto() == null || req.getWordProto().isBlank()) {
            throw new BizException("单词不能为空");
        }
        String proto = req.getWordProto().trim();
        WdWord existing = wordMapper.selectByNotebookAndWordProto(notebookId, proto);
        if (existing != null) {
            throw new BizException("单词本中已存在「" + proto + "」");
        }

        WdWord word = WdWord.builder()
                .notebookId(notebookId)
                .userId(userId)
                .wordProto(proto)
                .usPhonetic(req.getUsPhonetic())
                .ukPhonetic(req.getUkPhonetic())
                .partSpeechMean(req.getPartSpeechMean())
                .sentenceExample(req.getSentenceExample())
                .rootAffix(req.getRootAffix())
                .entryType("word")
                .isPublic(0)
                .build();
        wordMapper.insert(word);

        WdLearnPlan plan = WdLearnPlan.builder()
                .userId(userId)
                .notebookId(notebookId)
                .wordId(word.getId())
                .learnStatus(LearnStatus.NOT_LEARNED)
                .currentLevel(0)
                .reviewTimes(0)
                .build();
        planMapper.insert(plan);
    }

    @Override
    public List<WordVO> listByNotebook(Long userId, Long notebookId) {
        WdWordBook book = bookMapper.selectById(notebookId);
        if (book == null || !book.getUserId().equals(userId)) {
            throw new BizException("单词本不存在");
        }
        List<WdWord> words = wordMapper.selectByNotebookId(notebookId);
        return words.stream().map(word -> toVo(userId, word, false)).collect(Collectors.toList());
    }

    @Override
    public List<WordVO> listTrash(Long userId, Long notebookId) {
        WdWordBook book = bookMapper.selectById(notebookId);
        if (book == null || !book.getUserId().equals(userId)) {
            throw new BizException("单词本不存在");
        }
        // 打开时顺带清理过期项（避免自调用导致事务失效）
        int purged = wordMapper.hardDeleteExpiredTrash();
        if (purged > 0) {
            planMapper.hardDeleteOrphanPlans();
        }
        return wordMapper.selectTrashByNotebookId(notebookId).stream()
                .map(word -> toVo(userId, word, true))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(Long userId, Long wordId) {
        WdWord word = wordMapper.selectById(wordId);
        if (word == null || !word.getUserId().equals(userId)) {
            throw new BizException("单词不存在");
        }
        wordMapper.deleteById(wordId);
        planMapper.deleteByWordId(wordId);
        queueMapper.deletePendingByWordId(wordId);
    }

    @Override
    @Transactional
    public void restore(Long userId, Long notebookId, Long wordId) {
        WdWordBook book = bookMapper.selectById(notebookId);
        if (book == null || !book.getUserId().equals(userId)) {
            throw new BizException("单词本不存在");
        }
        WdWord word = wordMapper.selectByIdAny(wordId);
        if (word == null || !word.getUserId().equals(userId) || !notebookId.equals(word.getNotebookId())) {
            throw new BizException("单词不存在");
        }
        if (word.getIsDeleted() == null || word.getIsDeleted() != 1) {
            throw new BizException("该词条不在垃圾桶中");
        }
        if (word.getDeletedAt() != null
                && word.getDeletedAt().isBefore(LocalDateTime.now().minusDays(TRASH_RETAIN_DAYS))) {
            throw new BizException("已超过保留期限，无法恢复");
        }
        WdWord conflict = wordMapper.selectByNotebookAndWordProto(notebookId, word.getWordProto());
        if (conflict != null) {
            throw new BizException("单词本中已存在「" + word.getWordProto() + "」，无法恢复");
        }
        wordMapper.restoreById(wordId);
        int restored = planMapper.restoreByWordId(wordId);
        if (restored == 0) {
            WdLearnPlan plan = WdLearnPlan.builder()
                    .userId(userId)
                    .notebookId(notebookId)
                    .wordId(wordId)
                    .learnStatus(LearnStatus.NOT_LEARNED)
                    .currentLevel(0)
                    .reviewTimes(0)
                    .build();
            planMapper.insert(plan);
        }
    }

    @Override
    @Transactional
    public int purgeExpiredTrash() {
        int deleted = wordMapper.hardDeleteExpiredTrash();
        if (deleted > 0) {
            planMapper.hardDeleteOrphanPlans();
        }
        return deleted;
    }

    @Override
    @Transactional
    public void batchAddWords(Long userId, Long notebookId, List<AiParseResult> words) {
        WdWordBook book = bookMapper.selectById(notebookId);
        if (book == null || !book.getUserId().equals(userId)) {
            throw new BizException("单词本不存在");
        }
        List<String> errors = new ArrayList<>();
        for (AiParseResult r : words) {
            try {
                if (r.getWordProto() == null || r.getWordProto().isBlank()) {
                    errors.add("(空): 单词不能为空");
                    continue;
                }
                String proto = r.getWordProto().trim();
                if (wordMapper.selectByNotebookAndWordProto(notebookId, proto) != null) {
                    continue;
                }
                WdWord word = WdWord.builder()
                        .notebookId(notebookId)
                        .userId(userId)
                        .wordProto(proto)
                        .usPhonetic(r.getUsPhonetic())
                        .ukPhonetic(r.getUkPhonetic())
                        .partSpeechMean(r.getPartSpeechMean())
                        .sentenceExample(r.getSentenceExample())
                        .rootAffix(r.getRootAffix())
                        .entryType("word")
                        .isPublic(0)
                        .build();
                wordMapper.insert(word);

                WdLearnPlan plan = WdLearnPlan.builder()
                        .userId(userId)
                        .notebookId(notebookId)
                        .wordId(word.getId())
                        .learnStatus(LearnStatus.NOT_LEARNED)
                        .currentLevel(0)
                        .reviewTimes(0)
                        .build();
                planMapper.insert(plan);
            } catch (Exception e) {
                errors.add(r.getWordProto() + ": " + e.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            throw new BizException("部分单词导入失败: " + String.join("; ", errors));
        }
    }

    @Override
    @Transactional
    public void updateWord(Long userId, Long wordId, WordAddReq req) {
        WdWord word = wordMapper.selectById(wordId);
        if (word == null || !word.getUserId().equals(userId)) {
            throw new BizException("单词不存在");
        }
        word.setWordProto(req.getWordProto());
        word.setUsPhonetic(req.getUsPhonetic());
        word.setUkPhonetic(req.getUkPhonetic());
        word.setPartSpeechMean(req.getPartSpeechMean());
        word.setSentenceExample(req.getSentenceExample());
        word.setRootAffix(req.getRootAffix());
        wordMapper.update(word);
    }

    @Override
    @Transactional
    public int fillGaps(Long userId, Long notebookId, List<Long> wordIds) {
        WdWordBook book = bookMapper.selectById(notebookId);
        if (book == null || !book.getUserId().equals(userId)) {
            throw new BizException("单词本不存在");
        }
        List<WdWord> targets = wordMapper.selectByNotebookId(notebookId).stream()
                .filter(w -> wordIds == null || wordIds.isEmpty() || wordIds.contains(w.getId()))
                .filter(this::hasGap)
                .collect(Collectors.toList());
        if (targets.isEmpty()) {
            return 0;
        }
        List<String> protos = targets.stream().map(WdWord::getWordProto).collect(Collectors.toList());
        List<AiParseResult> parsed = aiService.parseWords(userId, protos);
        int updated = 0;
        for (WdWord word : targets) {
            AiParseResult match = parsed.stream()
                    .filter(p -> p.getWordProto() != null
                            && p.getWordProto().equalsIgnoreCase(word.getWordProto()))
                    .findFirst()
                    .orElse(null);
            if (match == null) continue;
            boolean changed = mergeGapFields(word, match);
            if (changed) {
                wordMapper.update(word);
                updated++;
            }
        }
        return updated;
    }

    private WordVO toVo(Long userId, WdWord word, boolean trash) {
        WdLearnPlan plan = trash ? null : planMapper.selectByUserIdAndWordId(userId, word.getId());
        WordVO.WordVOBuilder b = WordVO.builder()
                .id(word.getId())
                .notebookId(word.getNotebookId())
                .wordProto(word.getWordProto())
                .usPhonetic(word.getUsPhonetic())
                .ukPhonetic(word.getUkPhonetic())
                .partSpeechMean(word.getPartSpeechMean())
                .sentenceExample(word.getSentenceExample())
                .rootAffix(word.getRootAffix())
                .entryType(word.getEntryType())
                .learnStatus(plan != null ? plan.getLearnStatus() : LearnStatus.NOT_LEARNED)
                .currentLevel(plan != null ? plan.getCurrentLevel() : 0);
        if (trash && word.getDeletedAt() != null) {
            long daysLeft = ChronoUnit.DAYS.between(LocalDateTime.now(), word.getDeletedAt().plusDays(TRASH_RETAIN_DAYS));
            b.deletedAt(word.getDeletedAt())
                    .retainDaysLeft((int) Math.max(0, daysLeft));
        }
        return b.build();
    }

    private boolean hasGap(WdWord w) {
        return isBlank(w.getUsPhonetic()) || isBlank(w.getUkPhonetic())
                || isBlank(w.getRootAffix()) || isBlank(w.getSentenceExample());
    }

    private boolean mergeGapFields(WdWord word, AiParseResult from) {
        boolean changed = false;
        if (isBlank(word.getUsPhonetic()) && !isBlank(from.getUsPhonetic())) {
            word.setUsPhonetic(from.getUsPhonetic());
            changed = true;
        }
        if (isBlank(word.getUkPhonetic()) && !isBlank(from.getUkPhonetic())) {
            word.setUkPhonetic(from.getUkPhonetic());
            changed = true;
        }
        if (isBlank(word.getRootAffix()) && !isBlank(from.getRootAffix())) {
            word.setRootAffix(from.getRootAffix());
            changed = true;
        }
        if (isBlank(word.getSentenceExample()) && !isBlank(from.getSentenceExample())) {
            word.setSentenceExample(from.getSentenceExample());
            changed = true;
        }
        if (isBlank(word.getPartSpeechMean()) && !isBlank(from.getPartSpeechMean())) {
            word.setPartSpeechMean(from.getPartSpeechMean());
            changed = true;
        }
        return changed;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank() || "暂无".equals(s.trim());
    }
}
