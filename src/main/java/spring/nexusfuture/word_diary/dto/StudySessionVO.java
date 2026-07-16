package spring.nexusfuture.word_diary.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StudySessionVO {
    private Long dailyId;
    private String phase;
    private StudyTaskVO current;
    private Long queueItemId;
    private int reviewRemaining;
    private int newRemaining;
    private boolean reviewPhaseDone;
    private StudyTodayVO today;
}
