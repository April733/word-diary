package spring.nexusfuture.word_diary.dto;

import lombok.Data;

import java.util.List;

@Data
public class BookCollectReq {
    private Long notebookId;
    private String sourceSnippet;
    private String sourceLocation;
    private List<CollectEntryDTO> items;
    /** 为 true 时，已存在词条一律覆盖更新（与条目上的 overwrite 任一为真即可） */
    private Boolean overwriteExisting;
}
