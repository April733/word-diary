package spring.nexusfuture.word_diary.controller;

import org.springframework.web.bind.annotation.*;
import spring.nexusfuture.word_diary.common.R;
import spring.nexusfuture.word_diary.dto.AiModelTestReq;
import spring.nexusfuture.word_diary.dto.AiModelTestResultVO;
import spring.nexusfuture.word_diary.dto.AiProviderVO;
import spring.nexusfuture.word_diary.dto.AiSceneVO;
import spring.nexusfuture.word_diary.entity.SysAiModel;
import spring.nexusfuture.word_diary.entity.SysAiProvider;
import spring.nexusfuture.word_diary.entity.SysAiScene;
import spring.nexusfuture.word_diary.service.AiModelService;
import spring.nexusfuture.word_diary.service.AiProviderService;

import java.util.List;
import java.util.Map;

/**
 * AI 供应商 + 模型目录 + 场景绑定 + 成本汇总
 */
@RestController
@RequestMapping("/api/ai-models")
public class AiModelController {

    private final AiModelService aiModelService;
    private final AiProviderService aiProviderService;

    public AiModelController(AiModelService aiModelService, AiProviderService aiProviderService) {
        this.aiModelService = aiModelService;
        this.aiProviderService = aiProviderService;
    }

    /** 供应商接入点（base-url / api-key） */
    @GetMapping("/providers")
    public R<List<AiProviderVO>> listProviders() {
        return R.ok(aiProviderService.listAll());
    }

    @GetMapping("/providers/{id}")
    public R<AiProviderVO> providerDetail(@PathVariable Long id) {
        return R.ok(aiProviderService.getById(id));
    }

    @PostMapping("/providers")
    public R<AiProviderVO> createProvider(@RequestBody SysAiProvider provider) {
        return R.ok(aiProviderService.create(provider));
    }

    @PutMapping("/providers/{id}")
    public R<AiProviderVO> updateProvider(@PathVariable Long id, @RequestBody SysAiProvider provider) {
        provider.setId(id);
        return R.ok(aiProviderService.update(provider));
    }

    @DeleteMapping("/providers/{id}")
    public R<Void> deleteProvider(@PathVariable Long id) {
        aiProviderService.delete(id);
        return R.ok();
    }

    /** 模型目录 */
    @GetMapping
    public R<List<SysAiModel>> list() {
        return R.ok(aiModelService.listAll());
    }

    @GetMapping("/scenes")
    public R<List<AiSceneVO>> listScenes() {
        return R.ok(aiModelService.listScenes());
    }

    @PostMapping("/scenes")
    public R<SysAiScene> createScene(@RequestBody SysAiScene scene) {
        return R.ok(aiModelService.createScene(scene));
    }

    @PutMapping("/scenes/{id}")
    public R<SysAiScene> updateSceneById(@PathVariable Long id, @RequestBody SysAiScene scene) {
        scene.setId(id);
        return R.ok(aiModelService.updateScene(scene));
    }

    /** 更新某条场景绑定使用的模型，body: { "modelKey": "qwen-turbo" }；推荐用 /scenes/{id} */
    @PutMapping("/scenes/by-key/{sceneKey}")
    public R<AiSceneVO> updateSceneModelCompat(@PathVariable String sceneKey, @RequestBody Map<String, String> body) {
        // 兼容旧前端：无 region 时改该 scene 下第一条
        var scenes = aiModelService.listScenes().stream()
                .filter(s -> sceneKey.equals(s.getSceneKey()))
                .toList();
        if (scenes.isEmpty()) {
            throw new spring.nexusfuture.word_diary.common.BizException("场景不存在: " + sceneKey);
        }
        String region = body.get("region");
        var target = scenes.stream()
                .filter(s -> region == null || region.isBlank() || region.equals(s.getRegion()))
                .findFirst()
                .orElse(scenes.get(0));
        return R.ok(aiModelService.updateSceneModelById(target.getId(), body.get("modelKey")));
    }

    @DeleteMapping("/scenes/{id}")
    public R<Void> deleteScene(@PathVariable Long id) {
        aiModelService.deleteScene(id);
        return R.ok();
    }

    @GetMapping("/cost/daily")
    public R<List<Map<String, Object>>> dailyCost() {
        return R.ok(aiModelService.dailyCostSummary());
    }

    @PostMapping("/cost/recalculate")
    public R<Integer> recalculate() {
        return R.ok(aiModelService.recalculateHistoricalCosts());
    }

    /** 模型 CRUD 统一挂在 /catalog，避免与 /scenes、/cost 路径冲突 */
    @GetMapping("/catalog/{id}")
    public R<SysAiModel> detail(@PathVariable Long id) {
        return R.ok(aiModelService.getById(id));
    }

    @PostMapping
    public R<SysAiModel> create(@RequestBody SysAiModel model) {
        return R.ok(aiModelService.create(model));
    }

    @PutMapping("/catalog/{id}")
    public R<SysAiModel> update(@PathVariable Long id, @RequestBody SysAiModel model) {
        model.setId(id);
        return R.ok(aiModelService.update(model));
    }

    /**
     * 用供应商 + 模型发一次问候测试，返回是否可用及模型回复/错误信息。
     * body: { "provider": "modelscope", "modelKey": "Qwen/..." }
     */
    @PostMapping("/test")
    public R<AiModelTestResultVO> testConnection(@RequestBody AiModelTestReq req) {
        if (req == null) {
            req = new AiModelTestReq();
        }
        return R.ok(aiModelService.testConnection(req.getProvider(), req.getModelKey()));
    }

    @DeleteMapping("/catalog/{id}")
    public R<Void> delete(@PathVariable Long id) {
        aiModelService.delete(id);
        return R.ok();
    }

    /** 兼容旧前端路径 */
    @PutMapping("/{id}")
    public R<SysAiModel> updateCompat(@PathVariable Long id, @RequestBody SysAiModel model) {
        model.setId(id);
        return R.ok(aiModelService.update(model));
    }

    @DeleteMapping("/{id}")
    public R<Void> deleteCompat(@PathVariable Long id) {
        aiModelService.delete(id);
        return R.ok();
    }
}
