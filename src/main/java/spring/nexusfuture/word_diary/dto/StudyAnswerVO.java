package spring.nexusfuture.word_diary.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StudyAnswerVO {
    private StudyTaskVO next;
    private StudyTodayVO today;
    private boolean phaseSwitched;
    private String phase;
    private Long queueItemId;
}
