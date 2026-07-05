package spring.nexusfuture.word_diary.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WordVO {
    private Long id;
    private Long notebookId;
    private String wordProto;
    private String usPhonetic;
    private String ukPhonetic;
    private String partSpeechMean;
    private String sentenceExample;
    private String rootAffix;
    private Integer learnStatus;
    private Integer currentLevel;
}
