package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.entity.WdReaderSettings;

public interface ReaderSettingsService {

    /** 获取用户阅读设置（不存在则返回默认值） */
    WdReaderSettings getSettings(Long userId);

    /** 保存或更新用户阅读设置 */
    WdReaderSettings saveSettings(Long userId, WdReaderSettings settings);
}
