package spring.nexusfuture.word_diary.dto;

import lombok.Data;
import java.util.List;

@Data
public class AiParseReq {
    private Long notebookId;
    private List<String> words;
}
