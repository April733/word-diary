package spring.nexusfuture.word_diary.dto;

import lombok.Data;

@Data
public class StudyBrowseAnswerReq {
    private Long planId;
    /** 0忘记 1认识 2模糊 */
    private Integer feedback;
}
