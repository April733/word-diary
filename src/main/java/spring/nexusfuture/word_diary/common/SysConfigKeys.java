package spring.nexusfuture.word_diary.common;

/** 系统配置键常量 */
public final class SysConfigKeys {

    private SysConfigKeys() {}

    /** 今日复习最早展示时刻（小时 0-23） */
    public static final String STUDY_REVIEW_UNLOCK_HOUR = "study.review_unlock_hour";

    /** 默认每日新词上限（预留） */
    public static final String STUDY_DAILY_NEW_LIMIT_DEFAULT = "study.daily_new_limit_default";

    public static final int DEFAULT_REVIEW_UNLOCK_HOUR = 7;

    // ---- AI 调用场景（与 wd_ai_call_log.call_scene / sys_ai_scene.scene_key 对齐）----

    /** 单词批量导入 / AI 补全空缺 */
    public static final String AI_SCENE_WORD_PARSE = "word_parse";

    /** 阅读：选中文字后的 AI 功能解析 */
    public static final String AI_SCENE_FEATURE = "feature";

    /** 阅读：自由问答 */
    public static final String AI_SCENE_READING_CHAT = "reading_chat";

    /** 雅思写作：评分批改 + 润色/示范范文 */
    public static final String AI_SCENE_WRITING_GRADE = "writing_grade";

    /** 翻译练习：根据已学词出题 */
    public static final String AI_SCENE_TRANSLATION_GENERATE = "translation_generate";

    /** 翻译练习：批改用户译文 */
    public static final String AI_SCENE_TRANSLATION_GRADE = "translation_grade";

    /** 未匹配场景时的回退 */
    public static final String AI_SCENE_DEFAULT = "default";
}
