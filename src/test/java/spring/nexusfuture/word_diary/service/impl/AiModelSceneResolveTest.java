package spring.nexusfuture.word_diary.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import spring.nexusfuture.word_diary.common.AiRegions;
import spring.nexusfuture.word_diary.common.SysConfigKeys;
import spring.nexusfuture.word_diary.dto.ResolvedAiEndpoint;
import spring.nexusfuture.word_diary.entity.SysAiModel;
import spring.nexusfuture.word_diary.entity.SysAiScene;
import spring.nexusfuture.word_diary.mapper.SysAiModelMapper;
import spring.nexusfuture.word_diary.mapper.SysAiSceneMapper;
import spring.nexusfuture.word_diary.mapper.WdAiCallLogMapper;
import spring.nexusfuture.word_diary.service.AiProviderService;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiModelSceneResolveTest {

    @Mock
    SysAiModelMapper modelMapper;
    @Mock
    SysAiSceneMapper sceneMapper;
    @Mock
    WdAiCallLogMapper callLogMapper;
    @Mock
    AiProviderService aiProviderService;

    @InjectMocks
    AiModelServiceImpl aiModelService;

    @Test
    void resolve_wordParse_cn_usesQwenTurboOnAliyun() {
        when(sceneMapper.selectEnabledBySceneKeyAndRegion(SysConfigKeys.AI_SCENE_WORD_PARSE, AiRegions.CN))
                .thenReturn(SysAiScene.builder()
                        .sceneKey(SysConfigKeys.AI_SCENE_WORD_PARSE)
                        .region(AiRegions.CN)
                        .modelKey("qwen-turbo")
                        .isEnabled(1)
                        .build());
        when(modelMapper.selectEnabledByModelKey("qwen-turbo"))
                .thenReturn(SysAiModel.builder()
                        .modelKey("qwen-turbo")
                        .provider("aliyun")
                        .currency("CNY")
                        .inputPricePerMillion(new BigDecimal("0.3"))
                        .outputPricePerMillion(new BigDecimal("0.6"))
                        .isEnabled(1)
                        .build());

        ResolvedAiEndpoint ep = aiModelService.requireEndpointForScene(
                SysConfigKeys.AI_SCENE_WORD_PARSE, AiRegions.CN);
        assertEquals("qwen-turbo", ep.modelKey());
        assertEquals("aliyun", ep.provider());
    }

    @Test
    void resolve_feature_global_fallsBackToCnWhenMissing() {
        when(sceneMapper.selectEnabledBySceneKeyAndRegion(SysConfigKeys.AI_SCENE_FEATURE, AiRegions.GLOBAL))
                .thenReturn(null);
        when(sceneMapper.selectEnabledBySceneKeyAndRegion(SysConfigKeys.AI_SCENE_FEATURE, AiRegions.CN))
                .thenReturn(SysAiScene.builder()
                        .sceneKey(SysConfigKeys.AI_SCENE_FEATURE)
                        .region(AiRegions.CN)
                        .modelKey("deepseek-v4-flash")
                        .isEnabled(1)
                        .build());
        when(modelMapper.selectEnabledByModelKey("deepseek-v4-flash"))
                .thenReturn(SysAiModel.builder()
                        .modelKey("deepseek-v4-flash")
                        .provider("deepseek")
                        .isEnabled(1)
                        .build());

        ResolvedAiEndpoint ep = aiModelService.requireEndpointForScene(
                SysConfigKeys.AI_SCENE_FEATURE, AiRegions.GLOBAL);
        assertEquals("deepseek-v4-flash", ep.modelKey());
        assertEquals("deepseek", ep.provider());
    }

    @Test
    void resolve_unknownScene_fallsBackToDefaultScene() {
        when(sceneMapper.selectEnabledBySceneKeyAndRegion("unknown_scene", AiRegions.CN)).thenReturn(null);
        when(sceneMapper.selectEnabledBySceneKeyAndRegion("unknown_scene", AiRegions.GLOBAL)).thenReturn(null);
        when(sceneMapper.selectEnabledBySceneKey("unknown_scene")).thenReturn(null);
        when(sceneMapper.selectEnabledBySceneKeyAndRegion(SysConfigKeys.AI_SCENE_DEFAULT, AiRegions.CN))
                .thenReturn(SysAiScene.builder()
                        .sceneKey(SysConfigKeys.AI_SCENE_DEFAULT)
                        .region(AiRegions.CN)
                        .modelKey("deepseek-v4-flash")
                        .isEnabled(1)
                        .build());
        when(modelMapper.selectEnabledByModelKey("deepseek-v4-flash"))
                .thenReturn(SysAiModel.builder()
                        .modelKey("deepseek-v4-flash")
                        .provider("deepseek")
                        .isEnabled(1)
                        .build());

        ResolvedAiEndpoint ep = aiModelService.requireEndpointForScene("unknown_scene", AiRegions.CN);
        assertEquals("deepseek-v4-flash", ep.modelKey());
        assertEquals("deepseek", ep.provider());
    }
}
