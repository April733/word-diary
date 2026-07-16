package spring.nexusfuture.word_diary.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class StudyRecordVO {
    private Long id;
    private String wordProto;
    private Integer studyType;  // 1=first learn, 2=review, 3=browse
    private Integer result;     // 0=forgot, 1=remembered (legacy)
    private Integer feedback;   // 0=忘记 1=认识 2=模糊
    private Integer studyMode;  // 1=正式每日 2=自由回顾
    private LocalDateTime studyTime;
    private String notebookName;
}
