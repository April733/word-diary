package spring.nexusfuture.word_diary.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StatsVO {
    /** 在背单词本合计（已开启参与每日学习） */
    private long totalWords;
    /** 等待加入背诵计划（未学，learn_status=0） */
    private long newWords;
    /** 正在背（初学/复习中，learn_status=1/2） */
    private long reviewingWords;
    /** 全部已完成/已熟记（learn_status=3） */
    private long masteredWords;
    /** 未开启「在背」的单词本中的词数 */
    private long pausedWords;
    private long todayReviewCount;
    /** 今日待复习（会话队列） */
    private int todayReviewPending;
    /** 今日待新词（会话队列） */
    private int todayNewPending;
    /** 复习阶段是否已完成，可学新词 */
    private boolean reviewPhaseDone;
}
