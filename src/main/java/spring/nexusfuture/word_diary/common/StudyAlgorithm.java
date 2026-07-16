package spring.nexusfuture.word_diary.common;

import spring.nexusfuture.word_diary.entity.WdLearnPlan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 动态间隔（SM-2 变体）+ 复习优先级
 */
public final class StudyAlgorithm {

    private static final BigDecimal MIN_EASE = new BigDecimal("1.30");
    private static final BigDecimal MAX_EASE = new BigDecimal("3.00");
    private static final int MASTERED_REP = 8;
    private static final int MASTERED_INTERVAL_DAYS = 180;

    private StudyAlgorithm() {}

    public static int calcPriority(WdLearnPlan plan, LocalDate today) {
        int score = 0;
        Integer fb = plan.getLastFeedback();
        if (fb != null) {
            if (fb == StudyFeedback.FORGET) score += 1000;
            else if (fb == StudyFeedback.FUZZY) score += 500;
        }
        if (plan.getNextReviewTime() != null) {
            LocalDate due = plan.getNextReviewTime().toLocalDate();
            if (due.isBefore(today)) {
                score += (int) ChronoUnit.DAYS.between(due, today) * 50;
            }
        }
        int lapses = plan.getLapseCount() != null ? plan.getLapseCount() : 0;
        score += lapses * 20;
        BigDecimal interval = plan.getIntervalDays() != null ? plan.getIntervalDays() : BigDecimal.ZERO;
        score += Math.max(0, 30 - interval.intValue()) * 10;
        return score;
    }

    /**
     * 应用反馈，更新 plan 长期状态（正式每日学习）
     *
     * @param isNewPhase true=新词初学，false=复习
     */
    public static void applyFeedback(WdLearnPlan plan, int feedback, LocalDate today, boolean isNewPhase) {
        BigDecimal ease = plan.getEaseFactor() != null ? plan.getEaseFactor() : new BigDecimal("2.50");
        BigDecimal interval = plan.getIntervalDays() != null ? plan.getIntervalDays() : BigDecimal.ZERO;
        int rep = plan.getRepCount() != null ? plan.getRepCount() : 0;
        int lapses = plan.getLapseCount() != null ? plan.getLapseCount() : 0;

        plan.setLastFeedback(feedback);
        plan.setLastStudyAt(LocalDateTime.now());

        if (feedback == StudyFeedback.FORGET) {
            plan.setLearnStatus(LearnStatus.NOT_LEARNED);
            plan.setIntervalDays(BigDecimal.ZERO);
            plan.setRepCount(0);
            plan.setCurrentLevel(0);
            plan.setNextReviewTime(null);
            plan.setLapseCount(lapses + 1);
            ease = ease.subtract(new BigDecimal("0.20"));
            plan.setEaseFactor(clampEase(ease));
            plan.setScheduledNewDate(null);
            plan.setPriorityScore(calcPriority(plan, today));
            return;
        }

        if (feedback == StudyFeedback.FUZZY) {
            ease = ease.subtract(new BigDecimal("0.15"));
            plan.setEaseFactor(clampEase(ease));
            if (isNewPhase) {
                plan.setLearnStatus(LearnStatus.NOT_LEARNED);
                plan.setNextReviewTime(null);
            } else {
                interval = interval.multiply(new BigDecimal("0.5")).max(BigDecimal.ONE);
                plan.setIntervalDays(interval);
                plan.setLearnStatus(LearnStatus.REVIEWING);
                plan.setNextReviewTime(today.atStartOfDay().plusDays(interval.setScale(0, RoundingMode.CEILING).longValue()));
            }
            plan.setPriorityScore(calcPriority(plan, today));
            return;
        }

        // KNOW
        ease = ease.add(new BigDecimal("0.10"));
        plan.setEaseFactor(clampEase(ease));
        rep += 1;
        plan.setRepCount(rep);
        plan.setReviewTimes((plan.getReviewTimes() != null ? plan.getReviewTimes() : 0) + 1);

        if (isNewPhase) {
            plan.setLearnStatus(LearnStatus.FIRST_LEARNED);
            plan.setCurrentLevel(1);
            if (plan.getFirstStudyTime() == null) {
                plan.setFirstStudyTime(LocalDateTime.now());
            }
            interval = BigDecimal.ONE;
            plan.setIntervalDays(interval);
            plan.setNextReviewTime(today.atStartOfDay().plusDays(1));
            plan.setScheduledNewDate(null);
            plan.setPriorityScore(calcPriority(plan, today));
            return;
        }

        if (interval.compareTo(BigDecimal.ONE) <= 0) {
            interval = new BigDecimal("1");
        } else {
            interval = interval.multiply(ease);
        }
        plan.setIntervalDays(interval);
        plan.setLearnStatus(LearnStatus.REVIEWING);
        plan.setCurrentLevel(rep);

        long days = interval.setScale(0, RoundingMode.CEILING).longValue();
        if (rep >= MASTERED_REP && days >= MASTERED_INTERVAL_DAYS) {
            plan.setLearnStatus(LearnStatus.MASTERED);
            plan.setNextReviewTime(null);
        } else {
            plan.setNextReviewTime(today.atStartOfDay().plusDays(Math.max(1, days)));
        }
        plan.setPriorityScore(calcPriority(plan, today));
    }

    private static BigDecimal clampEase(BigDecimal ease) {
        if (ease.compareTo(MIN_EASE) < 0) return MIN_EASE;
        if (ease.compareTo(MAX_EASE) > 0) return MAX_EASE;
        return ease.setScale(2, RoundingMode.HALF_UP);
    }
}
