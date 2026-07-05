package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 单词学习状态配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysLearnStatus {

    /** 状态编码 */
    private Integer status;

    /** 状态名称 */
    private String statusName;

    /** 备注说明 */
    private String remark;

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
