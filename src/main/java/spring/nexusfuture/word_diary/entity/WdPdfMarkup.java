package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * PDF 批注（与原始 PDF 文件分离；仅归属上传用户本人）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WdPdfMarkup {

    public static final String TYPE_PEN = "pen";
    public static final String TYPE_RECT = "rect";
    public static final String TYPE_TEXT = "text";

    private Long id;
    private Long bookId;
    private Long userId;
    /** PDF 页码，从 1 起 */
    private Integer pageNum;
    /** pen | rect | text */
    private String markupType;
    /** 归一化坐标 JSON（0~1） */
    private String geometryJson;
    private String color;
    /** 文字笔记内容（type=text） */
    private String noteText;
    private Long createBy;
    private LocalDateTime createTime;
    private Long updateBy;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
