package spring.nexusfuture.word_diary.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectEntryDTO {
    /** word | phrase */
    private String entryType;
    private String wordProto;
    private String partSpeechMean;
    private String usPhonetic;
    private String ukPhonetic;
    private String sentenceExample;
    private String rootAffix;
    /** 已存在时是否仅追加例句 */
    private Boolean appendExample;
}
