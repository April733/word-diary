package spring.nexusfuture.word_diary.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

/**
 * 数据库启动初始化：补全缺失字段 / 表 / 种子数据（幂等，可重复执行）
 */
@Slf4j
@Component
@Order(1)
public class DatabaseInitConfig implements ApplicationRunner {

    @Autowired
    private DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ensureColumn(conn, stmt, "wd_book", "file_size",
                    "ALTER TABLE wd_book ADD COLUMN file_size bigint DEFAULT 0 COMMENT '文件大小(字节)' AFTER file_type");

            ensureColumn(conn, stmt, "wd_ai_message", "parent_id",
                    "ALTER TABLE wd_ai_message ADD COLUMN parent_id bigint DEFAULT NULL COMMENT '关联的问题ID' AFTER session_id");

            // 修改 wd_ai_message.feature_key 长度（多选功能拼接后可能超过32）
            // 每次启动都确保 ≥255，避免生产库仍停留在 varchar(32) 导致解析保存 500
            try {
                stmt.execute("ALTER TABLE wd_ai_message MODIFY COLUMN feature_key varchar(255) DEFAULT NULL COMMENT '触发此消息的AI功能标识'");
                log.info("数据库字段检查: wd_ai_message.feature_key → varchar(255)");
            } catch (Exception e) {
                log.warn("扩展 wd_ai_message.feature_key 失败（可忽略若已是255）: {}", e.getMessage());
            }

            // 管理员角色
            ensureColumn(conn, stmt, "wd_user", "role",
                    "ALTER TABLE wd_user ADD COLUMN role varchar(16) NOT NULL DEFAULT 'user' COMMENT '角色 user|admin' AFTER phone");

            // SRS 字段迁到 wd_word；从旧 plan 回填后删表
            migrateLearnPlanOntoWord(conn, stmt);

            // 下线会员等级体系：删表 + 去掉关联列
            dropMemberTierArtifacts(conn, stmt);
            // 下线邀请码
            dropTableIfExists(conn, stmt, "wd_invite_code");
            try {
                dropColumnIfExists(conn, stmt, "wd_user", "is_member");
            } catch (Exception e) {
                log.warn("清理 wd_user.is_member 失败（可忽略）: {}", e.getMessage());
            }

            // 场景系统提示词表 + 默认种子
            ensureScenePromptTable(conn, stmt);
            seedScenePromptsIfEmpty(stmt);
            migrateScenePromptLanguagePlaceholder(stmt);
            migrateReadingChatLanguagePlaceholder(stmt);
            migrateWordParsePromptSchema(conn);
            ensureWordFeaturePhonetics(conn, stmt);
            migrateWordFeatureConciseGloss(conn, stmt);
            migrateFeaturePromptLanguageNeutral(conn, stmt);
            migrateFeaturePromptWordMeaningJson(conn, stmt);
            migrateWordFeaturePromptV3(conn, stmt);
            migrateGrammarSentencePatternSplit(conn, stmt);
            migrateFeatureKeyBasedJsonContract(conn, stmt);
            migrateSplitAiModelAndScene(conn, stmt);
            ensureAiProviderTable(conn, stmt);
            ensureAbuseNoticeTables(conn, stmt);
            ensureColumn(conn, stmt, "wd_abuse_notice", "submitter_user_id",
                    "ALTER TABLE wd_abuse_notice ADD COLUMN submitter_user_id bigint DEFAULT NULL COMMENT '提交账号用户ID' AFTER status");
            ensureWritingEssayTable(conn, stmt);
            ensureColumn(conn, stmt, "wd_writing_essay", "prompt_image_key",
                    "ALTER TABLE wd_writing_essay ADD COLUMN prompt_image_key varchar(512) DEFAULT NULL COMMENT '题目截图COS key' AFTER prompt_text");
            ensureColumn(conn, stmt, "wd_writing_essay", "title",
                    "ALTER TABLE wd_writing_essay ADD COLUMN title varchar(120) NOT NULL DEFAULT '' COMMENT '用户自命名' AFTER user_id");
            ensureWritingEssayGradeTable(conn, stmt);
            seedWritingGradeHistoryFromExisting(conn);
            seedWritingGradePromptIfMissing(conn);
            // 偏好语言（须先于 preferred_ai_region；旧库可能缺此列）
            ensureColumn(conn, stmt, "wd_user", "preferred_locale",
                    "ALTER TABLE wd_user ADD COLUMN preferred_locale varchar(10) NOT NULL DEFAULT 'zh-CN' COMMENT '偏好语言' AFTER role");
            ensureColumn(conn, stmt, "wd_user", "preferred_ai_region",
                    "ALTER TABLE wd_user ADD COLUMN preferred_ai_region varchar(16) NOT NULL DEFAULT 'auto' COMMENT 'AI线路偏好 auto|cn|global' AFTER preferred_locale");

