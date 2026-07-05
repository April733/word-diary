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
}
