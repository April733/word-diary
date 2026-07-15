package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 对话消息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WdAiMessage {

    /** 主键 */
    private Long id;

    /** 所属会话ID */
    private Long sessionId;

    /** 关联的问题ID（assistant 消息指向对应的 user 消息） */
    private Long parentId;

    /** 角色: user / assistant */
    private String role;

    /** 消息内容 */
    private String content;

    /** 触发此消息的 AI 功能标识（可空） */
    private String featureKey;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 是否删除 1已删除 0未删除 */
    private Integer isDeleted;
}
