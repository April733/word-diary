package spring.nexusfuture.word_diary.common;

import spring.nexusfuture.word_diary.entity.WdWord;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 固定间隔艾宾浩斯：只维护 {@code status} + {@code statusData}。
 * <p>
 * 今日单词「认识」满 2 遍后调用 {@link #applyKnow}：升一阶，status_data=学习日。
 * 「不认识」不调本类（遍数归零由 Redis passStore.clear；不改 status）。
 */
public final class StudyAlgorithm {

    private StudyAlgorithm() {}

    /**
     * 认识满 2 遍后升阶：status+1（0→1→…→6），status_data=学习日。
     *
     * @param studyDate 当前学习日（默认每天 7:00 换日）
     */
    public static void applyKnow(WdWord word, LocalDate studyDate) {
        WordStudyStatus next = WordStudyStatus.fromCode(word.getStatus()).nextOnKnow();
        word.setStatus(next.getCode());
        word.setStatusData(studyDate.atStartOfDay());
    }

    /**
     * @deprecated 今日单词已不再「不认识打回 0」；保留供其它场景如需强制打回时使用。
     */
    @Deprecated
    public static void applyFeedback(WdWord word, int feedback, LocalDate studyDate) {
        if (feedback == StudyFeedback.KNOW) {
            applyKnow(word, studyDate);
            return;
        }
        word.setStatus(WordStudyStatus.UN_STUDY.getCode());
        word.setStatusData(LocalDateTime.now());
    }
}