            // AI 功能标签多语言（中文源文 → AI 同步 en/id/th/vi）
            ensureColumn(conn, stmt, "sys_ai_feature", "name_i18n",
                    "ALTER TABLE sys_ai_feature ADD COLUMN name_i18n json DEFAULT NULL COMMENT '标签多语言 JSON: en/id/th/vi' AFTER description");
            ensureColumn(conn, stmt, "sys_ai_feature", "description_i18n",
                    "ALTER TABLE sys_ai_feature ADD COLUMN description_i18n json DEFAULT NULL COMMENT '描述多语言 JSON: en/id/th/vi' AFTER name_i18n");
            seedWordFeatureI18nIfMissing(conn);
        } catch (Exception e) {
            log.warn("数据库字段初始化警告: {}", e.getMessage());
        }

        // 独立执行：避免被上面其它迁移异常短路，导致 region 列一直缺失
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            migrateAiSceneRegion(conn, stmt);
            seedAiScenes(stmt);
        } catch (Exception e) {
            log.warn("sys_ai_scene.region 初始化警告: {}", e.getMessage());
        }
    }

    /**
     * word 功能历史上缺 name_i18n，非中文界面会一直显示「单词」。
     * 仅在 name_i18n 为空时写入默认翻译（不覆盖后台已同步的译文）。
     */
    private void seedWordFeatureI18nIfMissing(Connection conn) {
        String nameJson = "{\"en\":\"Words\",\"id\":\"Kata\",\"th\":\"คำศัพท์\",\"vi\":\"Từ vựng\"}";
        String descJson = "{\"en\":\"Explain key difficult words with phonetics, POS and meanings.\","
                + "\"id\":\"Jelaskan kata sulit penting beserta fonetik, jenis kata, dan arti.\","
                + "\"th\":\"Explain key difficult words with phonetics, POS and meanings.\","
                + "\"vi\":\"Giải thích từ khó quan trọng kèm phiên âm, từ loại và nghĩa.\"}";
        try (var ps = conn.prepareStatement("""
                UPDATE sys_ai_feature
                SET name_i18n = ?, description_i18n = ?, update_time = NOW()
                WHERE feature_key = 'word' AND is_deleted = 0
                  AND (name_i18n IS NULL OR name_i18n = '' OR name_i18n = '{}')
                """)) {
            ps.setString(1, nameJson);
            ps.setString(2, descJson);
            int n = ps.executeUpdate();
            if (n > 0) {
                log.info("数据库种子初始化完成: sys_ai_feature.word name_i18n 已补齐，共 {} 行", n);
            }
        } catch (Exception e) {
            log.warn("补齐 sys_ai_feature.word i18n 失败: {}", e.getMessage());
        }
    }

    /**
     * sys_ai_scene 增加 region（cn/global），唯一键改为 (scene_key, region)，并为已有场景补 global 行。
     * 用「直接 ALTER + 忽略重复」避免 DatabaseMetaData 误判导致跳过。
     */
    private void migrateAiSceneRegion(Connection conn, Statement stmt) {
        try {
            addColumnIgnoreDuplicate(stmt,
                    "ALTER TABLE sys_ai_scene ADD COLUMN region varchar(16) NOT NULL DEFAULT 'cn' COMMENT '路由区 cn|global' AFTER scene_name",
                    "ALTER TABLE sys_ai_scene ADD COLUMN region varchar(16) NOT NULL DEFAULT 'cn' COMMENT '路由区 cn|global'");

            // 必须先放开 uk_scene_key，才能为同一 scene 插入 global 行
            try {
                stmt.execute("ALTER TABLE sys_ai_scene DROP INDEX uk_scene_key");
                log.info("已删除索引 sys_ai_scene.uk_scene_key");
            } catch (Exception e) {
                // 索引本就不存在则忽略
                log.debug("DROP uk_scene_key: {}", e.getMessage());
            }

            stmt.executeUpdate("""
                    INSERT INTO sys_ai_scene
                    (scene_key, scene_name, region, model_key, sort_num, is_enabled, remark, create_time, update_time)
                    SELECT s.scene_key, s.scene_name, 'global', s.model_key, s.sort_num, s.is_enabled,
                           CONCAT(COALESCE(s.remark, ''), CASE WHEN COALESCE(s.remark,'')='' THEN '' ELSE '；' END, '海外线路副本'),
                           NOW(), NOW()
                    FROM sys_ai_scene s
                    WHERE s.is_deleted = 0
                      AND (s.region = 'cn' OR s.region IS NULL OR s.region = '')
                      AND NOT EXISTS (
                        SELECT 1 FROM sys_ai_scene g
                        WHERE g.scene_key = s.scene_key AND g.region = 'global' AND g.is_deleted = 0
                      )
                    """);

            try {
                stmt.execute("ALTER TABLE sys_ai_scene ADD UNIQUE KEY uk_scene_region (scene_key, region)");
                log.info("已添加索引 sys_ai_scene.uk_scene_region");
            } catch (Exception e) {
                log.debug("ADD uk_scene_region: {}", e.getMessage());
            }
            log.info("sys_ai_scene.region 迁移完成");
        } catch (Exception e) {
            log.warn("迁移 sys_ai_scene.region 失败: {}", e.getMessage(), e);
        }
    }

    private void addColumnIgnoreDuplicate(Statement stmt, String... alterSqls) throws Exception {
        Exception last = null;
        for (String sql : alterSqls) {
            try {
                stmt.execute(sql);
                log.info("数据库字段初始化完成: {}", sql);
                return;
            } catch (Exception e) {
                last = e;
                String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                if (msg.contains("duplicate column") || msg.contains("already exists")) {
                    log.debug("列已存在，跳过: {}", sql);
                    return;
                }
            }
        }
        if (last != null) {
            throw last;
        }
    }

    /** 在 region 迁移完成后写入场景种子（幂等） */
    private void seedAiScenes(Statement stmt) {
        try {
            stmt.executeUpdate("""
                    INSERT IGNORE INTO sys_ai_scene
                    (scene_key, scene_name, region, model_key, sort_num, is_enabled, remark)
                    VALUES
                    ('feature', '阅读解析（选中文字 AI 功能）', 'cn', 'deepseek-v4-flash', 1, 1, '阅读器勾选单词/释义等功能'),
                    ('feature', '阅读解析（选中文字 AI 功能）', 'global', 'deepseek-v4-flash', 1, 1, '海外线路，可改为 Gemini 等'),
                    ('word_parse', '单词批量导入', 'cn', 'qwen-turbo', 2, 1, '单词本批量导入 / AI 补全'),
                    ('word_parse', '单词批量导入', 'global', 'qwen-turbo', 2, 1, '海外线路，可改为 Gemini 等'),
                    ('reading_chat', '阅读自由问答', 'cn', 'deepseek-v4-flash', 3, 1, '阅读器右侧自由提问'),
                    ('reading_chat', '阅读自由问答', 'global', 'deepseek-v4-flash', 3, 1, '海外线路，可改为 Gemini 等'),
                    ('writing_grade', '雅思写作批改', 'cn', 'deepseek-v4-flash', 4, 1, '写作评分+润色范文'),
                    ('writing_grade', '雅思写作批改', 'global', 'deepseek-v4-flash', 4, 1, '海外线路'),
                    ('default', '未匹配场景回退', 'cn', 'deepseek-v4-flash', 99, 1, '未知 scene 时回退'),
                    ('default', '未匹配场景回退', 'global', 'deepseek-v4-flash', 99, 1, '未知 scene 时回退')
                    """);
        } catch (Exception e) {
            log.warn("写入 sys_ai_scene 种子失败: {}", e.getMessage());
        }
    }

    private boolean indexExists(Connection conn, String table, String indexName) throws Exception {
        var rs = conn.getMetaData().getIndexInfo(null, null, table, false, false);
        try {
            while (rs.next()) {
                String name = rs.getString("INDEX_NAME");
                if (indexName.equalsIgnoreCase(name)) {
                    return true;
                }
            }
            return false;
        } finally {
            rs.close();
        }
    }

    /**
     * 将「场景+模型」合并表拆成：sys_ai_model（模型目录）+ sys_ai_scene（场景绑定）。
     * 幂等：检测 sys_ai_model 是否仍含 scene_key。
     */
    private void migrateSplitAiModelAndScene(Connection conn, Statement stmt) {
        try {
            var modelTable = conn.getMetaData().getTables(null, null, "sys_ai_model", new String[]{"TABLE"});
            if (!modelTable.next()) {
                modelTable.close();
                return;
            }
            modelTable.close();

            var sceneCol = conn.getMetaData().getColumns(null, null, "sys_ai_model", "scene_key");
            boolean hasSceneKey = sceneCol.next();
            sceneCol.close();
            if (!hasSceneKey) {
                // 已是新结构：确保场景表存在并有种子，并清理重复 model_key
                ensureSceneTableAndSeed(conn, stmt);
                dedupeAiModels(stmt);
                return;
            }

            log.info("开始迁移: 拆分 sys_ai_model（场景+模型）→ 模型目录 + 场景绑定");

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS `sys_ai_scene` (
                      `id` bigint NOT NULL AUTO_INCREMENT,
                      `scene_key` varchar(32) NOT NULL,
                      `scene_name` varchar(64) NOT NULL,
                      `model_key` varchar(64) NOT NULL,
                      `sort_num` int NOT NULL DEFAULT 0,
                      `is_enabled` tinyint NOT NULL DEFAULT 1,
                      `remark` varchar(512) DEFAULT '',
                      `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      `is_deleted` tinyint NOT NULL DEFAULT 0,
                      PRIMARY KEY (`id`),
                      UNIQUE KEY `uk_scene_key` (`scene_key`),
                      KEY `idx_model_key` (`model_key`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI调用场景与模型映射'
                    """);

            // 从旧表灌入场景（已有则忽略）
            stmt.executeUpdate("""
                    INSERT IGNORE INTO sys_ai_scene
                    (scene_key, scene_name, model_key, sort_num, is_enabled, remark, create_time, update_time)
                    SELECT scene_key, COALESCE(NULLIF(scene_name,''), scene_key), model_key,
                           COALESCE(sort_num, 0), COALESCE(is_enabled, 1), COALESCE(remark, ''),
                           COALESCE(create_time, NOW()), COALESCE(update_time, NOW())
                    FROM sys_ai_model
                    WHERE is_deleted = 0 AND scene_key IS NOT NULL AND scene_key <> ''
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS `sys_ai_model_new` (
                      `id` bigint NOT NULL AUTO_INCREMENT,
                      `model_key` varchar(64) NOT NULL,
                      `display_name` varchar(128) NOT NULL DEFAULT '',
                      `provider` varchar(64) NOT NULL DEFAULT '',
                      `currency` varchar(8) NOT NULL DEFAULT 'CNY',
                      `input_price_per_million` decimal(14,6) NOT NULL DEFAULT 0,
                      `output_price_per_million` decimal(14,6) NOT NULL DEFAULT 0,
                      `sort_num` int NOT NULL DEFAULT 0,
                      `is_enabled` tinyint NOT NULL DEFAULT 1,
                      `remark` varchar(512) DEFAULT '',
                      `create_by` bigint DEFAULT NULL,
                      `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      `update_by` bigint DEFAULT NULL,
                      `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      `is_deleted` tinyint NOT NULL DEFAULT 0,
                      PRIMARY KEY (`id`),
                      UNIQUE KEY `uk_model_key` (`model_key`),
                      KEY `idx_enabled` (`is_enabled`, `is_deleted`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI模型目录与Token单价'
                    """);

            // 每个 model_key 保留一条（最小 id）
            stmt.executeUpdate("""
                    INSERT IGNORE INTO sys_ai_model_new
                    (model_key, display_name, provider, currency,
                     input_price_per_million, output_price_per_million,
                     sort_num, is_enabled, remark, create_time, update_time)
                    SELECT m.model_key,
                           COALESCE(NULLIF(m.display_name,''), m.model_key),
                           COALESCE(m.provider, ''),
                           COALESCE(NULLIF(m.currency,''), 'CNY'),
                           COALESCE(m.input_price_per_million, 0),
                           COALESCE(m.output_price_per_million, 0),
                           COALESCE(m.sort_num, 0),
                           COALESCE(m.is_enabled, 1),
                           COALESCE(m.remark, ''),
                           COALESCE(m.create_time, NOW()),
                           COALESCE(m.update_time, NOW())
                    FROM sys_ai_model m
                    INNER JOIN (
                      SELECT model_key, MIN(id) AS min_id
                      FROM sys_ai_model
                      WHERE is_deleted = 0 AND model_key IS NOT NULL AND model_key <> ''
                      GROUP BY model_key
                    ) t ON t.min_id = m.id
                    """);

            stmt.execute("RENAME TABLE sys_ai_model TO sys_ai_model_legacy_scene_merged, sys_ai_model_new TO sys_ai_model");
            log.info("数据库迁移完成: sys_ai_model 已拆分为模型目录；原表备份为 sys_ai_model_legacy_scene_merged");
            dedupeAiModels(stmt);
        } catch (Exception e) {
            log.warn("迁移拆分 sys_ai_model/sys_ai_scene 失败: {}", e.getMessage());
        }
    }

    /** 软删同 model_key 的重复行，仅保留最小 id */
    private void dedupeAiModels(Statement stmt) {
        try {
            int n = stmt.executeUpdate("""
                    UPDATE sys_ai_model m
                    INNER JOIN (
                      SELECT model_key, MIN(id) AS keep_id
                      FROM sys_ai_model
                      WHERE is_deleted = 0 AND model_key IS NOT NULL AND model_key <> ''
                      GROUP BY model_key
                      HAVING COUNT(*) > 1
                    ) d ON m.model_key = d.model_key AND m.id <> d.keep_id
                    SET m.is_deleted = 1, m.update_time = NOW()
                    WHERE m.is_deleted = 0
                    """);
            if (n > 0) {
                log.info("已清理重复模型目录行: soft-deleted {} 条", n);
            }
        } catch (Exception e) {
            log.warn("清理重复模型失败: {}", e.getMessage());
        }
    }

    private void ensureSceneTableAndSeed(Connection conn, Statement stmt) throws Exception {
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS `sys_ai_scene` (
                  `id` bigint NOT NULL AUTO_INCREMENT,
                  `scene_key` varchar(32) NOT NULL,
                  `scene_name` varchar(64) NOT NULL,
                  `region` varchar(16) NOT NULL DEFAULT 'cn',
                  `model_key` varchar(64) NOT NULL,
                  `sort_num` int NOT NULL DEFAULT 0,
                  `is_enabled` tinyint NOT NULL DEFAULT 1,
                  `remark` varchar(512) DEFAULT '',
                  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  `is_deleted` tinyint NOT NULL DEFAULT 0,
                  PRIMARY KEY (`id`),
                  UNIQUE KEY `uk_scene_region` (`scene_key`, `region`),
                  KEY `idx_model_key` (`model_key`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI调用场景与模型映射（按地区）'
                """);
        // 场景种子在 migrateAiSceneRegion + seedAiScenes 中写入（需先具备 region 列与新唯一键）
        stmt.executeUpdate("""
                INSERT IGNORE INTO sys_ai_model
                (model_key, display_name, provider, currency,
                 input_price_per_million, output_price_per_million, sort_num, is_enabled, remark)
                VALUES
                ('deepseek-v4-flash', 'DeepSeek V4 Flash', 'deepseek', 'CNY', 1.0, 2.0, 1, 1, ''),
                ('qwen-turbo', 'Qwen Turbo', 'aliyun', 'CNY', 0.3, 0.6, 2, 1, '')
                """);
    }

    private void ensureColumn(Connection conn, Statement stmt, String table, String column, String alterSql) throws Exception {
        var rs = conn.getMetaData().getColumns(null, null, table, column);
        if (!rs.next()) {
            stmt.execute(alterSql);
            log.info("数据库字段初始化完成: {}.{} 已添加", table, column);
        } else {
            log.debug("数据库字段初始化: {}.{} 已存在，跳过", table, column);
        }
        rs.close();
    }

    /** 删除 sys_member_tier 及 wd_user.member_tier 列（幂等） */
    private void dropMemberTierArtifacts(Connection conn, Statement stmt) {
        try {
            dropColumnIfExists(conn, stmt, "wd_user", "member_tier");
            dropColumnIfExists(conn, stmt, "wd_invite_code", "member_tier");
            dropTableIfExists(conn, stmt, "sys_member_tier");
        } catch (Exception e) {
            log.warn("清理会员等级相关结构失败（可忽略）: {}", e.getMessage());
        }
    }

    /**
     * status + status_data 模型：补列、旧列回填、删 SM-2 字段与旧学习表。
     */
    private void migrateLearnPlanOntoWord(Connection conn, Statement stmt) {
        try {
            ensureColumn(conn, stmt, "wd_word", "status",
                    "ALTER TABLE wd_word ADD COLUMN status tinyint NOT NULL DEFAULT 0 COMMENT '学习状态: 0未学 1~6复习阶梯 7熟记' AFTER user_id");
            ensureColumn(conn, stmt, "wd_word", "status_data",
                    "ALTER TABLE wd_word ADD COLUMN status_data datetime DEFAULT NULL COMMENT '进入当前status的时间' AFTER status");

            // 若仍有旧 SM-2 列，先回填 status_data / 映射旧 status（仅此路径做 3→7）
            var rs = conn.getMetaData().getColumns(null, null, "wd_word", "last_study_at");
            boolean hasLegacy = rs.next();
            rs.close();
            if (hasLegacy) {
                stmt.executeUpdate("""
                        UPDATE wd_word SET status_data = COALESCE(last_study_at, first_study_time, create_time, NOW())
                        WHERE status_data IS NULL
                        """);
                // 旧模型：3=熟记 → 新 7
                stmt.executeUpdate("UPDATE wd_word SET status = 7 WHERE status = 3");
            } else {
                stmt.executeUpdate("""
                        UPDATE wd_word SET status_data = COALESCE(status_data, create_time, NOW())
                        WHERE status_data IS NULL
                        """);
            }

            String[] legacyCols = {
                    "current_level", "first_study_time", "next_review_time", "review_times",
                    "interval_days", "ease_factor", "rep_count", "lapse_count",
                    "last_feedback", "last_study_at", "priority_score"
            };
            for (String col : legacyCols) {
                dropColumnIfExists(conn, stmt, "wd_word", col);
            }

            var tables = conn.getMetaData().getTables(null, null, "wd_learn_plan", new String[]{"TABLE"});
            boolean planExists = tables.next();
            tables.close();
            if (planExists) {
                int n = stmt.executeUpdate("""
                        UPDATE wd_word w
                        INNER JOIN (
                          SELECT p.*
                          FROM wd_learn_plan p
                          INNER JOIN (
                            SELECT word_id, MAX(id) AS max_id
                            FROM wd_learn_plan
                            WHERE is_deleted = 0
                            GROUP BY word_id
                          ) latest ON latest.word_id = p.word_id AND latest.max_id = p.id
                          WHERE p.is_deleted = 0
                        ) p ON p.word_id = w.id
                        SET w.status = CASE
                              WHEN COALESCE(p.learn_status, 0) = 3 THEN 7
                              WHEN COALESCE(p.learn_status, 0) = 4 THEN 0
                              WHEN COALESCE(p.learn_status, 0) IN (1, 2) THEN p.learn_status
                              ELSE 0 END,
                            w.status_data = COALESCE(p.last_study_at, p.first_study_time, w.create_time, NOW()),
                            w.update_time = NOW()
                        """);
                log.info("已从 wd_learn_plan 回填 wd_word status/status_data: {} 行", n);
            }

            dropTableIfExists(conn, stmt, "wd_learn_plan");
            dropTableIfExists(conn, stmt, "wd_study_queue");
            dropTableIfExists(conn, stmt, "wd_study_daily");
            dropTableIfExists(conn, stmt, "wd_learn_record");
        } catch (Exception e) {
            log.warn("迁移学习计划到 wd_word 失败（可忽略）: {}", e.getMessage());
        }
    }

    private void dropTableIfExists(Connection conn, Statement stmt, String table) throws Exception {
        var tables = conn.getMetaData().getTables(null, null, table, new String[]{"TABLE"});
        boolean exists = tables.next();
        tables.close();
        if (!exists) {
            return;
        }
        stmt.execute("DROP TABLE IF EXISTS `" + table + "`");
        log.info("数据库表清理完成: {} 已删除", table);
    }

    private void dropColumnIfExists(Connection conn, Statement stmt, String table, String column) throws Exception {
        var rs = conn.getMetaData().getColumns(null, null, table, column);
        boolean exists = rs.next();
        rs.close();
        if (!exists) {
            return;
        }
        stmt.execute("ALTER TABLE `" + table + "` DROP COLUMN `" + column + "`");
        log.info("数据库字段清理完成: {}.{} 已删除", table, column);
    }

    private void ensureAiProviderTable(Connection conn, Statement stmt) throws Exception {
        var tables = conn.getMetaData().getTables(null, null, "sys_ai_provider", new String[]{"TABLE"});
        boolean exists = tables.next();
        tables.close();
        if (!exists) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS `sys_ai_provider` (
                      `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                      `provider_key` varchar(64) NOT NULL COMMENT '供应商标识，对齐 sys_ai_model.provider',
                      `display_name` varchar(128) NOT NULL DEFAULT '' COMMENT '展示名',
                      `base_url` varchar(512) NOT NULL DEFAULT '' COMMENT 'OpenAI 兼容根地址',
                      `api_key` varchar(512) NOT NULL DEFAULT '' COMMENT 'API Key',
                      `sort_num` int NOT NULL DEFAULT 0,
                      `is_enabled` tinyint NOT NULL DEFAULT 1,
                      `remark` varchar(512) DEFAULT '',
                      `create_by` bigint DEFAULT NULL,
                      `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      `update_by` bigint DEFAULT NULL,
                      `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      `is_deleted` tinyint NOT NULL DEFAULT 0,
                      PRIMARY KEY (`id`),
                      UNIQUE KEY `uk_provider_key` (`provider_key`),
                      KEY `idx_enabled` (`is_enabled`, `is_deleted`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI供应商接入点'
                    """);
            log.info("数据库表初始化完成: sys_ai_provider 已创建");
        }
        stmt.executeUpdate("""
                INSERT IGNORE INTO sys_ai_provider
                (provider_key, display_name, base_url, api_key, sort_num, is_enabled, remark)
                VALUES
                ('deepseek', 'DeepSeek', 'https://api.deepseek.com', '', 1, 1, '密钥可由 yml 引导或后台填写'),
                ('aliyun', '阿里云百炼', 'https://dashscope.aliyuncs.com/compatible-mode', '', 2, 1, '密钥可由 yml 引导或后台填写'),
                ('modelscope', '魔塔 ModelScope', 'https://api-inference.modelscope.cn', '', 3, 1, '密钥可由 yml 引导或后台填写')
                """);
    }

    private void ensureAbuseNoticeTables(Connection conn, Statement stmt) throws Exception {
        var noticeTable = conn.getMetaData().getTables(null, null, "wd_abuse_notice", new String[]{"TABLE"});
        boolean noticeExists = noticeTable.next();
        noticeTable.close();
        if (!noticeExists) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS `wd_abuse_notice` (
                      `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                      `notice_no` varchar(32) NOT NULL COMMENT '通知编号（对外回执）',
                      `notice_type` varchar(16) NOT NULL COMMENT 'copyright|illegal|complaint|suggestion',
                      `status` tinyint NOT NULL DEFAULT 0 COMMENT '0待审 1合格待处理 2已处理 3驳回 4关闭',
                      `submitter_user_id` bigint DEFAULT NULL COMMENT '提交账号用户ID',
                      `complainant_name` varchar(128) NOT NULL COMMENT '投诉人姓名/名称',
                      `complainant_email` varchar(128) NOT NULL COMMENT '投诉人邮箱',
                      `complainant_phone` varchar(32) DEFAULT '' COMMENT '投诉人电话',
                      `organization` varchar(256) DEFAULT '' COMMENT '权利人/机构名称',
                      `right_description` text COMMENT '权利说明（版权投诉）',
                      `ownership_proof` text COMMENT '权属证明说明',
                      `target_url` varchar(1024) DEFAULT '' COMMENT '涉事链接',
                      `target_book_id` bigint DEFAULT NULL COMMENT '涉事电子书ID',
                      `target_description` text NOT NULL COMMENT '涉事内容描述',
                      `detail` text NOT NULL COMMENT '投诉/举报详情',
                      `good_faith` tinyint NOT NULL DEFAULT 0 COMMENT '是否勾选善意声明 1是',
                      `received_at` datetime NOT NULL COMMENT '收到通知时间',
                      `qualified_at` datetime DEFAULT NULL COMMENT '认定为合格通知时间',
                      `due_at` datetime DEFAULT NULL COMMENT '处理时限（合格后24-48h）',
                      `actioned_at` datetime DEFAULT NULL COMMENT '实际处理完成时间',
                      `action_taken` varchar(32) DEFAULT '' COMMENT 'remove|restrict|reject|none',
                      `handle_note` text COMMENT '处理说明',
                      `handler_id` bigint DEFAULT NULL COMMENT '处理人用户ID',
                      `ip_address` varchar(64) DEFAULT '' COMMENT '提交IP',
                      `user_agent` varchar(512) DEFAULT '' COMMENT '提交UA',
                      `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      `is_deleted` tinyint NOT NULL DEFAULT 0,
                      PRIMARY KEY (`id`),
                      UNIQUE KEY `uk_notice_no` (`notice_no`),
                      KEY `idx_status_due` (`status`, `due_at`),
                      KEY `idx_type` (`notice_type`),
                      KEY `idx_received` (`received_at`),
                      KEY `idx_book` (`target_book_id`),
                      KEY `idx_submitter` (`submitter_user_id`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帮助中心投诉建议通知'
                    """);
            log.info("数据库表初始化完成: wd_abuse_notice 已创建");
        }

        var logTable = conn.getMetaData().getTables(null, null, "wd_abuse_notice_log", new String[]{"TABLE"});
        boolean logExists = logTable.next();
        logTable.close();
        if (!logExists) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS `wd_abuse_notice_log` (
                      `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                      `notice_id` bigint NOT NULL COMMENT '通知ID',
                      `event_type` varchar(32) NOT NULL COMMENT 'received|qualified|reviewing|removed|restricted|rejected|closed|note',
                      `event_detail` text COMMENT '事件详情/证据摘要',
                      `operator_id` bigint DEFAULT NULL COMMENT '操作人，NULL=系统/投诉人提交',
                      `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件时间点',
                      PRIMARY KEY (`id`),
                      KEY `idx_notice_time` (`notice_id`, `create_time`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='侵权投诉处理时间线（证据留存）'
                    """);
            log.info("数据库表初始化完成: wd_abuse_notice_log 已创建");
        }
    }

    private void ensureScenePromptTable(Connection conn, Statement stmt) throws Exception {
        var tables = conn.getMetaData().getTables(null, null, "sys_ai_scene_prompt", new String[]{"TABLE"});
        if (tables.next()) {
            tables.close();
            return;
        }
        tables.close();
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS `sys_ai_scene_prompt` (
                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                  `scene_key` varchar(32) NOT NULL COMMENT '场景: word_parse/reading_chat/feature',
                  `locale` varchar(10) NOT NULL DEFAULT 'zh-CN' COMMENT '语言',
                  `scene_name` varchar(64) NOT NULL DEFAULT '' COMMENT '场景展示名',
                  `system_prompt` mediumtext NOT NULL COMMENT '系统提示词',
                  `remark` varchar(255) DEFAULT '' COMMENT '备注',
                  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
                  `sort_num` int NOT NULL DEFAULT 0 COMMENT '排序',
                  `create_by` bigint DEFAULT NULL,
                  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  `update_by` bigint DEFAULT NULL,
                  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  `is_deleted` tinyint NOT NULL DEFAULT 0,
                  PRIMARY KEY (`id`),
                  UNIQUE KEY `uk_scene_locale` (`scene_key`, `locale`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI场景系统提示词'
                """);
        log.info("数据库表初始化完成: sys_ai_scene_prompt 已创建");
    }

    private void seedScenePromptsIfEmpty(Statement stmt) throws Exception {
        try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM sys_ai_scene_prompt WHERE is_deleted = 0")) {
            if (rs.next() && rs.getLong(1) > 0) {
                return;
            }
        } catch (Exception e) {
            log.warn("检查 sys_ai_scene_prompt 种子失败: {}", e.getMessage());
            return;
        }

        stmt.execute("""
                INSERT INTO `sys_ai_scene_prompt`
                (`scene_key`, `locale`, `scene_name`, `system_prompt`, `remark`, `status`, `sort_num`) VALUES
                ('word_parse', 'zh-CN', '单词解析',
                '{global_constraint}
你是一个专业的英语词典助手。用户会给你一个英文单词/短语列表（JSON数组），请为每个词条补全以下信息，并以严格的 JSON 数组格式返回。

返回格式要求：
[
  {
    "wordProto": "原词",
    "usPhonetic": "美式音标，如 /ˈeksəmpəl/",
    "ukPhonetic": "英式音标，如 /ɪɡˈzɑːmpəl/",
    "partSpeechMean": "词性+中文释义，如 n. 例子；v. 举例",
    "sentenceExample": "为每个词性各提供一个例句，格式：词性标记. 英文例句 中文翻译。多个词性用换行符\\n分隔",
    "rootAffix": "词根词缀解析；短语可写「短语，无词根词缀」"
  }
]

要求：
1. 必须返回完整的 JSON 数组，不要有任何额外的解释文字
2. 字段名必须严格使用上述 camelCase（wordProto/usPhonetic/ukPhonetic/partSpeechMean/sentenceExample/rootAffix），禁止使用 word、phonetic、cn_definition 等其它字段名
3. 字段不能为空，如果确实没有信息，写\"暂无\"
4. 音标使用 IPA 国际音标
5. 对于短语，partSpeechMean 标注为「短语」',
                '占位符 {global_constraint}=按语言注入回复规则；字段必须与前端 AiParseResult 一致', 1, 1),
                ('reading_chat', 'zh-CN', '阅读对话',
                '{global_constraint}
                你是一个专业的英语阅读助手。用户正在阅读英文原著，请用{language}回答用户的问题。
                用户当前选中的上下文是："{context}"',
                '占位符 {global_constraint}/{language}/{locale}/{context}', 1, 2),
                ('feature', 'zh-CN', '阅读功能分析',
                '{global_constraint}
你是英语阅读解析助手。
{feature_prompts}
用户选中原文："{context}"
若有目标词：{target_words}

【输出】只返回一个 JSON 对象，不要 Markdown。
顶层键必须与上方勾选功能的 feature_key 完全一致（如 meaning / word / collocation / grammar / sentence_pattern / phonetic）。
未勾选的键不要写。
文本类功能：值为字符串。
词条类功能（word / collocation / root_affix / example）：值为数组，元素含 word、meaning。',
                '占位符 {global_constraint}/{language}/{feature_prompts}/{context}/{target_words}；按 feature_key 分包 JSON', 1, 3)
                """);
        log.info("数据库种子初始化完成: sys_ai_scene_prompt 默认场景提示词已写入");
    }

    /**
     * 把旧种子里硬编码的「始终用中文」换成 {global_constraint}，运行时按请求语言注入。
     */
    private void migrateScenePromptLanguagePlaceholder(Statement stmt) {
        try {
            int n = stmt.executeUpdate("""
                    UPDATE `sys_ai_scene_prompt`
                    SET `system_prompt` = REPLACE(
                      `system_prompt`,
                      '【重要全局规则】请始终使用中文回复用户。无论用户输入何种语言，所有释义、例句翻译、解析说明都必须用中文。英文原文和音标除外。',
                      '{global_constraint}'
                    ),
                    `update_time` = NOW()
                    WHERE `is_deleted` = 0
                      AND `system_prompt` LIKE '%【重要全局规则】请始终使用中文回复用户%'
                    """);
            if (n > 0) {
                log.info("数据库迁移完成: sys_ai_scene_prompt 已将硬编码中文规则替换为 {{global_constraint}}，共 {} 行", n);
            }
        } catch (Exception e) {
            log.warn("迁移 sys_ai_scene_prompt 语言占位符失败: {}", e.getMessage());
        }
    }

    /** reading_chat 旧种子「请用中文回答」→「请用{language}回答」 */
    private void migrateReadingChatLanguagePlaceholder(Statement stmt) {
        try {
            int n = stmt.executeUpdate("""
                    UPDATE `sys_ai_scene_prompt`
                    SET `system_prompt` = REPLACE(
                      `system_prompt`,
                      '请用中文回答用户的问题',
                      '请用{language}回答用户的问题'
                    ),
                    `update_time` = NOW()
                    WHERE `is_deleted` = 0
                      AND `scene_key` = 'reading_chat'
                      AND `system_prompt` LIKE '%请用中文回答用户的问题%'
                    """);
            if (n > 0) {
                log.info("数据库迁移完成: reading_chat 已改为 {{language}}，共 {} 行", n);
            }
        } catch (Exception e) {
            log.warn("迁移 reading_chat 语言占位符失败: {}", e.getMessage());
        }
    }

    /**
     * 功能细节 prompt 里硬编码「中文释义」会盖过非中文 locale 的全局语言规则；
     * 改为「用户语言」中性表述（运行时 AiPromptBuilder 还会再按 locale 替换残留「中文」）。
     */
    private void migrateFeaturePromptLanguageNeutral(Connection conn, Statement stmt) {
        try {
            var tables = conn.getMetaData().getTables(null, null, "sys_ai_feature", new String[]{"TABLE"});
            if (!tables.next()) {
                tables.close();
                return;
            }
            tables.close();
            int n1 = stmt.executeUpdate("""
                    UPDATE sys_ai_feature
                    SET prompt = REPLACE(prompt, '中文释义', '用户语言释义'),
                        update_time = NOW()
                    WHERE is_deleted = 0 AND prompt LIKE '%中文释义%'
                    """);
            int n2 = stmt.executeUpdate("""
                    UPDATE sys_ai_feature
                    SET prompt = REPLACE(prompt, 'n./v./adj. + 中文', 'n./v./adj. + 用户语言'),
                        update_time = NOW()
                    WHERE is_deleted = 0 AND prompt LIKE '%n./v./adj. + 中文%'
                    """);
            if (n1 > 0 || n2 > 0) {
                log.info("数据库迁移完成: sys_ai_feature.prompt 语言中性化 n1={}, n2={}", n1, n2);
            }
        } catch (Exception e) {
            log.warn("迁移 sys_ai_feature 语言中性化失败: {}", e.getMessage());
        }
    }

    /**
     * 旧 word_parse 种子未写死 JSON 字段名，模型会返回 word/cn_definition 等导致前端表格空白。
     */
    private void migrateWordParsePromptSchema(Connection conn) {
        try {
            String prompt = """
                    {global_constraint}
                    你是一个专业的英语词典助手。用户会给你一个英文单词/短语列表（JSON数组），请为每个词条补全以下信息，并以严格的 JSON 数组格式返回。

                    返回格式要求：
                    [
                      {
                        "wordProto": "原词",
                        "usPhonetic": "美式音标，如 /ˈeksəmpəl/",
                        "ukPhonetic": "英式音标，如 /ɪɡˈzɑːmpəl/",
                        "partSpeechMean": "词性+中文释义，如 n. 例子；v. 举例",
                        "sentenceExample": "为每个词性各提供一个例句，格式：词性标记. 英文例句 中文翻译。多个词性用换行符\\\\n分隔",
                        "rootAffix": "词根词缀解析；短语可写「短语，无词根词缀」"
                      }
                    ]

                    要求：
                    1. 必须返回完整的 JSON 数组，不要有任何额外的解释文字
                    2. 字段名必须严格使用上述 camelCase（wordProto/usPhonetic/ukPhonetic/partSpeechMean/sentenceExample/rootAffix），禁止使用 word、phonetic、cn_definition 等其它字段名
                    3. 字段不能为空，如果确实没有信息，写\\"暂无\\"
                    4. 音标使用 IPA 国际音标
                    5. 对于短语，partSpeechMean 标注为「短语」
                    """;
            try (var ps = conn.prepareStatement(
                    "UPDATE sys_ai_scene_prompt SET system_prompt = ?, remark = ?, update_time = NOW() " +
                            "WHERE scene_key = 'word_parse' AND is_deleted = 0 AND system_prompt NOT LIKE '%wordProto%'")) {
                ps.setString(1, prompt);
                ps.setString(2, "字段必须与前端 AiParseResult 一致；占位符 {global_constraint}");
                int n = ps.executeUpdate();
                if (n > 0) {
                    log.info("数据库迁移完成: word_parse 提示词已补全 JSON 字段 schema，共 {} 行", n);
                }
            }
        } catch (Exception e) {
            log.warn("迁移 word_parse 提示词 schema 失败: {}", e.getMessage());
        }
    }

    /**
     * 为「单词」功能提示词补上英美音标要求（幂等：已含关键字则跳过）
     */
    private void ensureWordFeaturePhonetics(Connection conn, Statement stmt) {
        try {
            var tables = conn.getMetaData().getTables(null, null, "sys_ai_feature", new String[]{"TABLE"});
            if (!tables.next()) {
                tables.close();
                return;
            }
            tables.close();
            String addon = """

                    【音标要求】讲解每个单词时必须同时给出美式与英式 IPA 音标，禁止省略。推荐格式：- **route**: 美 /ruːt/，英 /ruːt/；n. 路线。
                    """;
            try (var ps = conn.prepareStatement(
                    "UPDATE sys_ai_feature SET prompt = CONCAT(IFNULL(prompt,''), ?), update_time = NOW() " +
                            "WHERE feature_key = 'word' AND is_deleted = 0 " +
                            "AND (prompt IS NULL OR (prompt NOT LIKE '%美式音标%' AND prompt NOT LIKE '%美 /%' AND prompt NOT LIKE '%US /%'))")) {
                ps.setString(1, addon);
                int n = ps.executeUpdate();
                if (n > 0) {
                    log.info("数据库迁移完成: sys_ai_feature.word 已追加英美音标要求，共 {} 行", n);
                }
            }
        } catch (Exception e) {
            log.warn("迁移 sys_ai_feature 音标要求失败: {}", e.getMessage());
        }
    }

    /**
     * 将「单词」功能提示词中的冗长释义示例改为 n./v./adj. 简洁格式（幂等）
     */
    private void migrateWordFeatureConciseGloss(Connection conn, Statement stmt) {
        try {
            var tables = conn.getMetaData().getTables(null, null, "sys_ai_feature", new String[]{"TABLE"});
            if (!tables.next()) {
                tables.close();
                return;
            }
            tables.close();
            // 替换历史冗长示例句
            int replaced = stmt.executeUpdate("""
                    UPDATE sys_ai_feature
                    SET prompt = REPLACE(
                          REPLACE(
                            REPLACE(prompt,
                              '名词，意为“路线”。',
                              'n. 路线。'),
                            '名词，意为“路线，道路”。',
                            'n. 路线，道路。'),
                          '；名词，意为“路线”。',
                          '；n. 路线。'),
                        update_time = NOW()
                    WHERE feature_key = 'word' AND is_deleted = 0
                      AND prompt LIKE '%名词，意为%'
                    """);
                    String conciseRule = """

                    【释义格式】每个单词用「英文词性缩写 + 用户语言释义」，如 n. 律师；v. 建议；adj. 地下的。禁止写「名词，意为“…」」「动词，意为“…」。
                    """;
            try (var ps = conn.prepareStatement(
                    "UPDATE sys_ai_feature SET prompt = CONCAT(IFNULL(prompt,''), ?), update_time = NOW() " +
                            "WHERE feature_key = 'word' AND is_deleted = 0 " +
                            "AND (prompt IS NULL OR prompt NOT LIKE '%释义格式%')")) {
                ps.setString(1, conciseRule);
                int appended = ps.executeUpdate();
                if (replaced > 0 || appended > 0) {
                    log.info("数据库迁移完成: sys_ai_feature.word 释义简洁化 replace={}, append={}", replaced, appended);
                }
            }
        } catch (Exception e) {
            log.warn("迁移 sys_ai_feature 释义格式失败: {}", e.getMessage());
        }
    }

    /**
     * 已废弃：旧版曾追加 translation/tense/entries 等硬编码字段说明。
     * 契约改由 {@link #migrateFeatureKeyBasedJsonContract} + 后台配置维护，此处不再写入。
     */
    private void migrateFeaturePromptWordMeaningJson(Connection conn, Statement stmt) {
        // no-op
    }

    /**
     * 单词功能提示词：纯字段约定，无版本标（遇到旧版/否定套话则覆盖）。
     */
    private void migrateWordFeaturePromptV3(Connection conn, Statement stmt) {
        try {
            var tables = conn.getMetaData().getTables(null, null, "sys_ai_feature", new String[]{"TABLE"});
            if (!tables.next()) {
                tables.close();
                return;
            }
            tables.close();
            String prompt = """
                    usPhonetic/ukPhonetic=美英IPA；meaning=n./v./adj.等+释义；rootAffix=词根词缀（可空）。
                    entries[]：{"entryType":"word","word":"原形","meaning":"n. 释义","usPhonetic":"/…/","ukPhonetic":"/…/","rootAffix":""}
                    """;
            try (var ps = conn.prepareStatement(
                    "UPDATE sys_ai_feature SET prompt = ?, update_time = NOW() "
                            + "WHERE feature_key = 'word' AND is_deleted = 0 "
                            + "AND (prompt IS NULL OR prompt LIKE '%【word·%' OR prompt LIKE '%跳过专有%' "
                            + "OR prompt LIKE '%禁止%' OR prompt LIKE '%重难点%' OR prompt LIKE '%【JSON·word】%')")) {
                ps.setString(1, prompt.trim());
                int n = ps.executeUpdate();
                if (n > 0) {
                    log.info("数据库迁移完成: sys_ai_feature.word 提示词已精简，共 {} 行", n);
                }
            }
        } catch (Exception e) {
            log.warn("迁移 sys_ai_feature.word 提示词失败: {}", e.getMessage());
        }
    }

    /**
     * 语法 / 句式提示词拆分并精简（无版本标）。
     */
    private void migrateGrammarSentencePatternSplit(Connection conn, Statement stmt) {
        try {
            var tables = conn.getMetaData().getTables(null, null, "sys_ai_feature", new String[]{"TABLE"});
            if (!tables.next()) {
                tables.close();
                return;
            }
            tables.close();

            String grammarPrompt = """
                    在 JSON 顶层键 "grammar" 填写语法分析（字段内用 \\n 换行），可含时态/语态/其它语法点。
                    例：{"grammar":"时间状语 in the nineteenth century → 一般过去时\\n谓语：tend → tended\\n语态：主动\\n主谓结构\\n主语：…\\n谓语：…"}
                    """;
            String sentencePrompt = """
                    在 JSON 顶层键 "sentence_pattern" 按三行填写：句式 / 句子类型 / 功能。
                    例：{"sentence_pattern":"句式：主谓基础陈述句\\n句子类型：简单句\\n功能：陈述客观趋势"}
                    """;

            int nGrammar = 0;
            int nSentence = 0;
            try (var ps = conn.prepareStatement(
                    "UPDATE sys_ai_feature SET prompt = ?, update_time = NOW() "
                            + "WHERE feature_key = 'grammar' AND is_deleted = 0")) {
                ps.setString(1, grammarPrompt.trim());
                nGrammar = ps.executeUpdate();
            }
            try (var ps = conn.prepareStatement(
                    "UPDATE sys_ai_feature SET prompt = ?, update_time = NOW() "
                            + "WHERE feature_key = 'sentence_pattern' AND is_deleted = 0")) {
                ps.setString(1, sentencePrompt.trim());
                nSentence = ps.executeUpdate();
            }
            if (nGrammar > 0 || nSentence > 0) {
                log.info("数据库迁移完成: grammar/sentence_pattern 提示词已精简 grammar={}, sentence={}",
                        nGrammar, nSentence);
            }
        } catch (Exception e) {
            log.warn("迁移 grammar/sentence_pattern 失败: {}", e.getMessage());
        }
    }

    /**
     * 阅读解析：按 feature_key 分包返回 JSON；场景壳与各功能 prompt 写入约定（幂等 marker）。
     */
    private void migrateFeatureKeyBasedJsonContract(Connection conn, Statement stmt) {
        try {
            var tables = conn.getMetaData().getTables(null, null, "sys_ai_scene_prompt", new String[]{"TABLE"});
            if (!tables.next()) {
                tables.close();
                return;
            }
            tables.close();

            String scenePrompt = """
                    {global_constraint}
                    你是英语阅读解析助手。
                    {feature_prompts}
                    用户选中原文："{context}"
                    若有目标词：{target_words}

                    【输出】只返回一个 JSON 对象，不要 Markdown。
                    顶层键必须与上方勾选功能的 feature_key 完全一致；未勾选的键不要写。
                    文本类功能：值为字符串。
                    词条类功能：值为对象数组（元素含 word、meaning 等字段，具体以各功能 prompt 为准）。
                    """;

            try (var ps = conn.prepareStatement(
                    "UPDATE sys_ai_scene_prompt SET system_prompt = ?, remark = ?, update_time = NOW() "
                            + "WHERE scene_key = 'feature' AND locale = 'zh-CN' AND is_deleted = 0 "
                            + "AND (system_prompt IS NULL OR system_prompt NOT LIKE '%顶层键必须与上方勾选功能的 feature_key%')")) {
                ps.setString(1, scenePrompt.trim());
                ps.setString(2, "占位符 {global_constraint}/{language}/{feature_prompts}/{context}/{target_words}；按 feature_key 分包 JSON");
                int n = ps.executeUpdate();
                if (n > 0) log.info("数据库迁移完成: feature 场景壳已改为 Key 分包 JSON，共 {} 行", n);
            }

            record Feat(String key, String prompt) {}
            // 干净约定：无目标词时接口层根本不下发 word/root_affix，提示词不解释「空」
            List<Feat> feats = List.of(
                    new Feat("meaning", "在 JSON 键 \"meaning\" 写整句/段落译文（字符串）。例：{\"meaning\":\"…\"}"),
                    new Feat("word", "在 JSON 键 \"word\" 写词条数组。仅解析场景中的目标词。元素：word, meaning, usPhonetic, ukPhonetic, rootAffix(可空)。例：{\"word\":[{\"word\":\"reviewer\",\"meaning\":\"n. 评论家\",\"usPhonetic\":\"/…/\",\"ukPhonetic\":\"/…/\",\"rootAffix\":\"re- + view + -er\"}]}"),
                    new Feat("collocation", "在 JSON 键 \"collocation\" 写固定搭配数组。元素：word, meaning。例：{\"collocation\":[{\"word\":\"examples of\",\"meaning\":\"…的例子\"}]}"),
                    new Feat("phonetic", "在 JSON 键 \"phonetic\" 写连读/吞音/重读等发音说明（字符串，非整词释义音标）。例：{\"phonetic\":\"…\"}"),
                    new Feat("root_affix", "在 JSON 键 \"root_affix\" 写词条数组，含 rootAffix。仅解析场景中的目标词。例：{\"root_affix\":[{\"word\":\"…\",\"meaning\":\"…\",\"rootAffix\":\"…\"}]}"),
                    new Feat("example", "在 JSON 键 \"example\" 写例句/用法数组。例：{\"example\":[{\"word\":\"…\",\"meaning\":\"例句与说明\"}]}")
            );
            // 清掉此前「无目标词则 [] / 学习短语块」等污染文案（可重复执行）
            String forceSql = "UPDATE sys_ai_feature SET prompt = ?, update_time = NOW() "
                    + "WHERE feature_key = ? AND is_deleted = 0 "
                    + "AND (prompt LIKE '%无目标词则%' OR prompt LIKE '%学习短语块%' "
                    + "OR prompt LIKE '%不依赖目标词%' OR prompt LIKE '%必须从选中原文提取%' "
                    + "OR prompt LIKE '%禁止从原文自行抽词%')";
            int forced = 0;
            try (var ps = conn.prepareStatement(forceSql)) {
                for (Feat f : feats) {
                    if (!"word".equals(f.key()) && !"root_affix".equals(f.key()) && !"collocation".equals(f.key())) {
                        continue;
                    }
                    ps.setString(1, f.prompt());
                    ps.setString(2, f.key());
                    forced += ps.executeUpdate();
                }
            }
            String sql = "UPDATE sys_ai_feature SET prompt = ?, update_time = NOW() "
                    + "WHERE feature_key = ? AND is_deleted = 0 "
                    + "AND (prompt IS NULL OR prompt NOT LIKE ?)";
            int total = forced;
            try (var ps = conn.prepareStatement(sql)) {
                for (Feat f : feats) {
                    ps.setString(1, f.prompt());
                    ps.setString(2, f.key());
                    ps.setString(3, "%JSON 键 \"" + f.key() + "\"%");
                    total += ps.executeUpdate();
                }
            }
            if (total > 0) {
                log.info("数据库迁移完成: sys_ai_feature Key 分包 prompt 已更新，共 {} 行", total);
            }
        } catch (Exception e) {
            log.warn("迁移 Key 分包 JSON 约定失败: {}", e.getMessage());
        }
    }

    private void ensureWritingEssayTable(Connection conn, Statement stmt) throws Exception {
        var tables = conn.getMetaData().getTables(null, null, "wd_writing_essay", new String[]{"TABLE"});
        boolean exists = tables.next();
        tables.close();
        if (exists) {
            return;
        }
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS `wd_writing_essay` (
                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                  `user_id` bigint NOT NULL COMMENT '用户ID',
                  `title` varchar(120) NOT NULL DEFAULT '' COMMENT '用户自命名',
                  `task_type` varchar(16) NOT NULL COMMENT 'task1|task2',
                  `prompt_text` mediumtext NOT NULL COMMENT '题目',
                  `prompt_image_key` varchar(512) DEFAULT NULL COMMENT '题目截图COS key',
                  `body_text` mediumtext COMMENT '作文正文',
                  `status` varchar(16) NOT NULL DEFAULT 'draft' COMMENT 'draft|graded',
                  `word_count` int NOT NULL DEFAULT 0 COMMENT '英文词数',
                  `overall_band` decimal(3,1) DEFAULT NULL COMMENT '总分0-9',
                  `score_json` mediumtext COMMENT '评分详情JSON',
                  `polished_essay` mediumtext COMMENT 'AI写作',
                  `model_essay` mediumtext COMMENT '满分示范(兼容)',
                  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  `is_deleted` tinyint NOT NULL DEFAULT 0,
                  PRIMARY KEY (`id`),
                  KEY `idx_user_update` (`user_id`, `update_time`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='雅思写作练习稿'
                """);
        log.info("数据库表初始化完成: wd_writing_essay");
    }

    private void ensureWritingEssayGradeTable(Connection conn, Statement stmt) throws Exception {
        var tables = conn.getMetaData().getTables(null, null, "wd_writing_essay_grade", new String[]{"TABLE"});
        boolean exists = tables.next();
        tables.close();
        if (exists) {
            return;
        }
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS `wd_writing_essay_grade` (
                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                  `essay_id` bigint NOT NULL COMMENT '作文ID',
                  `user_id` bigint NOT NULL COMMENT '用户ID',
                  `body_text` mediumtext COMMENT '批改当时正文',
                  `word_count` int NOT NULL DEFAULT 0 COMMENT '词数',
                  `overall_band` decimal(3,1) DEFAULT NULL COMMENT '总分0-9',
                  `score_json` mediumtext COMMENT '评分详情JSON',
                  `polished_essay` mediumtext COMMENT 'AI润色',
                  `model_essay` mediumtext COMMENT '示范范文',
                  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  `is_deleted` tinyint NOT NULL DEFAULT 0,
                  PRIMARY KEY (`id`),
                  KEY `idx_essay_id` (`essay_id`, `id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='写作批改历史'
                """);
        log.info("数据库表初始化完成: wd_writing_essay_grade");
    }

    /** 已批改但无历史行的作文，灌入一条当前结果，供下拉回看 */
    private void seedWritingGradeHistoryFromExisting(Connection conn) {
        try {
            var essayTables = conn.getMetaData().getTables(null, null, "wd_writing_essay", new String[]{"TABLE"});
            if (!essayTables.next()) {
                essayTables.close();
                return;
            }
            essayTables.close();
            var gradeTables = conn.getMetaData().getTables(null, null, "wd_writing_essay_grade", new String[]{"TABLE"});
            if (!gradeTables.next()) {
                gradeTables.close();
                return;
            }
            gradeTables.close();
            int n = conn.createStatement().executeUpdate("""
                    INSERT INTO wd_writing_essay_grade(
                      essay_id, user_id, body_text, word_count, overall_band,
                      score_json, polished_essay, model_essay, create_time
                    )
                    SELECT e.id, e.user_id, e.body_text, e.word_count, e.overall_band,
                           e.score_json, e.polished_essay, e.model_essay,
                           COALESCE(e.update_time, e.create_time, NOW())
                    FROM wd_writing_essay e
                    WHERE e.is_deleted = 0
                      AND e.status = 'graded'
                      AND e.score_json IS NOT NULL
                      AND e.score_json <> ''
                      AND NOT EXISTS (
                        SELECT 1 FROM wd_writing_essay_grade g
                        WHERE g.essay_id = e.id AND g.is_deleted = 0
                      )
                    """);
            if (n > 0) {
                log.info("数据库种子初始化完成: 写作批改历史已从存量灌入 {} 条", n);
            }
        } catch (Exception e) {
            log.warn("灌入写作批改历史失败: {}", e.getMessage());
        }
    }

    /** 已有库写入 writing_grade 场景壳（仅缺省时插入，不覆盖后台已改文案） */
    private void seedWritingGradePromptIfMissing(Connection conn) {
        try {
            var tables = conn.getMetaData().getTables(null, null, "sys_ai_scene_prompt", new String[]{"TABLE"});
            if (!tables.next()) {
                tables.close();
                return;
            }
            tables.close();
            String prompt = """
                    {global_constraint}
                    You are an IELTS Academic Writing examiner and coach for {task_type}.
                    Grade the essay; produce polishedEssay (keep student's ideas) and modelEssay (high-band sample for the same question).
                    Return ONLY one JSON object with keys:
                    overallBand, taskAchievement or taskResponse, coherence, lexical, grammar,
                    summary, priorities (3-5 strings), annotations ([{excerpt,issue,fix}]),
                    polishedEssay, modelEssay.
                    Bands use 0-9 with 0.5 steps. Be specific and actionable. Reply language follows {language}.
                    """;
            try (var ps = conn.prepareStatement("""
                    INSERT INTO sys_ai_scene_prompt
                    (scene_key, locale, scene_name, system_prompt, remark, status, sort_num, create_time, update_time)
                    SELECT 'writing_grade', 'zh-CN', '雅思写作批改', ?, ?, 1, 4, NOW(), NOW()
                    FROM DUAL
                    WHERE NOT EXISTS (
                      SELECT 1 FROM sys_ai_scene_prompt
                      WHERE scene_key = 'writing_grade' AND locale = 'zh-CN' AND is_deleted = 0
                    )
                    """)) {
                ps.setString(1, prompt.trim());
                ps.setString(2, "占位符 {global_constraint}/{language}/{task_type}");
                int n = ps.executeUpdate();
                if (n > 0) {
                    log.info("数据库种子初始化完成: writing_grade 场景提示词已写入");
                }
            }
        } catch (Exception e) {
            log.warn("写入 writing_grade 场景提示词失败: {}", e.getMessage());
        }
    }
}
