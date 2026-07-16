package spring.nexusfuture.word_diary.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StatsVO {
    private long totalWords;
    private long newWords;       // status=0
    private long reviewingWords; // status=1 or 2
    private long masteredWords;  // status=3
    private long todayReviewCount;
    /** 今日待复习（会话队列） */
    private int todayReviewPending;
    /** 今日待新词（会话队列） */
    private int todayNewPending;
    /** 复习阶段是否已完成，可学新词 */
    private boolean reviewPhaseDone;
}
