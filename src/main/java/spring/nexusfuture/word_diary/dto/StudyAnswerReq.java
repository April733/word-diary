package spring.nexusfuture.word_diary.dto;

import lombok.Data;

@Data
public class StudyAnswerReq {
    private Long planId;
    private Long queueItemId;
    /** 0忘记 1认识 2模糊 */
    private Integer feedback;
    /** review | new */
    private String phase;
}
