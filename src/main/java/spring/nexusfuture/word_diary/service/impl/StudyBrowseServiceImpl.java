package spring.nexusfuture.word_diary.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import spring.nexusfuture.word_diary.common.BizException;
import spring.nexusfuture.word_diary.common.StudyFeedback;
import spring.nexusfuture.word_diary.common.WordStudyStatusEnum;
import spring.nexusfuture.word_diary.dto.request.StudyBrowseAnswerReq;
import spring.nexusfuture.word_diary.dto.response.StudyBrowsePageVO;
import spring.nexusfuture.word_diary.dto.response.StudyTaskVO;
import spring.nexusfuture.word_diary.entity.WdWord;
import spring.nexusfuture.word_diary.entity.WdWordBook;
import spring.nexusfuture.word_diary.mapper.WdWordBookMapper;
import spring.nexusfuture.word_diary.mapper.WdWordMapper;
import spring.nexusfuture.word_diary.service.StudyBrowseService;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class StudyBrowseServiceImpl implements StudyBrowseService {

    private final WdWordMapper wordMapper;
    private final WdWordBookMapper bookMapper;

    public StudyBrowseServiceImpl(WdWordMapper wordMapper, WdWordBookMapper bookMapper) {
        this.wordMapper = wordMapper;
        this.bookMapper = bookMapper;
    }

    @Override
    public StudyBrowsePageVO listWords(Long userId, Long notebookId, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        int offset = (safePage - 1) * safeSize;

        int total = wordMapper.countBrowseWords(userId, notebookId);
        List<WdWord> words = wordMapper.selectBrowseWords(userId, notebookId, safeSize, offset);
        List<StudyTaskVO> items = words.stream().map(this::toTaskVO).collect(Collectors.toList());

        return StudyBrowsePageVO.builder()
                .items(items)
                .total(total)
                .page(safePage)
                .pageSize(safeSize)
                .build();
    }

    @Override
    @Transactional
    public void submitAnswer(Long userId, StudyBrowseAnswerReq req) {
        if (req == null || req.getWordId() == null || req.getFeedback() == null) {
            throw new BizException("参数不完整");
        }
        if (!StudyFeedback.isValid(req.getFeedback())) {
            throw new BizException("无效的反馈类型");
        }

        WdWord word = wordMapper.selectById(req.getWordId());
        if (word == null) {
            throw new BizException("单词不存在");
        }
        boolean owned = word.getUserId() != null && word.getUserId().equals(userId);
        if (!owned) {
            WdWordBook book = bookMapper.selectById(word.getNotebookId());
            if (book == null || !book.getUserId().equals(userId)) {
                throw new BizException("单词不存在");
            }
        }
        WordStudyStatusEnum status = WordStudyStatusEnum.fromCode(word.getStatus());
        if (!status.isInReviewPipeline() && !status.isMastered()) {
            throw new BizException("该单词不可回顾");
        }
        // 浏览模式：仅校验，不改 status
    }

    private StudyTaskVO toTaskVO(WdWord word) {
        WdWordBook book = bookMapper.selectById(word.getNotebookId());
        return StudyTaskVO.builder()
                .wordId(word.getId())
                .wordProto(word.getWordProto() != null ? word.getWordProto() : "")
                .usPhonetic(word.getUsPhonetic() != null ? word.getUsPhonetic() : "")
                .ukPhonetic(word.getUkPhonetic() != null ? word.getUkPhonetic() : "")
                .partSpeechMean(word.getPartSpeechMean() != null ? word.getPartSpeechMean() : "")
                .sentenceExample(word.getSentenceExample() != null ? word.getSentenceExample() : "")
                .rootAffix(word.getRootAffix() != null ? word.getRootAffix() : "")
                .remark(word.getRemark() != null ? word.getRemark() : "")
                .learnStatus(word.getStatus())
                .statusData(word.getStatusData())
                .notebookName(book != null ? book.getBookName() : "")
                .build();
    }
}
