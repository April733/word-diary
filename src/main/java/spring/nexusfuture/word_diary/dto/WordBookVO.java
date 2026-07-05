package spring.nexusfuture.word_diary.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WordBookVO {
    private Long id;
    private String bookName;
    private String bookDesc;
    private Integer sortNum;
    private Integer status;
    private Long wordCount;
}
