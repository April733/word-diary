package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户 AI 功能偏好表（wd_user_ai_preference）：保存用户启用的 AI 功能列表及其展示排序。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WdUserAiPreference {

    /** 主键 */
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 用户启用的功能 feature_key 列表，JSON 数组，如 ["word","collocation"] */
    private String featureKeys;

    /** 用户自定义功能排序，JSON 数组，如 ["word","example","collocation"] */
    private String featureOrder;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
