package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 单词库（个人私有单词表）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WdWord {

    /** 单词主键 */
    private Long id;

    /** 归属单词本ID */
    private Long notebookId;

    /** 单词/短语原型 */
    private String wordProto;

    /** 美式音标 */
    private String usPhonetic;

    /** 英式音标 */
    private String ukPhonetic;

    /** 词性+中文含义 */
    private String partSpeechMean;

    /** 例句：书籍收录存「【原文】选中句」一条；手动添加可为空或用户填写 */
    private String sentenceExample;

    /** 词根词缀解析 */
    private String rootAffix;

    /** word | phrase */
    private String entryType;

    /** 来源书籍 */
    private Long sourceBookId;

    /** 采集时原文片段 */
    private String sourceSnippet;

    /** 定位信息 */
    private String sourceLocation;

    /** 是否公开 1公开 0私有 */
    private Integer isPublic;

    /** 创建用户ID，NULL表示系统预置 */
    private Long userId;

    /** 创建人 */
    private Long createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 修改人 */
    private Long updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 是否删除 1已删除 0未删除 */
    private Integer isDeleted;

    /** 移入垃圾桶时间；满 7 天后物理删除 */
    private LocalDateTime deletedAt;
}
