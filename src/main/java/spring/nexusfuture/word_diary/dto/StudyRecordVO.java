package spring.nexusfuture.word_diary.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class StudyRecordVO {
    private Long id;
    private String wordProto;
    private Integer studyType;  // 1=first learn, 2=review
    private Integer result;     // 0=forgot, 1=remembered
    private LocalDateTime studyTime;
    private String notebookName;
}
