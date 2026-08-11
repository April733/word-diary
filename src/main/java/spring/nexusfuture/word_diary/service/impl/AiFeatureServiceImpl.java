package spring.nexusfuture.word_diary.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import spring.nexusfuture.word_diary.common.AiFeatureKeys;
import spring.nexusfuture.word_diary.common.AiFeaturePromptEnum;
import spring.nexusfuture.word_diary.common.AiFeaturePromptOralEnum;
import spring.nexusfuture.word_diary.dto.response.AiFeaturePreferenceVO;
import spring.nexusfuture.word_diary.entity.WdUserAiPreference;
import spring.nexusfuture.word_diary.mapper.WdUserAiPreferenceMapper;
import spring.nexusfuture.word_diary.service.AiFeatureService;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiFeatureServiceImpl implements AiFeatureService {

    private final WdUserAiPreferenceMapper preferenceMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<AiFeaturePreferenceVO.FeatureCatalogItem> getCatalog() {
        return Arrays.stream(AiFeaturePromptEnum.values())
                .map(e -> AiFeaturePreferenceVO.FeatureCatalogItem.builder()
                        .key(e.getKey())
                        .label(e.getLabel())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<AiFeaturePreferenceVO.FeatureCatalogItem> getOralCatalog() {
        return Arrays.stream(AiFeaturePromptOralEnum.values())
                .map(e -> AiFeaturePreferenceVO.FeatureCatalogItem.builder()
                        .key(e.getKey())
                        .label(e.getLabel())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public AiFeaturePreferenceVO getPreferences(Long userId, String locale) {
        return getPreferencesInternal(userId, locale, false);
    }

    @Override
    public AiFeaturePreferenceVO getOralPreferences(Long userId, String locale) {
        return getPreferencesInternal(userId, locale, true);
    }

    private AiFeaturePreferenceVO getPreferencesInternal(Long userId, String locale, boolean oral) {
        List<String> preferredKeys = List.of();
        List<String> featureOrder = List.of();

        WdUserAiPreference pref = preferenceMapper.selectByUserId(userId);
        if (pref != null && pref.getFeatureKeys() != null && !pref.getFeatureKeys().isEmpty()) {
            try {
                preferredKeys = objectMapper.readValue(pref.getFeatureKeys(), new TypeReference<>() {});
            } catch (JsonProcessingException ignored) {
                // keep empty → normalize to defaults
            }
        }
        if (pref != null && pref.getFeatureOrder() != null && !pref.getFeatureOrder().isEmpty()) {
            try {
                featureOrder = objectMapper.readValue(pref.getFeatureOrder(), new TypeReference<>() {});
            } catch (JsonProcessingException ignored) {
                // keep empty → normalize to defaults
            }
        }

        AiFeatureKeys.Normalized normalized = AiFeatureKeys.normalize(preferredKeys, featureOrder, oral);
        return AiFeaturePreferenceVO.builder()
                .catalog(oral ? getOralCatalog() : getCatalog())
                .preferredKeys(normalized.preferredKeys())
                .featureOrder(normalized.featureOrder())
                .build();
    }

    @Override
    public void savePreferences(Long userId, List<String> featureKeys, List<String> featureOrder) {
        savePreferencesInternal(userId, featureKeys, featureOrder, false);
    }

    @Override
    public void saveOralPreferences(Long userId, List<String> featureKeys, List<String> featureOrder) {
        savePreferencesInternal(userId, featureKeys, featureOrder, true);
    }

    private void savePreferencesInternal(Long userId, List<String> featureKeys, List<String> featureOrder, boolean oral) {
        AiFeatureKeys.Normalized normalized = AiFeatureKeys.normalize(featureKeys, featureOrder, oral);
        try {
            String keysJson = objectMapper.writeValueAsString(normalized.preferredKeys());
            String orderJson = objectMapper.writeValueAsString(normalized.featureOrder());
            WdUserAiPreference existing = preferenceMapper.selectByUserId(userId);
            if (existing != null) {
                preferenceMapper.updateByUserId(userId, keysJson, orderJson);
            } else {
                preferenceMapper.insert(WdUserAiPreference.builder()
                        .userId(userId)
                        .featureKeys(keysJson)
                        .featureOrder(orderJson)
                        .build());
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("保存偏好失败", e);
        }
    }
}
