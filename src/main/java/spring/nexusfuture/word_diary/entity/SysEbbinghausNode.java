package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 艾宾浩斯复习间隔配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysEbbinghausNode {

    /** 复习层级ID */
    private Integer id;

    /** 阶段名称 */
    private String nodeName;

    /** 距初次学习间隔天数 */
    private Integer dayInterval;

    /** 排序顺序 */
    private Integer sort;

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
