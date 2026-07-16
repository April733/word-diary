package spring.nexusfuture.word_diary.controller;

import org.springframework.web.bind.annotation.*;
import spring.nexusfuture.word_diary.common.R;
import spring.nexusfuture.word_diary.entity.SysAiModel;
import spring.nexusfuture.word_diary.service.AiModelService;

import java.util.List;
import java.util.Map;

/**
 * AI 场景/模型/单价管理 + 成本汇总
 */
@RestController
@RequestMapping("/api/ai-models")
public class AiModelController {

    private final AiModelService aiModelService;

    public AiModelController(AiModelService aiModelService) {
        this.aiModelService = aiModelService;
    }

    @GetMapping
    public R<List<SysAiModel>> list() {
        return R.ok(aiModelService.listAll());
    }

    @GetMapping("/scenes")
    public R<List<SysAiModel>> listScenes() {
        return R.ok(aiModelService.listScenes());
    }

    /** 更新某场景使用的模型，body: { "modelKey": "qwen-turbo" } */
    @PutMapping("/scenes/{sceneKey}")
    public R<SysAiModel> updateScene(@PathVariable String sceneKey, @RequestBody Map<String, String> body) {
        String modelKey = body.get("modelKey");
        return R.ok(aiModelService.updateSceneModel(sceneKey, modelKey));
    }

    @GetMapping("/{id}")
    public R<SysAiModel> detail(@PathVariable Long id) {
        return R.ok(aiModelService.getById(id));
    }

    @PostMapping
    public R<SysAiModel> create(@RequestBody SysAiModel model) {
        return R.ok(aiModelService.create(model));
    }

    @PutMapping("/{id}")
    public R<SysAiModel> update(@PathVariable Long id, @RequestBody SysAiModel model) {
        model.setId(id);
        return R.ok(aiModelService.update(model));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        aiModelService.delete(id);
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
}
