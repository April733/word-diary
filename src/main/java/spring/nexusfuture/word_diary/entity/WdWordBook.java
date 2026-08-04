package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 单词本表（wd_word_book）：管理用户单词本（可独立或归属某本书），并控制是否参与每日学习。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WdWordBook {

    /** 单词本ID */
    private Long id;

    /** 所属用户ID */
    private Long userId;

    /** 所属阅读书籍ID；NULL=独立单词本，非空则仅属于该书 */
    private Long bookId;

    /** 来源分类：阅读|听力 */
    private String sourceType;

    /** 单词本名称 */
    private String bookName;

    /** 单词本简介 */
    private String bookDesc;

    /** 排序权重 */
    private Integer sortNum;

    /** 是否参与每日学习：1参与 0不参与（单词本本身仍可查看/编辑） */
    private Integer status;

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
