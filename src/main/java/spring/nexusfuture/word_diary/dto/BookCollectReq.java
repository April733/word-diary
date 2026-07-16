package spring.nexusfuture.word_diary.dto;

import lombok.Data;

import java.util.List;

@Data
public class BookCollectReq {
    private Long notebookId;
    private String sourceSnippet;
    private String sourceLocation;
    private List<CollectEntryDTO> items;
}
