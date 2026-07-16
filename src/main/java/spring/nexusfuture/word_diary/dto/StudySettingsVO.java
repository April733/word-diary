package spring.nexusfuture.word_diary.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StudySettingsVO {
    private int dailyNewLimit;
    private int reviewUnlockHour;
    private boolean autoSpreadNew;
}
