package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.dto.response.AiFeaturePreferenceVO;
import spring.nexusfuture.word_diary.entity.SysAiFeature;

import java.util.List;

/** 客户端 AI 功能卡片：列表、用户偏好与排序。 */
public interface AiFeatureService {

    /**
     * 列出已启用的 AI 功能（按 locale 解析名称与描述）。
     *
     * @param locale 语言区域，如 zh-CN、en
     * @return 功能实体列表
     */
    List<SysAiFeature> listActiveFeatures(String locale);

    /**
     * 获取用户偏好与全量功能列表（featureName/description 已按 locale 解析）。
     *
     * @param userId 当前用户 ID
     * @param locale 语言区域
     * @return 偏好与功能列表 VO
     */
    AiFeaturePreferenceVO getPreferences(Long userId, String locale);

    /**
     * 保存用户 AI 功能偏好（含启用项与排序）。
     *
     * @param userId 当前用户 ID
     * @param featureKeys 启用的功能 key 列表
     * @param featureOrder 功能展示顺序 key 列表
     */
    void savePreferences(Long userId, List<String> featureKeys, List<String> featureOrder);
}
