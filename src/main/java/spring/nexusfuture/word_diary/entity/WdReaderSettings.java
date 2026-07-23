package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 阅读器偏好设置表（wd_reader_settings）：保存用户字体、配色与翻页方式等阅读器偏好。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WdReaderSettings {

    /** 主键 */
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 字体大小（px） */
    private Integer fontSize;

    /** 行高 */
    private BigDecimal lineHeight;

    /** 背景颜色 */
    private String backgroundColor;

    /** 文字颜色 */
    private String textColor;

    /** 排版/翻页模式：paginated 分页 / scrolled 滚动（如 scrolled-doc） */
    private String flow;

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
