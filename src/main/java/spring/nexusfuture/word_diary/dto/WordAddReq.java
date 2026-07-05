package spring.nexusfuture.word_diary.dto;

import lombok.Data;

@Data
public class WordAddReq {
    private String wordProto;
    private String usPhonetic;
    private String ukPhonetic;
    private String partSpeechMean;
    private String sentenceExample;
    private String rootAffix;
}
