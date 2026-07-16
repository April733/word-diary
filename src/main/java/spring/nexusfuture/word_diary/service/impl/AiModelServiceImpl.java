package spring.nexusfuture.word_diary.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import spring.nexusfuture.word_diary.common.BizException;
import spring.nexusfuture.word_diary.common.SysConfigKeys;
import spring.nexusfuture.word_diary.dto.ResolvedAiEndpoint;
import spring.nexusfuture.word_diary.entity.SysAiModel;
import spring.nexusfuture.word_diary.mapper.SysAiModelMapper;
import spring.nexusfuture.word_diary.mapper.WdAiCallLogMapper;
import spring.nexusfuture.word_diary.service.AiModelService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
public class AiModelServiceImpl implements AiModelService {

    private static final BigDecimal MILLION = new BigDecimal("1000000");

    private final SysAiModelMapper modelMapper;
    private final WdAiCallLogMapper callLogMapper;

    public AiModelServiceImpl(SysAiModelMapper modelMapper, WdAiCallLogMapper callLogMapper) {
        this.modelMapper = modelMapper;
        this.callLogMapper = callLogMapper;
    }

    @Override
    public List<SysAiModel> listAll() {
        return modelMapper.selectAll();
    }

    @Override
    public SysAiModel getById(Long id) {
        SysAiModel model = modelMapper.selectById(id);
        if (model == null) {
            throw new BizException("配置不存在");
        }
        return model;
    }

    @Override
    @Transactional
    public SysAiModel create(SysAiModel model) {
        normalize(model);
        if (modelMapper.selectBySceneKey(model.getSceneKey()) != null) {
            throw new BizException("场景已存在: " + model.getSceneKey());
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
        getById(model.getId());
        normalize(model);
        SysAiModel sameScene = modelMapper.selectBySceneKey(model.getSceneKey());
        if (sameScene != null && !sameScene.getId().equals(model.getId())) {
            throw new BizException("场景标识已被占用: " + model.getSceneKey());
        }
        modelMapper.update(model);
        return modelMapper.selectById(model.getId());
    }

    @Override
    public void delete(Long id) {
        getById(id);
        modelMapper.softDelete(id);
    }

    @Override
    public SysAiModel resolveByModelName(String modelName) {
        if (modelName != null && !modelName.isBlank()) {
            SysAiModel hit = modelMapper.selectByModelKey(modelName.trim());
            if (hit != null && (hit.getIsEnabled() == null || hit.getIsEnabled() == 1)) {
                return hit;
            }
        }
        return modelMapper.selectDefault();
    }

    @Override
    public ResolvedAiEndpoint requireEndpointForScene(String sceneKey) {
        String key = (sceneKey == null || sceneKey.isBlank())
                ? SysConfigKeys.AI_SCENE_DEFAULT : sceneKey.trim();

        SysAiModel row = modelMapper.selectEnabledBySceneKey(key);
        if (row == null && !SysConfigKeys.AI_SCENE_DEFAULT.equals(key)) {
            row = modelMapper.selectEnabledBySceneKey(SysConfigKeys.AI_SCENE_DEFAULT);
        }
        if (row == null) {
            throw new BizException("未配置 AI 场景: " + key + "（请在 sys_ai_model 维护）");
        }
        if (row.getModelKey() == null || row.getModelKey().isBlank()) {
            throw new BizException("场景 " + key + " 未配置模型名称");
        }
        String provider = row.getProvider() != null ? row.getProvider().trim() : "";
        if (provider.isBlank()) {
            throw new BizException("场景 " + key + " 未配置 provider（如 deepseek / aliyun）");
        }
        return new ResolvedAiEndpoint(row.getModelKey().trim(), provider.toLowerCase());
    }

    @Override
    public String requireModelKeyForScene(String sceneKey) {
        return requireEndpointForScene(sceneKey).modelKey();
    }

    @Override
    public String requireActiveModelKey() {
        SysAiModel def = modelMapper.selectDefault();
        if (def == null || def.getModelKey() == null || def.getModelKey().isBlank()) {
            throw new BizException("未配置 default 场景模型，请在 sys_ai_model 维护");
        }
        return def.getModelKey().trim();
    }

    @Override
    public List<SysAiModel> listScenes() {
        return modelMapper.selectAll();
    }

    @Override
    @Transactional
    public SysAiModel updateSceneModel(String sceneKey, String modelKey) {
        if (sceneKey == null || sceneKey.isBlank()) {
            throw new BizException("sceneKey 不能为空");
        }
        if (modelKey == null || modelKey.isBlank()) {
            throw new BizException("modelKey 不能为空");
        }
        SysAiModel existing = modelMapper.selectBySceneKey(sceneKey.trim());
        if (existing == null) {
            throw new BizException("场景不存在: " + sceneKey);
        }
        // 从已有同名模型行复制 provider/币种/单价
        SysAiModel priceRef = modelMapper.selectByModelKey(modelKey.trim());
        existing.setModelKey(modelKey.trim());
        existing.setDisplayName(priceRef != null && priceRef.getDisplayName() != null
                ? priceRef.getDisplayName() : modelKey.trim());
        if (priceRef != null) {
            existing.setProvider(priceRef.getProvider());
            existing.setCurrency(priceRef.getCurrency());
            existing.setInputPricePerMillion(priceRef.getInputPricePerMillion());
            existing.setOutputPricePerMillion(priceRef.getOutputPricePerMillion());
        }
        modelMapper.updateModelBySceneKey(existing);
        return modelMapper.selectBySceneKey(sceneKey.trim());
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

    private void normalize(SysAiModel model) {
        if (model.getSceneKey() == null || model.getSceneKey().isBlank()) {
            throw new BizException("sceneKey 不能为空");
        }
        model.setSceneKey(model.getSceneKey().trim());
        if (model.getSceneName() == null || model.getSceneName().isBlank()) {
            model.setSceneName(model.getSceneKey());
        }
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
}
