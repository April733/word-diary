package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户反馈表（wd_feedback）：记录产品问题反馈与错误详情，供运营/开发跟进处理。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WdFeedback {

    /** 主键 */
    private Long id;

    /** 提交用户ID；未登录提交时可为空 */
    private Long userId;

    /** 提交时的用户名快照 */
    private String username;

    /** 反馈人联系邮箱 */
    private String reporterEmail;

    /** 反馈主题/标题 */
    private String subject;

    /** 问题描述正文 */
    private String description;

    /** 错误详情（堆栈、接口返回等，可空） */
    private String errorDetail;

    /** 处理状态：0待处理 1已处理 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 是否删除：1已删除 0未删除 */
    private Integer isDeleted;
}
