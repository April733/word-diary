package spring.nexusfuture.word_diary.dto;

import lombok.Data;

@Data
public class WordBookAddReq {
    private String bookName;
    private String bookDesc;
    private Integer sortNum;
}
