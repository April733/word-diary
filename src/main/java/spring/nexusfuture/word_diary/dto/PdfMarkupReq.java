package spring.nexusfuture.word_diary.dto;

import lombok.Data;

@Data
public class PdfMarkupReq {
    /** 页码，从 1 起 */
    private Integer pageNum;
    /** pen | rect | text */
    private String markupType;
    /** 归一化坐标 JSON */
    private String geometryJson;
    private String color;
    private String noteText;
}
