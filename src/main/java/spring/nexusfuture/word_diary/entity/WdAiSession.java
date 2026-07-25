package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 对话会话表（wd_ai_session）。
 * <p>
 * 保存阅读器助手等多轮对话的会话元数据（按用户、可选按书隔离）。
 * 消息正文见 wd_ai_message；与翻译/写作练习稿表拆分。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WdAiSession {

    /** 主键 */
    private Long id;

    /** 所属用户ID */
    private Long userId;

    /** 关联书籍ID（可选，按书隔离会话） */
    private Long bookId;

    /** 翻译批改记录ID（可选，按练习记录隔离答疑） */
    private Long gradeId;

    /** 会话标题（通常取第一条用户消息摘要） */
    private String title;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 是否删除：1已删除 0未删除 */
    private Integer isDeleted;
}
