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
import spring.nexusfuture.word_diary.mapper.WdWordBookMapper;
import spring.nexusfuture.word_diary.mapper.WdWordMapper;
import spring.nexusfuture.word_diary.service.WordService;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WordServiceImpl implements WordService {

    private final WdWordMapper wordMapper;
    private final WdWordBookMapper bookMapper;
    private final WdLearnPlanMapper planMapper;

    public WordServiceImpl(WdWordMapper wordMapper, WdWordBookMapper bookMapper, WdLearnPlanMapper planMapper) {
        this.wordMapper = wordMapper;
        this.bookMapper = bookMapper;
        this.planMapper = planMapper;
    }

    @Override
    @Transactional
    public void addWord(Long userId, Long notebookId, WordAddReq req) {
        WdWordBook book = bookMapper.selectById(notebookId);
        if (book == null || !book.getUserId().equals(userId)) {
            throw new BizException("单词本不存在");
        }

        WdWord word = WdWord.builder()
                .notebookId(notebookId)
                .userId(userId)
                .wordProto(req.getWordProto())
                .usPhonetic(req.getUsPhonetic())
                .ukPhonetic(req.getUkPhonetic())
                .partSpeechMean(req.getPartSpeechMean())
                .sentenceExample(req.getSentenceExample())
                .rootAffix(req.getRootAffix())
                .isPublic(0)
                .build();
        wordMapper.insert(word);

        // Auto-create study plan for the new word
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
        return words.stream().map(word -> {
            WdLearnPlan plan = planMapper.selectByUserIdAndWordId(userId, word.getId());
            return WordVO.builder()
                    .id(word.getId())
                    .notebookId(word.getNotebookId())
                    .wordProto(word.getWordProto())
                    .usPhonetic(word.getUsPhonetic())
                    .ukPhonetic(word.getUkPhonetic())
                    .partSpeechMean(word.getPartSpeechMean())
                    .sentenceExample(word.getSentenceExample())
                    .rootAffix(word.getRootAffix())
                    .learnStatus(plan != null ? plan.getLearnStatus() : LearnStatus.NOT_LEARNED)
                    .currentLevel(plan != null ? plan.getCurrentLevel() : 0)
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(Long userId, Long wordId) {
        WdWord word = wordMapper.selectById(wordId);
        if (word == null || !word.getUserId().equals(userId)) {
            throw new BizException("单词不存在");
        }
        wordMapper.deleteById(wordId);
        // Also mark the associated learn plan as deleted
        planMapper.deleteByWordId(wordId);
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
                WdWord word = WdWord.builder()
                        .notebookId(notebookId)
                        .userId(userId)
                        .wordProto(r.getWordProto())
                        .usPhonetic(r.getUsPhonetic())
                        .ukPhonetic(r.getUkPhonetic())
                        .partSpeechMean(r.getPartSpeechMean())
                        .sentenceExample(r.getSentenceExample())
                        .rootAffix(r.getRootAffix())
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
}
