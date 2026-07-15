package spring.nexusfuture.word_diary.dto;

import lombok.Data;

@Data
public class AnnotationReq {
    private Long bookId;
    private Integer chapterIndex;
    private String cfiStart;
    private String cfiEnd;
    private String textContent;
    private String note;
    private String color;
}
