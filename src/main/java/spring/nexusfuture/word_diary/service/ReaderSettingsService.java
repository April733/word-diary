package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.entity.WdReaderSettings;

/** 用户阅读器偏好设置（字体、主题、布局等）。 */
public interface ReaderSettingsService {

    /**
     * 获取用户阅读设置；不存在则返回系统默认值。
     *
     * @param userId 当前用户 ID
     * @return 阅读设置实体
     */
    WdReaderSettings getSettings(Long userId);

    /**
     * 保存或更新用户阅读设置。
     *
     * @param userId 当前用户 ID
     * @param settings 待保存的设置内容
     * @return 持久化后的设置实体
     */
    WdReaderSettings saveSettings(Long userId, WdReaderSettings settings);
}
