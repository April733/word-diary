package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.dto.AiFeaturePreferenceVO;
import spring.nexusfuture.word_diary.entity.SysAiFeature;

import java.util.List;

public interface AiFeatureService {
    List<SysAiFeature> listActiveFeatures(String locale);

    /** 获取用户偏好+全量功能列表（featureName/description 已按 locale 解析） */
    AiFeaturePreferenceVO getPreferences(Long userId, String locale);

    /** 保存用户偏好（含排序） */
    void savePreferences(Long userId, List<String> featureKeys, List<String> featureOrder);
}
