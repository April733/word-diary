package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * 阅读器偏好设置表
 */
public class WdReaderSettings {

    /** 主键 */
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 字体大小 */
    private Integer fontSize;

    /** 行高 */
    private BigDecimal lineHeight;

    /** 背景颜色 */
    private String backgroundColor;

    /** 文字颜色 */
    private String textColor;

    /** 排版模式，如 paginated（分页）/ scrolled（滚动） */
    private String flow;

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
