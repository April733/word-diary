package spring.nexusfuture.word_diary.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class StudyTaskVO {
    private Long planId;
    private Long wordId;
    private String wordProto;
    private String usPhonetic;
    private String ukPhonetic;
    private String partSpeechMean;
    private String sentenceExample;
    private String rootAffix;
    private Integer learnStatus;
    private Integer currentLevel;
    private Integer reviewTimes;
    private LocalDateTime nextReviewTime;
    private String notebookName;
}
