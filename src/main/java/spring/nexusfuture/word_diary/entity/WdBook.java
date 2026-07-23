package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 电子书/书架表（wd_book）：保存用户上传的电子书元数据、文件路径与阅读进度。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WdBook {

    /** 主键 */
    private Long id;

    /** 所属用户ID */
    private Long userId;

    /** 书名 */
    private String title;

    /** 作者 */
    private String author;

    /** 封面图片URL */
    private String coverUrl;

    /** 文件存储路径 */
    private String filePath;

    /** 文件类型，如 epub / pdf / txt */
    private String fileType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 总章节数 */
    private Integer totalChapters;

    /** 当前阅读章节索引 */
    private Integer currentChapter;

    /** 当前阅读位置（EPUB CFI） */
    private String currentCfi;

    /** 阅读进度百分比 */
    private BigDecimal progressPercent;

    /** 最近阅读时间 */
    private LocalDateTime lastReadAt;

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
