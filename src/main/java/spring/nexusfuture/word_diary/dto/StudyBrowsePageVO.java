package spring.nexusfuture.word_diary.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class StudyBrowsePageVO {
    private List<StudyTaskVO> items;
    private long total;
    private int page;
    private int pageSize;
}
