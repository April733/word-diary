package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.dto.ResolvedAiEndpoint;
import spring.nexusfuture.word_diary.entity.SysAiModel;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface AiModelService {

    List<SysAiModel> listAll();

    SysAiModel getById(Long id);

    SysAiModel create(SysAiModel model);

    SysAiModel update(SysAiModel model);

    void delete(Long id);

    /** 按 model_name 解析定价（同模型多场景取一条） */
    SysAiModel resolveByModelName(String modelName);

    /** 按场景解析：模型名 + provider */
    ResolvedAiEndpoint requireEndpointForScene(String sceneKey);

    String requireModelKeyForScene(String sceneKey);

    String requireActiveModelKey();

    /** 场景列表（即 sys_ai_model 全表） */
    List<SysAiModel> listScenes();

    /** 仅改场景绑定的模型名（并尽量带上同名模型的单价/provider） */
    SysAiModel updateSceneModel(String sceneKey, String modelKey);

    BigDecimal estimateCost(String modelName, int promptTokens, int completionTokens);

    List<Map<String, Object>> dailyCostSummary();

    int recalculateHistoricalCosts();
}
