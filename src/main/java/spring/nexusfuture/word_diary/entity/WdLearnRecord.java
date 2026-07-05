package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 背诵行为记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WdLearnRecord {

    /** 主键 */
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 单词本ID */
    private Long notebookId;

    /** 单词ID */
    private Long wordId;

    /** 1初次学习 2复习 */
    private Integer studyType;

    /** 0未记住 1记住 */
    private Integer result;

    /** 学习时间 */
    private LocalDateTime studyTime;

    /** 创建人 */
    private Long createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 修改人 */
    private Long updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 是否删除 1已删除 0未删除 */
    private Integer isDeleted;
}
