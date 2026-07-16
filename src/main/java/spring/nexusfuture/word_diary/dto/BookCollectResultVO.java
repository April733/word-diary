package spring.nexusfuture.word_diary.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BookCollectResultVO {
    private int added;
    private int skipped;
    private int updated;
    private List<String> messages;
}
