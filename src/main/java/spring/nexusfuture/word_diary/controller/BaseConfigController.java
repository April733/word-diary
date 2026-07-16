package spring.nexusfuture.word_diary.controller;

import org.springframework.web.bind.annotation.*;
import spring.nexusfuture.word_diary.common.R;
import spring.nexusfuture.word_diary.entity.SysBaseConfig;
import spring.nexusfuture.word_diary.service.SysBaseConfigService;

import java.util.List;

/**
 * 系统基础配置（全局参数，可在库中手动维护或通过接口更新）
 */
@RestController
@RequestMapping("/api/config")
public class BaseConfigController {

    private final SysBaseConfigService configService;

    public BaseConfigController(SysBaseConfigService configService) {
        this.configService = configService;
    }

    /** 全部配置（含说明，便于后台或 DBeaver 对照） */
    @GetMapping("/base")
    public R<List<SysBaseConfig>> listAll() {
        return R.ok(configService.listAll());
    }

    /** 按分类，如 category=study */
    @GetMapping("/base/category/{category}")
    public R<List<SysBaseConfig>> listByCategory(@PathVariable String category) {
        return R.ok(configService.listByCategory(category));
    }

    @GetMapping("/base/{key}")
    public R<SysBaseConfig> getByKey(@PathVariable String key) {
        SysBaseConfig c = configService.getByKey(key);
        if (c == null) {
            return R.fail("配置不存在: " + key);
        }
        return R.ok(c);
    }

    /** 更新配置值（改价/改时间等运维场景） */
    @PutMapping("/base/{key}")
    public R<Void> updateValue(@PathVariable String key, @RequestBody UpdateConfigReq req) {
        if (req.getConfigValue() == null) {
            return R.fail("configValue 不能为空");
        }
        configService.updateValue(key, req.getConfigValue());
        return R.ok();
    }

    public static class UpdateConfigReq {
        private String configValue;

        public String getConfigValue() {
            return configValue;
        }

        public void setConfigValue(String configValue) {
            this.configValue = configValue;
        }
    }
}
