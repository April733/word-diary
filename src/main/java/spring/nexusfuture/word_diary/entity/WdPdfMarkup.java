package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * PDF 批注表（wd_pdf_markup）：与原始 PDF 文件分离存储的笔迹/框选/文字笔记，仅归属上传用户本人。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WdPdfMarkup {

    public static final String TYPE_PEN = "pen";
    public static final String TYPE_RECT = "rect";
    public static final String TYPE_TEXT = "text";

    /** 主键 */
    private Long id;

    /** 所属书籍ID */
    private Long bookId;

    /** 用户ID（仅本人可见） */
    private Long userId;

    /** PDF 页码，从 1 起 */
    private Integer pageNum;

    /** 批注类型：pen 笔迹 / rect 矩形框 / text 文字笔记 */
    private String markupType;

    /** 归一化坐标 JSON（相对页面宽高，取值 0~1） */
    private String geometryJson;

    /** 批注颜色，如 #E53935 */
    private String color;

    /** 文字笔记内容（markupType=text 时使用） */
    private String noteText;

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
