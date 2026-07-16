package spring.nexusfuture.word_diary.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import spring.nexusfuture.word_diary.common.SysConfigKeys;
import spring.nexusfuture.word_diary.dto.ResolvedAiEndpoint;
import spring.nexusfuture.word_diary.entity.SysAiModel;
import spring.nexusfuture.word_diary.mapper.SysAiModelMapper;
import spring.nexusfuture.word_diary.mapper.WdAiCallLogMapper;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiModelSceneResolveTest {

    @Mock
    SysAiModelMapper modelMapper;
    @Mock
    WdAiCallLogMapper callLogMapper;

    @InjectMocks
    AiModelServiceImpl aiModelService;

    @Test
    void resolve_wordParse_usesQwenTurboOnAliyun() {
        when(modelMapper.selectEnabledBySceneKey(SysConfigKeys.AI_SCENE_WORD_PARSE))
                .thenReturn(SysAiModel.builder()
                        .sceneKey(SysConfigKeys.AI_SCENE_WORD_PARSE)
                        .modelKey("qwen-turbo")
                        .provider("aliyun")
                        .currency("CNY")
                        .inputPricePerMillion(new BigDecimal("0.3"))
                        .outputPricePerMillion(new BigDecimal("0.6"))
                        .isEnabled(1)
                        .build());

        ResolvedAiEndpoint ep = aiModelService.requireEndpointForScene(SysConfigKeys.AI_SCENE_WORD_PARSE);
        assertEquals("qwen-turbo", ep.modelKey());
        assertEquals("aliyun", ep.provider());
    }

    @Test
    void resolve_feature_usesDeepseekFlash() {
        when(modelMapper.selectEnabledBySceneKey(SysConfigKeys.AI_SCENE_FEATURE))
                .thenReturn(SysAiModel.builder()
                        .sceneKey(SysConfigKeys.AI_SCENE_FEATURE)
                        .modelKey("deepseek-v4-flash")
                        .provider("deepseek")
                        .currency("CNY")
                        .inputPricePerMillion(new BigDecimal("1.0"))
                        .outputPricePerMillion(new BigDecimal("2.0"))
                        .isEnabled(1)
                        .build());

        ResolvedAiEndpoint ep = aiModelService.requireEndpointForScene(SysConfigKeys.AI_SCENE_FEATURE);
        assertEquals("deepseek-v4-flash", ep.modelKey());
        assertEquals("deepseek", ep.provider());
    }

    @Test
    void resolve_unknownScene_fallsBackToDefaultScene() {
        when(modelMapper.selectEnabledBySceneKey("unknown_scene")).thenReturn(null);
        when(modelMapper.selectEnabledBySceneKey(SysConfigKeys.AI_SCENE_DEFAULT))
                .thenReturn(SysAiModel.builder()
                        .sceneKey(SysConfigKeys.AI_SCENE_DEFAULT)
                        .modelKey("deepseek-v4-flash")
                        .provider("deepseek")
                        .isEnabled(1)
                        .build());

        ResolvedAiEndpoint ep = aiModelService.requireEndpointForScene("unknown_scene");
        assertEquals("deepseek-v4-flash", ep.modelKey());
        assertEquals("deepseek", ep.provider());
    }
}
