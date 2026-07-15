package spring.nexusfuture.word_diary.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiParseResult {
    private String wordProto;
    private String usPhonetic;
    private String ukPhonetic;
    private String partSpeechMean;
    private String sentenceExample;
    private String rootAffix;
}
