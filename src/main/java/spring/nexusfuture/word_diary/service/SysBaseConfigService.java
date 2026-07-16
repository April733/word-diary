package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.entity.SysBaseConfig;

import java.util.List;

public interface SysBaseConfigService {

    String getString(String key, String defaultValue);

    int getInt(String key, int defaultValue);

    /** 今日复习开放时刻（小时），来自 sys_base_config */
    int getReviewUnlockHour();

    SysBaseConfig getByKey(String key);

    List<SysBaseConfig> listAll();

    List<SysBaseConfig> listByCategory(String category);

    void updateValue(String key, String value);
}
