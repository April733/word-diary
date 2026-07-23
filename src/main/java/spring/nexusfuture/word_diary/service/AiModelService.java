package spring.nexusfuture.word_diary.service;

import spring.nexusfuture.word_diary.dto.response.AiModelTestResultVO;
import spring.nexusfuture.word_diary.dto.response.AiSceneVO;
import spring.nexusfuture.word_diary.dto.ResolvedAiEndpoint;
import spring.nexusfuture.word_diary.entity.SysAiModel;
import spring.nexusfuture.word_diary.entity.SysAiScene;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** AI 模型与场景配置：CRUD、路由解析与成本估算。 */
public interface AiModelService {

    /**
     * 列出全部 AI 模型配置。
     *
     * @return 模型实体列表
     */
    List<SysAiModel> listAll();

    /**
     * 按 ID 获取模型配置。
     *
     * @param id 模型主键
     * @return 模型实体
     */
    SysAiModel getById(Long id);

    /**
     * 新建 AI 模型配置。
     *
     * @param model 待创建的模型实体
     * @return 创建后的实体
     */
    SysAiModel create(SysAiModel model);

    /**
     * 更新 AI 模型配置。
     *
     * @param model 含 ID 的模型实体
     * @return 更新后的实体
     */
    SysAiModel update(SysAiModel model);

    /**
     * 删除 AI 模型配置。
     *
     * @param id 模型主键
     */
    void delete(Long id);

    /**
     * 用指定供应商 + 模型做流式短对话，校验连通性（兼容 Qwen3 等思考模型）。
     * 失败不抛异常，结果写在 {@link AiModelTestResultVO#isOk()} / error。
     *
     * @param provider 供应商标识
     * @param modelKey 模型 key
     * @return 连通性测试结果
     */
    AiModelTestResultVO testConnection(String provider, String modelKey);

    /**
     * 按 model_name 解析定价与模型行。
     *
     * @param modelName 模型名称
     * @return 模型实体；不存在时返回 null
     */
    SysAiModel resolveByModelName(String modelName);

    /**
     * 按场景解析模型端点（默认地区 cn，兼容旧调用）。
     *
     * @param sceneKey 场景标识
     * @return 解析后的供应商、模型与 Prompt 端点
     */
    ResolvedAiEndpoint requireEndpointForScene(String sceneKey);

    /**
     * 按场景 + 路由区（cn/global）解析模型端点。
     *
     * @param sceneKey 场景标识
     * @param region 路由区 cn|global
     * @return 解析后的端点；未配置时抛业务异常
     */
    ResolvedAiEndpoint requireEndpointForScene(String sceneKey, String region);

    /**
     * 按场景获取绑定的 modelKey（默认 cn）。
     *
     * @param sceneKey 场景标识
     * @return 模型 key
     */
    String requireModelKeyForScene(String sceneKey);

    /**
     * 按场景 + 路由区获取绑定的 modelKey。
     *
     * @param sceneKey 场景标识
     * @param region 路由区 cn|global
     * @return 模型 key
     */
    String requireModelKeyForScene(String sceneKey, String region);

    /**
     * 获取系统当前默认启用的 modelKey。
     *
     * @return 默认模型 key
     */
    String requireActiveModelKey();

    /**
     * 列出全部 AI 场景配置（含绑定模型摘要）。
     *
     * @return 场景 VO 列表
     */
    List<AiSceneVO> listScenes();

    /**
     * 新建 AI 场景配置。
     *
     * @param scene 待创建的场景实体
     * @return 创建后的实体
     */
    SysAiScene createScene(SysAiScene scene);

    /**
     * 更新 AI 场景配置。
     *
     * @param scene 含 ID 的场景实体
     * @return 更新后的实体
     */
    SysAiScene updateScene(SysAiScene scene);

    /**
     * 仅修改某条场景绑定的模型（按 id）。
     *
     * @param id 场景主键
     * @param modelKey 新模型 key
     * @return 更新后的场景 VO
     */
    AiSceneVO updateSceneModelById(Long id, String modelKey);

    /**
     * 删除 AI 场景配置。
     *
     * @param id 场景主键
     */
    void deleteScene(Long id);

    /**
     * 按模型定价估算单次调用费用。
     *
     * @param modelName 模型名称
     * @param promptTokens 输入 token 数
     * @param completionTokens 输出 token 数
     * @return 估算费用（元）
     */
    BigDecimal estimateCost(String modelName, int promptTokens, int completionTokens);

    /**
     * 按日汇总历史 AI 调用成本。
     *
     * @return 每日成本 Map 列表（含 day、cost 等字段）
     */
    List<Map<String, Object>> dailyCostSummary();

    /**
     * 按当前模型定价重算历史调用日志中的费用字段。
     *
     * @return 更新的日志条数
     */
    int recalculateHistoricalCosts();
}
