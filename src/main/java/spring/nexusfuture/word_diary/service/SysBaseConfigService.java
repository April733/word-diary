package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.entity.SysBaseConfig;

import java.util.List;

/** 系统基础配置项读写（键值对，来自 sys_base_config）。 */
public interface SysBaseConfigService {

    /**
     * 按 key 读取字符串配置，不存在或为空时返回默认值。
     *
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    String getString(String key, String defaultValue);

    /**
     * 按 key 读取整型配置，不存在或解析失败时返回默认值。
     *
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    int getInt(String key, int defaultValue);

    /**
     * 获取今日复习开放时刻（小时，0–23），来自 sys_base_config。
     *
     * @return 解锁小时
     */
    int getReviewUnlockHour();

    /**
     * 按 key 获取完整配置行。
     *
     * @param key 配置键
     * @return 配置实体；不存在时返回 null
     */
    SysBaseConfig getByKey(String key);

    /**
     * 列出全部系统配置项。
     *
     * @return 配置实体列表
     */
    List<SysBaseConfig> listAll();

    /**
     * 按分类列出配置项。
     *
     * @param category 分类名
     * @return 该分类下的配置列表
     */
    List<SysBaseConfig> listByCategory(String category);

    /**
     * 更新指定 key 的配置值。
     *
     * @param key 配置键
     * @param value 新值
     */
    void updateValue(String key, String value);
}
