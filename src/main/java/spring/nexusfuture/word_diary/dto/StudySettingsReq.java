package spring.nexusfuture.word_diary.dto;

import lombok.Data;

@Data
public class StudySettingsReq {
    private Integer dailyNewLimit;
    private Integer reviewUnlockHour;
    private Boolean autoSpreadNew;
}
