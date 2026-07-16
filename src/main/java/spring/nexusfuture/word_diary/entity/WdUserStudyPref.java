package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WdUserStudyPref {
    private Long userId;
    private Integer dailyNewLimit;
    private Integer reviewUnlockHour;
    private Integer autoSpreadNew;
    private String timezone;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
