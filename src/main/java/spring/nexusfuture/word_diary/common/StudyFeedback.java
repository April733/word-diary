package spring.nexusfuture.word_diary.common;

/** 学习三档反馈 */
public final class StudyFeedback {

    private StudyFeedback() {}

    public static final int FORGET = 0;
    public static final int KNOW = 1;
    public static final int FUZZY = 2;

    public static boolean isValid(int feedback) {
        return feedback == FORGET || feedback == KNOW || feedback == FUZZY;
    }
}
