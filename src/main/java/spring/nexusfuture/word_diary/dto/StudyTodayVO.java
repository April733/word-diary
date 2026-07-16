package spring.nexusfuture.word_diary.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class StudyTodayVO {
    private LocalDate studyDate;
    private int reviewUnlockHour;
    private int reviewPending;
    private int reviewDone;
    private int newPending;
    private int newDone;
    private boolean reviewPhaseDone;
    private boolean newPhaseUnlocked;
    private String currentPhase;
}
