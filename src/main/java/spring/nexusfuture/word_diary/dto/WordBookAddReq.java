package spring.nexusfuture.word_diary.dto;

import lombok.Data;

@Data
public class WordBookAddReq {
    private String bookName;
    private String bookDesc;
    private Integer sortNum;
    /** 所属阅读书籍；创建时传入则归属该书，不传则为独立单词本 */
    private Long bookId;
}
