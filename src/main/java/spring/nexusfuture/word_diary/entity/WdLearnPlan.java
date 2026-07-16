package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 个人单词背诵学习计划
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WdLearnPlan {

    /** 主键 */
    private Long id;

    /** 所属用户 */
    private Long userId;

    /** 所属单词本 */
    private Long notebookId;

    /** 关联单词 */
    private Long wordId;

    /** 关联学习状态 */
    private Integer learnStatus;

    /** 当前艾宾浩斯层级 */
    private Integer currentLevel;

    /** 首次学习时间 */
    private LocalDateTime firstStudyTime;

    /** 下次复习时间 */
    private LocalDateTime nextReviewTime;

    /** 累计复习次数 */
    private Integer reviewTimes;

    /** 当前复习间隔(天) */
    private BigDecimal intervalDays;

    /** 难度系数 */
    private BigDecimal easeFactor;

    /** 连续成功复习次数 */
    private Integer repCount;

    /** 遗忘次数 */
    private Integer lapseCount;

    /** 最近一次反馈: 0忘记 1认识 2模糊 */
    private Integer lastFeedback;

    /** 最近一次正式学习时间 */
    private LocalDateTime lastStudyAt;

    /** 复习优先级分 */
    private Integer priorityScore;

    /** 计划作为今日新词出现的日期 */
    private LocalDate scheduledNewDate;

    /** 在该日新词队列中的顺序 */
    private Integer newAllocSeq;

    /** 创建人 */
    private Long createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 修改人 */
    private Long updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 是否删除 1已删除 0未删除 */
    private Integer isDeleted;
}
