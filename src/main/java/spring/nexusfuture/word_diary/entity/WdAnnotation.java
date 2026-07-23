package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 阅读标注/笔记表（wd_annotation）：保存 EPUB 等阅读器中的高亮选区与用户笔记。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WdAnnotation {

    /** 主键 */
    private Long id;

    /** 关联书籍ID */
    private Long bookId;

    /** 用户ID */
    private Long userId;

    /** 章节索引 */
    private Integer chapterIndex;

    /** EPUB CFI 起始位置 */
    private String cfiStart;

    /** EPUB CFI 结束位置 */
    private String cfiEnd;

    /** 标注的文本内容 */
    private String textContent;

    /** 用户笔记 */
    private String note;

    /** 高亮颜色 */
    private String color;

    /** 创建人 */
    private Long createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 修改人 */
    private Long updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 是否删除：1已删除 0未删除 */
    private Integer isDeleted;
}
