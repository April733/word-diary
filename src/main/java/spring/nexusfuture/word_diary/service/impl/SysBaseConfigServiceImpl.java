package spring.nexusfuture.word_diary.service.impl;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import spring.nexusfuture.word_diary.common.SysConfigKeys;
import spring.nexusfuture.word_diary.entity.SysBaseConfig;
import spring.nexusfuture.word_diary.mapper.SysBaseConfigMapper;
import spring.nexusfuture.word_diary.service.SysBaseConfigService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SysBaseConfigServiceImpl implements SysBaseConfigService {

    private final SysBaseConfigMapper configMapper;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public SysBaseConfigServiceImpl(SysBaseConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    @PostConstruct
    public void loadCache() {
        cache.clear();
        for (SysBaseConfig c : configMapper.selectAll()) {
            if (c.getIsEnabled() != null && c.getIsEnabled() == 1) {
                cache.put(c.getConfigKey(), c.getConfigValue());
            }
        }
    }

    @Override
    public String getString(String key, String defaultValue) {
        String v = cache.get(key);
        if (v != null) {
            return v;
        }
        SysBaseConfig row = configMapper.selectByKey(key);
        if (row != null) {
            cache.put(key, row.getConfigValue());
            return row.getConfigValue();
        }
        return defaultValue;
    }

    @Override
    public int getInt(String key, int defaultValue) {
        String raw = getString(key, null);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public int getReviewUnlockHour() {
        int v = getInt(SysConfigKeys.STUDY_REVIEW_UNLOCK_HOUR, SysConfigKeys.DEFAULT_REVIEW_UNLOCK_HOUR);
        return Math.max(0, Math.min(23, v));
    }

    @Override
    public SysBaseConfig getByKey(String key) {
        return configMapper.selectByKey(key);
    }

    @Override
    public List<SysBaseConfig> listAll() {
        return configMapper.selectAll();
    }

    @Override
    public List<SysBaseConfig> listByCategory(String category) {
        return configMapper.selectByCategory(category);
    }

    @Override
    public void updateValue(String key, String value) {
        configMapper.updateValue(key, value);
        cache.put(key, value);
    }
}
