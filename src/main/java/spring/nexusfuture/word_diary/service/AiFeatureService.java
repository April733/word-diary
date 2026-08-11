package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.dto.response.AiFeaturePreferenceVO;

import java.util.List;

/** 客户端 AI 功能偏好：读写 wd_user_ai_preference（白名单校验）。 */
public interface AiFeatureService {

    /**
     * 获取阅读功能目录（key + 标签）；由 AiFeaturePromptEnum 驱动。
     */
    List<AiFeaturePreferenceVO.FeatureCatalogItem> getCatalog();

    /**
     * 获取跟读功能目录（key + 标签）；由 AiFeaturePromptOralEnum 驱动。
     */
    List<AiFeaturePreferenceVO.FeatureCatalogItem> getOralCatalog();

    /**
     * 获取用户偏好（catalog + preferredKeys / featureOrder）；非法 key 已过滤，缺省=全部启用。
     *
     * @param userId 当前用户 ID
     * @param locale 语言区域（保留参数，前端自带目录文案）
     * @return 偏好 VO
     */
    AiFeaturePreferenceVO getPreferences(Long userId, String locale);

    /**
     * 获取跟读用户偏好（catalog + preferredKeys / featureOrder）；非法 key 已过滤，缺省=全部启用。
     *
     * @param userId 当前用户 ID
     * @param locale 语言区域（保留参数，前端自带目录文案）
     * @return 偏好 VO
     */
    AiFeaturePreferenceVO getOralPreferences(Long userId, String locale);

    /**
     * 保存用户 AI 功能偏好（含启用项与排序）；仅写入白名单内 key。
     *
     * @param userId 当前用户 ID
     * @param featureKeys 启用的功能 key 列表
     * @param featureOrder 功能展示顺序 key 列表
     */
    void savePreferences(Long userId, List<String> featureKeys, List<String> featureOrder);

    /**
     * 保存跟读用户 AI 功能偏好（含启用项与排序）；仅写入白名单内 key。
     *
     * @param userId 当前用户 ID
     * @param featureKeys 启用的功能 key 列表
     * @param featureOrder 功能展示顺序 key 列表
     */
    void saveOralPreferences(Long userId, List<String> featureKeys, List<String> featureOrder);
}
