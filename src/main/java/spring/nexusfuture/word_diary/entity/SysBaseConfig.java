package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 系统基础配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysBaseConfig {

    private Long id;
    private String configKey;
    private String configValue;
    private String valueType;
    private String category;
    private String description;
    private Integer sortNum;
    private Integer isEnabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
