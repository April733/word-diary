package spring.nexusfuture.word_diary.common;

/** 学习反馈：认识 / 不认识（历史 FUZZY=2 仍接受，按不认识处理） */
public final class StudyFeedback {

    private StudyFeedback() {}

    public static final int FORGET = 0;
    public static final int KNOW = 1;
    /** @deprecated 已取消「模糊」档，提交时按 FORGET 处理 */
    public static final int FUZZY = 2;

    public static boolean isValid(int feedback) {
        return feedback == FORGET || feedback == KNOW || feedback == FUZZY;
    }

    /** 归一为二档：认识 / 不认识 */
    public static int normalize(int feedback) {
        return feedback == KNOW ? KNOW : FORGET;
    }
}
