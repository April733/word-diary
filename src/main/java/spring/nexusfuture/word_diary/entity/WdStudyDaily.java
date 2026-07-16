package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WdStudyDaily {
    private Long id;
    private Long userId;
    private LocalDate studyDate;
    private Integer reviewTotal;
    private Integer reviewDone;
    private Integer newTotal;
    private Integer newDone;
    private Integer reviewPhaseDone;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
