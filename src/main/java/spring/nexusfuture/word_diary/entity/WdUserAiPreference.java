package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * 用户AI功能偏好表
 */
public class WdUserAiPreference {

    /** 主键 */
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 用户启用的功能 feature_key 列表，JSON数组格式，如 ["word","collocation"] */
    private String featureKeys;

    /** 用户自定义功能排序，JSON数组格式，如 ["word","example","collocation"] */
    private String featureOrder;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
