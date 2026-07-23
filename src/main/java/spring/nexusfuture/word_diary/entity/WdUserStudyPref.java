package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户学习偏好表（wd_user_study_pref）：保存每日新词上限、复习解锁时刻与时区等个人设置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WdUserStudyPref {

    /** 用户ID（主键，一对一） */
    private Long userId;

    /** 每日新词上限（通常 1–200） */
    private Integer dailyNewLimit;

    /** 每日刷新节点小时（0–23，默认 7）：复习与新词同一时刻刷新 */
    private Integer reviewUnlockHour;

    /** 已废弃：是否均摊新词到未来；现查模式固定为 0 */
    private Integer autoSpreadNew;

    /** 用户时区，如 Asia/Shanghai */
    private String timezone;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
