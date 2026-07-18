package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.dto.AiModelTestResultVO;
import spring.nexusfuture.word_diary.dto.AiSceneVO;
import spring.nexusfuture.word_diary.dto.ResolvedAiEndpoint;
import spring.nexusfuture.word_diary.entity.SysAiModel;
import spring.nexusfuture.word_diary.entity.SysAiScene;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface AiModelService {

    List<SysAiModel> listAll();

    SysAiModel getById(Long id);

    SysAiModel create(SysAiModel model);

    SysAiModel update(SysAiModel model);

    void delete(Long id);

    /**
     * 用指定供应商 + 模型做流式短对话，校验连通性（兼容 Qwen3 等思考模型）。
     * 失败不抛异常，结果写在 {@link AiModelTestResultVO#isOk()} / error。
     */
    AiModelTestResultVO testConnection(String provider, String modelKey);

    /** 按 model_name 解析定价 */
    SysAiModel resolveByModelName(String modelName);

    /** 按场景解析（默认地区 cn，兼容旧调用） */
    ResolvedAiEndpoint requireEndpointForScene(String sceneKey);

    /** 按场景 + 路由区（cn/global）解析模型 */
    ResolvedAiEndpoint requireEndpointForScene(String sceneKey, String region);

    String requireModelKeyForScene(String sceneKey);

    String requireModelKeyForScene(String sceneKey, String region);

    String requireActiveModelKey();

    List<AiSceneVO> listScenes();

    SysAiScene createScene(SysAiScene scene);

    SysAiScene updateScene(SysAiScene scene);

    /** 仅改某条场景绑定的模型（按 id） */
    AiSceneVO updateSceneModelById(Long id, String modelKey);

    void deleteScene(Long id);

    BigDecimal estimateCost(String modelName, int promptTokens, int completionTokens);

    List<Map<String, Object>> dailyCostSummary();

    int recalculateHistoricalCosts();
}
