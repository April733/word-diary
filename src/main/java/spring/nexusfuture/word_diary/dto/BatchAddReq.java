package spring.nexusfuture.word_diary.dto;

import lombok.Data;
import java.util.List;

@Data
public class BatchAddReq {
    private Long notebookId;
    private List<AiParseResult> words;
}
