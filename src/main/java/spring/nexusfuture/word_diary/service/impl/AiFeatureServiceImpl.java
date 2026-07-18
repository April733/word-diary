package spring.nexusfuture.word_diary.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import spring.nexusfuture.word_diary.common.AiFeatureI18nSupport;
import spring.nexusfuture.word_diary.dto.AiFeaturePreferenceVO;
import spring.nexusfuture.word_diary.entity.SysAiFeature;
import spring.nexusfuture.word_diary.entity.WdUserAiPreference;
import spring.nexusfuture.word_diary.mapper.SysAiFeatureMapper;
import spring.nexusfuture.word_diary.mapper.WdUserAiPreferenceMapper;
import spring.nexusfuture.word_diary.service.AiFeatureService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiFeatureServiceImpl implements AiFeatureService {

    private final SysAiFeatureMapper featureMapper;
    private final WdUserAiPreferenceMapper preferenceMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<SysAiFeature> listActiveFeatures(String locale) {
        List<SysAiFeature> list = featureMapper.selectActiveList();
        AiFeatureI18nSupport.resolveAllInPlace(list, locale);
        return list;
    }

    @Override
    public AiFeaturePreferenceVO getPreferences(Long userId, String locale) {
        List<SysAiFeature> allFeatures = featureMapper.selectActiveList();
        List<String> preferredKeys = new ArrayList<>();
        List<String> featureOrder = new ArrayList<>();

        WdUserAiPreference pref = preferenceMapper.selectByUserId(userId);
        if (pref != null && pref.getFeatureKeys() != null && !pref.getFeatureKeys().isEmpty()) {
            try {
                preferredKeys = objectMapper.readValue(pref.getFeatureKeys(), new TypeReference<List<String>>() {});
            } catch (JsonProcessingException e) {
                // JSON 解析失败，返回空列表
            }
        }
        if (pref != null && pref.getFeatureOrder() != null && !pref.getFeatureOrder().isEmpty()) {
            try {
                featureOrder = objectMapper.readValue(pref.getFeatureOrder(), new TypeReference<List<String>>() {});
            } catch (JsonProcessingException e) {
                // JSON 解析失败，返回空列表
            }
        }

        // 如果用户没有偏好记录，默认全部启用
        if (preferredKeys.isEmpty()) {
            preferredKeys = allFeatures.stream().map(SysAiFeature::getFeatureKey).toList();
        }
        // 如果没有排序记录，默认按数据库sort_num排序
        if (featureOrder.isEmpty()) {
            featureOrder = allFeatures.stream()
                    .sorted(Comparator.comparingInt(SysAiFeature::getSortNum))
                    .map(SysAiFeature::getFeatureKey)
                    .toList();
        }

        // 按用户自定义顺序重新排列 allFeatures
        List<SysAiFeature> orderedFeatures = new ArrayList<>();
        Map<String, SysAiFeature> featureMap = allFeatures.stream()
                .collect(Collectors.toMap(SysAiFeature::getFeatureKey, f -> f));
        for (String key : featureOrder) {
            SysAiFeature f = featureMap.get(key);
            if (f != null) {
                orderedFeatures.add(f);
            }
        }
        // 补充未在排序中的功能（新添加的）
        for (SysAiFeature f : allFeatures) {
            if (!featureOrder.contains(f.getFeatureKey())) {
                orderedFeatures.add(f);
            }
        }

        AiFeatureI18nSupport.resolveAllInPlace(orderedFeatures, locale);

        return AiFeaturePreferenceVO.builder()
                .allFeatures(orderedFeatures)
                .preferredKeys(preferredKeys)
                .featureOrder(featureOrder)
                .build();
    }

    @Override
    public void savePreferences(Long userId, List<String> featureKeys, List<String> featureOrder) {
        try {
            String keysJson = objectMapper.writeValueAsString(featureKeys != null ? featureKeys : Collections.emptyList());
            String orderJson = objectMapper.writeValueAsString(featureOrder != null ? featureOrder : Collections.emptyList());
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
