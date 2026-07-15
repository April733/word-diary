package spring.nexusfuture.word_diary.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import spring.nexusfuture.word_diary.entity.SysAiFeature;

import java.util.List;

/**
 * AI 功能偏好响应：全量功能列表 + 用户当前偏好的 featureKey 列表 + 用户自定义排序
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiFeaturePreferenceVO {

    /** 全量启用的功能列表 */
    private List<SysAiFeature> allFeatures;

    /** 用户当前偏好的 feature_key 列表，如 ["word","collocation"] */
    private List<String> preferredKeys;

    /** 用户自定义功能排序，如 ["word","example","collocation"] */
    private List<String> featureOrder;
}
