package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WdStudyQueue {
    private Long id;
    private Long userId;
    private LocalDate studyDate;
    private Long planId;
    private String queueType;
    private String phase;
    private Integer sortOrder;
    private Integer state;
    private String insertReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static final String TYPE_REVIEW = "review";
    public static final String TYPE_NEW = "new";
    public static final String TYPE_INTRADAY = "intraday";

    public static final int STATE_PENDING = 0;
    public static final int STATE_DONE = 1;
}
