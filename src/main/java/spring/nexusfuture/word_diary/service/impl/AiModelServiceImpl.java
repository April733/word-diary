package spring.nexusfuture.word_diary.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import spring.nexusfuture.word_diary.common.AiRegions;
import spring.nexusfuture.word_diary.common.BizException;
import spring.nexusfuture.word_diary.common.SysConfigKeys;
import spring.nexusfuture.word_diary.config.AiChatClientRouter;
import spring.nexusfuture.word_diary.dto.AiModelTestResultVO;
import spring.nexusfuture.word_diary.dto.AiSceneVO;
import spring.nexusfuture.word_diary.dto.ResolvedAiEndpoint;
import spring.nexusfuture.word_diary.entity.SysAiModel;
import spring.nexusfuture.word_diary.entity.SysAiScene;
import spring.nexusfuture.word_diary.mapper.SysAiModelMapper;
import spring.nexusfuture.word_diary.mapper.SysAiSceneMapper;
import spring.nexusfuture.word_diary.mapper.WdAiCallLogMapper;
import spring.nexusfuture.word_diary.service.AiModelService;
import spring.nexusfuture.word_diary.service.AiProviderService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AiModelServiceImpl implements AiModelService {

    private static final BigDecimal MILLION = new BigDecimal("1000000");
    private static final String TEST_PROMPT = "你好，你是谁？";

    private final SysAiModelMapper modelMapper;
    private final SysAiSceneMapper sceneMapper;
    private final WdAiCallLogMapper callLogMapper;
    private final AiProviderService aiProviderService;
    private final AiChatClientRouter chatClientRouter;

    public AiModelServiceImpl(SysAiModelMapper modelMapper,
                              SysAiSceneMapper sceneMapper,
                              WdAiCallLogMapper callLogMapper,
                              AiProviderService aiProviderService,
                              @Lazy AiChatClientRouter chatClientRouter) {
        this.modelMapper = modelMapper;
        this.sceneMapper = sceneMapper;
        this.callLogMapper = callLogMapper;
        this.aiProviderService = aiProviderService;
        this.chatClientRouter = chatClientRouter;
    }

    @Override
    public List<SysAiModel> listAll() {
        return modelMapper.selectAll();
    }

    @Override
    public SysAiModel getById(Long id) {
        SysAiModel model = modelMapper.selectById(id);
        if (model == null) {
            throw new BizException("模型不存在");
        }
        return model;
    }

    @Override
    @Transactional
    public SysAiModel create(SysAiModel model) {
        normalizeModel(model);
        if (modelMapper.selectByModelKey(model.getModelKey()) != null) {
            throw new BizException("模型已存在: " + model.getModelKey());
        }
        modelMapper.insert(model);
        return model;
    }

    @Override
    @Transactional
    public SysAiModel update(SysAiModel model) {
        if (model.getId() == null) {
            throw new BizException("缺少ID");
        }
        SysAiModel existing = getById(model.getId());
        normalizeModel(model);
        SysAiModel sameKey = modelMapper.selectByModelKey(model.getModelKey());
        if (sameKey != null && !sameKey.getId().equals(model.getId())) {
            throw new BizException("模型标识已被占用: " + model.getModelKey());
        }
        String oldKey = existing.getModelKey();
        modelMapper.update(model);
        // model_key 变更时，同步场景绑定，避免绑到旧 key
        if (oldKey != null && !oldKey.equals(model.getModelKey())) {
            sceneMapper.updateModelKeyByOldKey(oldKey, model.getModelKey());
        }
        return modelMapper.selectById(model.getId());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (id == null) {
            throw new BizException("缺少模型 ID");
        }
        SysAiModel model = modelMapper.selectById(id);
        if (model == null) {
            throw new BizException("模型不存在或已删除（id=" + id + "），请刷新页面后重试");
        }
        String modelKey = model.getModelKey();
        // 同 model_key 若还有其它行，允许删掉重复行（清理历史脏数据）
        boolean hasSibling = modelMapper.selectAll().stream()
                .anyMatch(m -> modelKey != null
                        && modelKey.equals(m.getModelKey())
                        && m.getId() != null
                        && !m.getId().equals(id));
        if (!hasSibling) {
            int used = sceneMapper.countByModelKey(modelKey);
            if (used > 0) {
                throw new BizException("仍有 " + used + " 个场景绑定「" + modelKey
                        + "」，请先在下方「场景绑定」改绑到其它模型，再删除");
            }
        }
        modelMapper.softDelete(id);
    }

    @Override
    public AiModelTestResultVO testConnection(String provider, String modelKey) {
        String providerKey = provider != null ? provider.trim().toLowerCase() : "";
        String key = modelKey != null ? modelKey.trim() : "";
        long start = System.currentTimeMillis();
        AiModelTestResultVO.AiModelTestResultVOBuilder builder = AiModelTestResultVO.builder()
                .provider(providerKey)
                .modelKey(key)
                .prompt(TEST_PROMPT);

        if (providerKey.isBlank()) {
            return builder.ok(false).error("provider 不能为空").latencyMs(0L).build();
        }
        if (key.isBlank()) {
            return builder.ok(false).error("modelKey 不能为空").latencyMs(0L).build();
        }

        try {
            aiProviderService.requireEnabled(providerKey);
            ChatClient client = chatClientRouter.require(providerKey);
            // 走流式：兼容通义 Qwen3 等「非流式必须 enable_thinking=false」的模型
            List<String> chunks = client.prompt()
                    .options(OpenAiChatOptions.builder()
                            .model(key)
                            .streamUsage(false)
                            .temperature(0.3)
                            .maxTokens(256)
                            .build())
                    .user(TEST_PROMPT)
                    .stream()
                    .content()
                    .filter(text -> text != null && !text.isEmpty())
                    .collectList()
                    .block(Duration.ofSeconds(90));

            String reply = chunks == null || chunks.isEmpty() ? "" : String.join("", chunks);
            if (reply.isBlank()) {
                return builder
                        .ok(false)
                        .error("流式测试完成但未收到文本内容（部分思考模型可能只返回 reasoning）")
                        .latencyMs(System.currentTimeMillis() - start)
                        .build();
            }
            return builder
                    .ok(true)
                    .reply(reply.trim())
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            log.warn("模型连通性测试失败 provider={} modelKey={}: {}", providerKey, key, e.toString());
            return builder
                    .ok(false)
                    .error(resolveTestErrorMessage(e))
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        }
    }

    private static String resolveTestErrorMessage(Throwable e) {
        Throwable cur = e;
        String best = null;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null && !msg.isBlank()) {
                best = msg.trim();
            }
            if (cur instanceof BizException) {
                return best;
            }
            cur = cur.getCause();
        }
        if (best != null) {
            return best.length() > 800 ? best.substring(0, 800) + "…" : best;
        }
        return e.getClass().getSimpleName();
    }

    @Override
    public SysAiModel resolveByModelName(String modelName) {
        if (modelName != null && !modelName.isBlank()) {
            SysAiModel hit = modelMapper.selectEnabledByModelKey(modelName.trim());
            if (hit != null) {
                return hit;
            }
            hit = modelMapper.selectByModelKey(modelName.trim());
            if (hit != null) {
                return hit;
            }
        }
        return resolveDefaultModel();
    }

    @Override
    public ResolvedAiEndpoint requireEndpointForScene(String sceneKey) {
        return requireEndpointForScene(sceneKey, AiRegions.CN);
    }

    @Override
    public ResolvedAiEndpoint requireEndpointForScene(String sceneKey, String region) {
        String key = (sceneKey == null || sceneKey.isBlank())
                ? SysConfigKeys.AI_SCENE_DEFAULT : sceneKey.trim();
        String routingRegion = AiRegions.normalizeRoutingRegion(region);

        SysAiScene scene = findScene(key, routingRegion);
        if (scene == null && !SysConfigKeys.AI_SCENE_DEFAULT.equals(key)) {
            scene = findScene(SysConfigKeys.AI_SCENE_DEFAULT, routingRegion);
        }
        if (scene == null) {
            throw new BizException("未配置 AI 场景: " + key + " / " + routingRegion + "（请在「场景绑定」维护）");
        }
        if (scene.getModelKey() == null || scene.getModelKey().isBlank()) {
            throw new BizException("场景 " + key + "（" + routingRegion + "）未绑定模型");
        }
        SysAiModel model = modelMapper.selectEnabledByModelKey(scene.getModelKey().trim());
        if (model == null) {
            model = modelMapper.selectByModelKey(scene.getModelKey().trim());
        }
        if (model == null) {
            throw new BizException("场景 " + key + " 绑定的模型不存在: " + scene.getModelKey());
        }
        if (model.getIsEnabled() != null && model.getIsEnabled() == 0) {
            throw new BizException("场景 " + key + " 绑定的模型已停用: " + model.getModelKey());
        }
        String provider = model.getProvider() != null ? model.getProvider().trim() : "";
        if (provider.isBlank()) {
            throw new BizException("模型 " + model.getModelKey() + " 未配置 provider（如 deepseek / aliyun / modelscope）");
        }
        return new ResolvedAiEndpoint(model.getModelKey().trim(), provider.toLowerCase());
    }

    /** 精确地区 → 对侧地区 → 任意启用行 */
    private SysAiScene findScene(String sceneKey, String region) {
        SysAiScene scene = sceneMapper.selectEnabledBySceneKeyAndRegion(sceneKey, region);
        if (scene != null) {
            return scene;
        }
        String other = AiRegions.CN.equals(region) ? AiRegions.GLOBAL : AiRegions.CN;
        scene = sceneMapper.selectEnabledBySceneKeyAndRegion(sceneKey, other);
        if (scene != null) {
            return scene;
        }
        return sceneMapper.selectEnabledBySceneKey(sceneKey);
    }

    @Override
    public String requireModelKeyForScene(String sceneKey) {
        return requireEndpointForScene(sceneKey).modelKey();
    }

    @Override
    public String requireModelKeyForScene(String sceneKey, String region) {
        return requireEndpointForScene(sceneKey, region).modelKey();
    }

    @Override
    public String requireActiveModelKey() {
        SysAiModel def = resolveDefaultModel();
        if (def == null || def.getModelKey() == null || def.getModelKey().isBlank()) {
            throw new BizException("未配置 default 场景模型，请在「场景绑定」维护");
        }
        return def.getModelKey().trim();
    }

    @Override
    public List<AiSceneVO> listScenes() {
        return sceneMapper.selectAllWithModel();
    }

    @Override
    @Transactional
    public SysAiScene createScene(SysAiScene scene) {
        normalizeScene(scene);
        if (sceneMapper.selectBySceneKeyAndRegion(scene.getSceneKey(), scene.getRegion()) != null) {
            throw new BizException("场景已存在: " + scene.getSceneKey() + " / " + scene.getRegion());
        }
        ensureModelExists(scene.getModelKey());
        sceneMapper.insert(scene);
        return scene;
    }

    @Override
    @Transactional
    public SysAiScene updateScene(SysAiScene scene) {
        if (scene.getId() == null) {
            throw new BizException("缺少ID");
        }
        SysAiScene existing = sceneMapper.selectById(scene.getId());
        if (existing == null) {
            throw new BizException("场景不存在");
        }
        if (scene.getSceneKey() == null || scene.getSceneKey().isBlank()) {
            scene.setSceneKey(existing.getSceneKey());
        }
        if (scene.getRegion() == null || scene.getRegion().isBlank()) {
            scene.setRegion(existing.getRegion());
        }
        if (scene.getSceneName() == null || scene.getSceneName().isBlank()) {
            scene.setSceneName(existing.getSceneName());
        }
        if (scene.getModelKey() == null || scene.getModelKey().isBlank()) {
            scene.setModelKey(existing.getModelKey());
        }
        if (scene.getIsEnabled() == null) {
            scene.setIsEnabled(existing.getIsEnabled());
        }
        if (scene.getSortNum() == null) {
            scene.setSortNum(existing.getSortNum());
        }
        normalizeScene(scene);
        SysAiScene same = sceneMapper.selectBySceneKeyAndRegion(scene.getSceneKey(), scene.getRegion());
        if (same != null && !same.getId().equals(scene.getId())) {
            throw new BizException("场景已存在: " + scene.getSceneKey() + " / " + scene.getRegion());
        }
        ensureModelExists(scene.getModelKey());
        sceneMapper.update(scene);
        return sceneMapper.selectById(scene.getId());
    }

    @Override
    @Transactional
    public AiSceneVO updateSceneModelById(Long id, String modelKey) {
        if (id == null) {
            throw new BizException("缺少场景 ID");
        }
        if (modelKey == null || modelKey.isBlank()) {
            throw new BizException("modelKey 不能为空");
        }
        SysAiScene existing = sceneMapper.selectById(id);
        if (existing == null) {
            throw new BizException("场景不存在");
        }
        ensureModelExists(modelKey.trim());
        sceneMapper.updateModelKeyById(id, modelKey.trim());
        return sceneMapper.selectAllWithModel().stream()
                .filter(v -> id.equals(v.getId()))
                .findFirst()
                .orElseThrow(() -> new BizException("场景更新后读取失败"));
    }

    @Override
    @Transactional
    public void deleteScene(Long id) {
        if (sceneMapper.selectById(id) == null) {
            throw new BizException("场景不存在");
        }
        sceneMapper.softDelete(id);
    }

    @Override
    public BigDecimal estimateCost(String modelName, int promptTokens, int completionTokens) {
        SysAiModel model = resolveByModelName(modelName);
        if (model == null) {
            return BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        }
        BigDecimal inPrice = model.getInputPricePerMillion() != null
                ? model.getInputPricePerMillion() : BigDecimal.ZERO;
        BigDecimal outPrice = model.getOutputPricePerMillion() != null
                ? model.getOutputPricePerMillion() : BigDecimal.ZERO;

        BigDecimal inputCost = BigDecimal.valueOf(Math.max(promptTokens, 0))
                .multiply(inPrice)
                .divide(MILLION, 10, RoundingMode.HALF_UP);
        BigDecimal outputCost = BigDecimal.valueOf(Math.max(completionTokens, 0))
                .multiply(outPrice)
                .divide(MILLION, 10, RoundingMode.HALF_UP);
        return inputCost.add(outputCost).setScale(8, RoundingMode.HALF_UP);
    }

    @Override
    public List<Map<String, Object>> dailyCostSummary() {
        return callLogMapper.selectDailyCostSummary();
    }

    @Override
    public int recalculateHistoricalCosts() {
        return callLogMapper.recalculateEstimatedCosts();
    }

    private SysAiModel resolveDefaultModel() {
        SysAiScene def = findScene(SysConfigKeys.AI_SCENE_DEFAULT, AiRegions.CN);
        if (def == null || def.getModelKey() == null || def.getModelKey().isBlank()) {
            return null;
        }
        SysAiModel model = modelMapper.selectEnabledByModelKey(def.getModelKey().trim());
        return model != null ? model : modelMapper.selectByModelKey(def.getModelKey().trim());
    }

    private void ensureModelExists(String modelKey) {
        if (modelMapper.selectByModelKey(modelKey) == null) {
            throw new BizException("模型不存在，请先在「模型目录」新增: " + modelKey);
        }
    }

    private void normalizeModel(SysAiModel model) {
        if (model.getModelKey() == null || model.getModelKey().isBlank()) {
            throw new BizException("modelKey 不能为空");
        }
        model.setModelKey(model.getModelKey().trim());
        if (model.getDisplayName() == null || model.getDisplayName().isBlank()) {
            model.setDisplayName(model.getModelKey());
        }
        if (model.getProvider() == null || model.getProvider().isBlank()) {
            throw new BizException("provider 不能为空");
        }
        model.setProvider(model.getProvider().trim().toLowerCase());
        if (!aiProviderService.existsEnabled(model.getProvider())) {
            throw new BizException("供应商不存在或已停用: " + model.getProvider()
                    + "（请先在「供应商」中新增并启用）");
        }
        if (model.getCurrency() == null || model.getCurrency().isBlank()) {
            model.setCurrency("CNY");
        }
        if (model.getInputPricePerMillion() == null) {
            model.setInputPricePerMillion(BigDecimal.ZERO);
        }
        if (model.getOutputPricePerMillion() == null) {
            model.setOutputPricePerMillion(BigDecimal.ZERO);
        }
        if (model.getSortNum() == null) {
            model.setSortNum(0);
        }
        if (model.getIsEnabled() == null) {
            model.setIsEnabled(1);
        }
        if (model.getRemark() == null) {
            model.setRemark("");
        }
    }

    private void normalizeScene(SysAiScene scene) {
        if (scene.getSceneKey() == null || scene.getSceneKey().isBlank()) {
            throw new BizException("sceneKey 不能为空");
        }
        scene.setSceneKey(scene.getSceneKey().trim());
        if (scene.getSceneName() == null || scene.getSceneName().isBlank()) {
            scene.setSceneName(scene.getSceneKey());
        }
        scene.setRegion(AiRegions.normalizeRoutingRegion(scene.getRegion()));
        if (scene.getModelKey() == null || scene.getModelKey().isBlank()) {
            throw new BizException("modelKey 不能为空");
        }
        scene.setModelKey(scene.getModelKey().trim());
        if (scene.getSortNum() == null) {
            scene.setSortNum(0);
        }
        if (scene.getIsEnabled() == null) {
            scene.setIsEnabled(1);
        }
        if (scene.getRemark() == null) {
            scene.setRemark("");
        }
    }
}
