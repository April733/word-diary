package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.entity.SysMemberTier;

public interface MemberTierService {

    /** 获取指定等级的配置 */
    SysMemberTier getByTier(Integer tier);

    /** 获取默认等级（最低等级） */
    SysMemberTier getDefaultTier();
}
