package spring.nexusfuture.word_diary.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import spring.nexusfuture.word_diary.entity.WdReaderSettings;
import spring.nexusfuture.word_diary.mapper.WdReaderSettingsMapper;
import spring.nexusfuture.word_diary.service.ReaderSettingsService;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReaderSettingsServiceImpl implements ReaderSettingsService {

    private final WdReaderSettingsMapper settingsMapper;

    @Override
    public WdReaderSettings getSettings(Long userId) {
        WdReaderSettings settings = settingsMapper.selectByUserId(userId);
        if (settings == null) {
            // 返回默认设置
            return WdReaderSettings.builder()
                    .userId(userId)
                    .fontSize(16)
                    .lineHeight(new BigDecimal("1.6"))
                    .backgroundColor("#ffffff")
                    .textColor("#1a1a2e")
                    .flow("paginated")
                    .build();
        }
        return settings;
    }

    @Override
    @Transactional
    public WdReaderSettings saveSettings(Long userId, WdReaderSettings settings) {
        WdReaderSettings existing = settingsMapper.selectByUserId(userId);
        if (existing == null) {
            // 新增
            WdReaderSettings newSettings = WdReaderSettings.builder()
                    .userId(userId)
                    .fontSize(settings.getFontSize() != null ? settings.getFontSize() : 16)
                    .lineHeight(settings.getLineHeight() != null ? settings.getLineHeight() : new BigDecimal("1.6"))
                    .backgroundColor(settings.getBackgroundColor() != null ? settings.getBackgroundColor() : "#ffffff")
                    .textColor(settings.getTextColor() != null ? settings.getTextColor() : "#1a1a2e")
                    .flow(settings.getFlow() != null ? settings.getFlow() : "paginated")
                    .createBy(userId)
                    .createTime(LocalDateTime.now())
                    .updateBy(userId)
                    .updateTime(LocalDateTime.now())
                    .isDeleted(0)
                    .build();
            settingsMapper.insert(newSettings);
            log.info("用户 {} 创建阅读设置", userId);
            return newSettings;
        } else {
            // 更新已有记录
            if (settings.getFontSize() != null) existing.setFontSize(settings.getFontSize());
            if (settings.getLineHeight() != null) existing.setLineHeight(settings.getLineHeight());
            if (settings.getBackgroundColor() != null) existing.setBackgroundColor(settings.getBackgroundColor());
            if (settings.getTextColor() != null) existing.setTextColor(settings.getTextColor());
            if (settings.getFlow() != null) existing.setFlow(settings.getFlow());
            existing.setUpdateBy(userId);
            settingsMapper.updateByUserId(existing);
            log.info("用户 {} 更新阅读设置", userId);
            return existing;
        }
    }
}
