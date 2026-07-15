package spring.nexusfuture.word_diary.service.impl;

import org.springframework.stereotype.Service;
import spring.nexusfuture.word_diary.entity.SysMemberTier;
import spring.nexusfuture.word_diary.mapper.SysMemberTierMapper;
import spring.nexusfuture.word_diary.service.MemberTierService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MemberTierServiceImpl implements MemberTierService {

    private final SysMemberTierMapper mapper;

    /** 缓存所有等级配置，避免频繁查库 */
    private final Map<Integer, SysMemberTier> cache = new ConcurrentHashMap<>();
    private volatile Integer defaultTier;

    public MemberTierServiceImpl(SysMemberTierMapper mapper) {
        this.mapper = mapper;
        refreshCache();
    }

    @Override
    public SysMemberTier getByTier(Integer tier) {
        if (tier == null) {
            tier = getDefaultTier().getTier();
        }
        return cache.computeIfAbsent(tier, t -> {
            SysMemberTier config = mapper.selectByTier(t);
            if (config == null && t > 1) {
                // 找不到对应等级，降级到默认等级
                return getDefaultTier();
            }
            return config;
        });
    }

    @Override
    public SysMemberTier getDefaultTier() {
        return cache.get(defaultTier);
    }

    /** 刷新缓存（管理端修改配置后调用） */
    public void refreshCache() {
        List<SysMemberTier> all = mapper.selectAll();
        cache.clear();
        for (SysMemberTier tier : all) {
            cache.put(tier.getTier(), tier);
        }
        // 默认等级取最小的 tier
        defaultTier = all.stream()
                .mapToInt(SysMemberTier::getTier)
                .min()
                .orElse(1);
    }
}
