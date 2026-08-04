package spring.nexusfuture.word_diary.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 个人单词库表（wd_word）：存放用户私有单词/短语及其释义、例句、来源定位与学习阶段。
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

    /** 条目类型：word 单词 / phrase 短语 */
    private String entryType;

    /** 来源书籍 */
    private Long sourceBookId;

    /** 采集时原文片段 */
    private String sourceSnippet;

    /** 定位信息 */
    private String sourceLocation;

    /** 用户备注 */
    private String remark;

    /** 是否公开 1公开 0私有 */
    private Integer isPublic;

    /** 创建用户ID，NULL表示系统预置 */
    private Long userId;

    /**
     * 学习状态（见 {@link spring.nexusfuture.word_diary.common.WordStudyStatus}）：
     * 0未学 1~5复习阶梯 6熟记
     */
    private Integer status;

    /**
     * 进入当前 status 的时间；认识升阶时写入当前<strong>学习日</strong>日期（默认每天 7:00 切日）。
     * 到期复习：DATEDIFF(学习日, DATE(status_data)) &gt;= 该阶段间隔天数
     */
    private LocalDateTime statusData;

    /** 创建人 */
    private Long createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 修改人 */
    private Long updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 是否删除：1已删除 0未删除 */
    private Integer isDeleted;
}
