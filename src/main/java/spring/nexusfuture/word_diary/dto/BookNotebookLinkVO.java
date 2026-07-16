package spring.nexusfuture.word_diary.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BookNotebookLinkVO {
    private Long notebookId;
    private String notebookName;
    private String linkName;
    private Integer sortNum;
}
