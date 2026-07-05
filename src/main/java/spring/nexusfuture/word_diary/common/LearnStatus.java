package spring.nexusfuture.word_diary.common;

public class LearnStatus {
    public static final int NOT_LEARNED = 0;    // 未学习
    public static final int FIRST_LEARNED = 1;  // 已初学
    public static final int REVIEWING = 2;      // 复习中
    public static final int MASTERED = 3;       // 已熟记
    public static final int FORGOTTEN = 4;      // 遗忘重学

    public static final int STUDY_TYPE_FIRST = 1;   // 初次学习
    public static final int STUDY_TYPE_REVIEW = 2;  // 复习
    public static final int RESULT_REMEMBER = 1;    // 记住了
    public static final int RESULT_FORGET = 0;      // 未记住
}
