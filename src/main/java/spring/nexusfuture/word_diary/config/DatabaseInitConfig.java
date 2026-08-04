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
            ensureColumn(conn, stmt, "wd_book", "resource_id",
                    "ALTER TABLE wd_book ADD COLUMN resource_id bigint DEFAULT NULL COMMENT '共享资源ID' AFTER file_path");
            ensureBookSharingTables(conn, stmt);

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
            migrateWordFeaturePosMeaningContract(conn, stmt);
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
            ensureTranslationPracticeTable(conn, stmt);
            ensureTranslationGradeTable(conn, stmt);
            // 已下线「文章专练」：列保留仅用于过滤；历史数据软删
            ensureColumn(conn, stmt, "wd_translation_practice", "article_id",
                    "ALTER TABLE wd_translation_practice ADD COLUMN article_id bigint DEFAULT NULL COMMENT '已废弃：文章专练ID' AFTER user_id");
            try {
                stmt.execute("UPDATE wd_translation_practice SET is_deleted = 1, update_time = NOW() "
                        + "WHERE article_id IS NOT NULL AND is_deleted = 0");
            } catch (Exception e) {
                log.debug("清理文章专练练习稿跳过: {}", e.getMessage());
            }
            try {
                stmt.execute("UPDATE wd_passage_article SET is_deleted = 1, update_time = NOW() WHERE is_deleted = 0");
            } catch (Exception e) {
                log.debug("清理文章专练表跳过: {}", e.getMessage());
            }
            ensureShadowingClipTable(conn, stmt);
            ensureSentencePatternTable(conn, stmt);
            ensureUserSentencePatternStatTable(conn, stmt);
            ensureSentencePatternGuessTable(conn, stmt);
            ensureStudyDurationTable(conn, stmt);
            ensureStudyDurationRefNullable(conn, stmt);
            seedStudyIdleTimeoutConfig(conn);
            seedStudyMinValidSecondsConfig(conn);
            ensureColumn(conn, stmt, "wd_translation_grade", "direction",
                    "ALTER TABLE wd_translation_grade ADD COLUMN direction varchar(16) DEFAULT NULL COMMENT '批改当时方向 en2zh|zh2en' AFTER user_id");
            ensureColumn(conn, stmt, "wd_translation_grade", "prompt_text",
                    "ALTER TABLE wd_translation_grade ADD COLUMN prompt_text mediumtext COMMENT '批改当时题目' AFTER direction");
            ensureColumn(conn, stmt, "wd_translation_grade", "source_words_json",
                    "ALTER TABLE wd_translation_grade ADD COLUMN source_words_json mediumtext COMMENT '批改当时目标词JSON' AFTER prompt_text");
            ensureColumn(conn, stmt, "wd_ai_session", "grade_id",
                    "ALTER TABLE wd_ai_session ADD COLUMN grade_id bigint DEFAULT NULL COMMENT '翻译批改记录ID，按练习记录隔离答疑' AFTER book_id");
            try {
                if (!indexExists(conn, "wd_ai_session", "idx_user_grade")) {
                    stmt.execute("CREATE INDEX idx_user_grade ON wd_ai_session(user_id, grade_id)");
                    log.info("数据库索引初始化完成: wd_ai_session.idx_user_grade");
                }
            } catch (Exception e) {
                log.warn("创建 wd_ai_session.idx_user_grade 失败（可忽略）: {}", e.getMessage());
            }
            seedTranslationScenePromptsIfMissing(conn);
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
            ensureColumn(conn, stmt, "sys_ai_model", "billing_unit",
                    "ALTER TABLE sys_ai_model ADD COLUMN billing_unit varchar(16) NOT NULL DEFAULT 'token' COMMENT '计费单位 token|second' AFTER provider");

            // wd_ai_call_log: 区分 reasoning tokens（思考开销）与 content tokens（用户可见内容）
            ensureColumn(conn, stmt, "wd_ai_call_log", "reasoning_tokens",
                    "ALTER TABLE wd_ai_call_log ADD COLUMN reasoning_tokens int NOT NULL DEFAULT 0 COMMENT '思考token数量（reasoning tokens，仅推理模型有）' AFTER completion_tokens");
            ensureColumn(conn, stmt, "wd_ai_call_log", "content_tokens",
                    "ALTER TABLE wd_ai_call_log ADD COLUMN content_tokens int NOT NULL DEFAULT 0 COMMENT '可见内容token数量（= completionTokens - reasoningTokens，给用户看的实际内容）' AFTER reasoning_tokens");

            // 关闭所有场景绑定的思考模式，避免产生不必要的 reasoning tokens 开销
            try {
                int updated = stmt.executeUpdate(
                        "UPDATE sys_ai_scene SET thinking_mode = 'disabled', reasoning_effort = NULL, thinking_budget = NULL WHERE is_deleted = 0");
                log.info("已将 {} 条场景绑定的思考模式设为 disabled", updated);
            } catch (Exception e) {
                log.warn("关闭场景思考模式失败: {}", e.getMessage());
            }

            seedWordFeatureI18nIfMissing(conn);
            seedParaformerBilling(conn);
        } catch (Exception e) {
            log.warn("数据库字段初始化警告: {}", e.getMessage());
        }

        // 独立执行：水平标尺
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ensureProficiencyTables(conn, stmt);
            seedProficiencyActiveScaleConfig(conn);
            seedProficiencyScalesAndLevels(conn, stmt);
        } catch (Exception e) {
            log.warn("水平标尺表初始化警告: {}", e.getMessage(), e);
        }

        // 独立执行：词性运用统计表
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ensureWordPosStatTable(stmt);
            ensureColumn(conn, stmt, "wd_word_pos_stat", "meaning",
                    "ALTER TABLE wd_word_pos_stat ADD COLUMN meaning varchar(512) DEFAULT NULL COMMENT '该词性对应释义' AFTER pos");
            ensureColumn(conn, stmt, "wd_word_pos_stat", "spell_practice_count",
                    "ALTER TABLE wd_word_pos_stat ADD COLUMN spell_practice_count int NOT NULL DEFAULT 0 COMMENT '拼写练写次数' AFTER correct_count");
            ensureColumn(conn, stmt, "wd_word_pos_stat", "spell_correct_count",
                    "ALTER TABLE wd_word_pos_stat ADD COLUMN spell_correct_count int NOT NULL DEFAULT 0 COMMENT '拼写正确次数' AFTER spell_practice_count");
            ensureColumn(conn, stmt, "wd_word_pos_stat", "spell_mastered",
                    "ALTER TABLE wd_word_pos_stat ADD COLUMN spell_mastered tinyint NOT NULL DEFAULT 0 COMMENT '书写是否掌握 0否1是' AFTER spell_correct_count");
            ensureColumn(conn, stmt, "wd_word_pos_stat", "spell_status",
                    "ALTER TABLE wd_word_pos_stat ADD COLUMN spell_status tinyint NOT NULL DEFAULT 0 COMMENT '拼写阶梯0未练1~3复习4熟记' AFTER spell_correct_count");
            ensureColumn(conn, stmt, "wd_word_pos_stat", "spell_status_data",
                    "ALTER TABLE wd_word_pos_stat ADD COLUMN spell_status_data datetime DEFAULT NULL COMMENT '进入当前spell_status的学习日' AFTER spell_status");
            migrateSpellStatusFromMastered(stmt);
            shortenSpellStudyStatuses(stmt);
            alignSpellStudyStatuses(conn, stmt);
            alignWordStudyStatuses(conn, stmt);
        } catch (Exception e) {
            log.warn("wd_word_pos_stat 初始化警告: {}", e.getMessage(), e);
        }

        // 独立执行：避免被上面其它迁移异常短路，导致 region 列一直缺失
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            migrateAiSceneRegion(conn, stmt);
            migrateAiSceneCallParams(stmt);
            seedAiScenes(stmt);
        } catch (Exception e) {
            log.warn("sys_ai_scene.region 初始化警告: {}", e.getMessage());
        }

        // 语录表
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ensureQuoteTable(conn, stmt);
        } catch (Exception e) {
            log.warn("sys_quote 初始化警告: {}", e.getMessage());
        }

        // 自学小 Tips 表
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ensureStudyTipTable(conn, stmt);
        } catch (Exception e) {
            log.warn("sys_study_tip 初始化警告: {}", e.getMessage());
        }
    }

    /** 场景级 Chat 参数：temperature / thinking 等 */
    private void migrateAiSceneCallParams(Statement stmt) {
        try {
            addColumnIgnoreDuplicate(stmt,
                    "ALTER TABLE sys_ai_scene ADD COLUMN temperature decimal(4,2) DEFAULT NULL COMMENT '采样温度，空=供应商默认' AFTER model_key");
            addColumnIgnoreDuplicate(stmt,
                    "ALTER TABLE sys_ai_scene ADD COLUMN max_tokens int DEFAULT NULL COMMENT '最大生成token，空=默认' AFTER temperature");
            addColumnIgnoreDuplicate(stmt,
                    "ALTER TABLE sys_ai_scene ADD COLUMN thinking_mode varchar(16) DEFAULT NULL COMMENT '思考模式 enabled|disabled|空=默认' AFTER max_tokens");
            addColumnIgnoreDuplicate(stmt,
                    "ALTER TABLE sys_ai_scene ADD COLUMN thinking_budget int DEFAULT NULL COMMENT '思考token预算' AFTER thinking_mode");
            addColumnIgnoreDuplicate(stmt,
                    "ALTER TABLE sys_ai_scene ADD COLUMN reasoning_effort varchar(16) DEFAULT NULL COMMENT 'reasoning_effort low|medium|high' AFTER thinking_budget");
            addColumnIgnoreDuplicate(stmt,
                    "ALTER TABLE sys_ai_scene ADD COLUMN response_format varchar(32) DEFAULT NULL COMMENT 'response_format json_object|text|空=不传' AFTER reasoning_effort");
            log.info("sys_ai_scene 调用参数列迁移完成");
        } catch (Exception e) {
            log.warn("迁移 sys_ai_scene 调用参数失败: {}", e.getMessage(), e);
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

    /** paraformer-v2：按秒计费；输入价字段存「元/秒」 */
    private void seedParaformerBilling(Connection conn) {
        try (var ps = conn.prepareStatement("""
                UPDATE sys_ai_model
                SET billing_unit = 'second',
                    input_price_per_million = CASE
                        WHEN input_price_per_million IS NULL OR input_price_per_million = 0
                        THEN 0.00008 ELSE input_price_per_million END,
                    output_price_per_million = 0,
                    remark = CASE
                        WHEN remark IS NULL OR remark = ''
                        THEN '跟读 ASR；input 价=元/秒（有效语音）'
                        ELSE remark END,
                    update_time = NOW()
                WHERE model_key = 'paraformer-v2' AND is_deleted = 0
                """)) {
            int n = ps.executeUpdate();
            if (n > 0) {
                log.info("数据库迁移完成: paraformer-v2 计费单位 second，共 {} 行", n);
            }
        } catch (Exception e) {
            log.warn("补齐 paraformer-v2 计费单位失败: {}", e.getMessage());
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
                    INSERT IGNORE INTO sys_ai_model
                    (model_key, display_name, provider, billing_unit, currency,
                     input_price_per_million, output_price_per_million, sort_num, is_enabled, remark)
                    VALUES
                    ('paraformer-v2', 'Paraformer V2（录音转写）', 'aliyun', 'second', 'CNY', 0.00008, 0, 10, 1,
                     '跟读 ASR；input 价=元/秒（有效语音）')
                    """);
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
                    ('translation_generate', '翻译练习出题', 'cn', 'deepseek-v4-flash', 5, 1, '基于已学词出翻译题'),
                    ('translation_generate', '翻译练习出题', 'global', 'deepseek-v4-flash', 5, 1, '海外线路'),
                    ('translation_grade', '翻译练习批改', 'cn', 'deepseek-v4-flash', 6, 1, '翻译评分+参考译文'),
                    ('translation_grade', '翻译练习批改', 'global', 'deepseek-v4-flash', 6, 1, '海外线路'),
                    ('translation_chat', '翻译练习答疑', 'cn', 'deepseek-v4-flash', 7, 1, '针对当前批改记录的 Aiko 答疑'),
                    ('translation_chat', '翻译练习答疑', 'global', 'deepseek-v4-flash', 7, 1, '海外线路'),
                    ('shadowing_asr', '跟读台词转写', 'cn', 'paraformer-v2', 8, 1, '跟读音频 ASR，须绑定阿里云 Paraformer'),
                    ('shadowing_asr', '跟读台词转写', 'global', 'paraformer-v2', 8, 1, '海外线路，可改为同供应商其它 ASR 模型'),
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
                  `temperature` decimal(4,2) DEFAULT NULL,
                  `max_tokens` int DEFAULT NULL,
                  `thinking_mode` varchar(16) DEFAULT NULL,
                  `thinking_budget` int DEFAULT NULL,
                  `reasoning_effort` varchar(16) DEFAULT NULL,
                  `response_format` varchar(32) DEFAULT NULL,
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
                (model_key, display_name, provider, billing_unit, currency,
                 input_price_per_million, output_price_per_million, sort_num, is_enabled, remark)
                VALUES
                ('deepseek-v4-flash', 'DeepSeek V4 Flash', 'deepseek', 'token', 'CNY', 1.0, 2.0, 1, 1, ''),
                ('qwen-turbo', 'Qwen Turbo', 'aliyun', 'token', 'CNY', 0.3, 0.6, 2, 1, ''),
                ('paraformer-v2', 'Paraformer V2（录音转写）', 'aliyun', 'second', 'CNY', 0.00008, 0, 10, 1,
                 '跟读 ASR；input 价=元/秒（有效语音）')
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

    private void ensureBookSharingTables(Connection conn, Statement stmt) throws Exception {
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS `wd_book_resource` (
                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                  `owner_user_id` bigint NOT NULL COMMENT '资源创建者用户ID',
                  `title` varchar(256) NOT NULL COMMENT '书名',
                  `author` varchar(128) DEFAULT '' COMMENT '作者',
                  `cover_url` varchar(512) DEFAULT '' COMMENT '封面图URL',
                  `file_path` varchar(512) NOT NULL COMMENT '文件在服务器上的存储路径',
                  `file_type` varchar(16) NOT NULL DEFAULT 'epub' COMMENT '文件类型 epub/pdf/txt',
                  `file_size` bigint DEFAULT 0 COMMENT '文件大小(字节)',
                  `ref_count` int NOT NULL DEFAULT 1 COMMENT '引用计数',
                  `create_by` bigint DEFAULT NULL COMMENT '创建人',
                  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  `update_by` bigint DEFAULT NULL COMMENT '修改人',
                  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除 1已删除 0未删除',
                  PRIMARY KEY (`id`),
                  KEY `idx_owner_user_id` (`owner_user_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='共享书籍资源表'
                """);
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS `wd_book_share_code` (
                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                  `resource_id` bigint NOT NULL COMMENT '共享资源ID',
                  `owner_user_id` bigint NOT NULL COMMENT '发起分享的用户ID',
                  `share_code` varchar(32) NOT NULL COMMENT '分享码',
                  `expires_at` datetime NOT NULL COMMENT '过期时间',
                  `used_by` bigint DEFAULT NULL COMMENT '核销用户ID',
                  `used_time` datetime DEFAULT NULL COMMENT '核销时间',
                  `create_by` bigint DEFAULT NULL COMMENT '创建人',
                  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  `update_by` bigint DEFAULT NULL COMMENT '修改人',
                  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除 1已删除 0未删除',
                  PRIMARY KEY (`id`),
                  UNIQUE KEY `uk_share_code` (`share_code`),
                  KEY `idx_resource_id` (`resource_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='书籍分享码表'
                """);
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
                    "ALTER TABLE wd_word ADD COLUMN status tinyint NOT NULL DEFAULT 0 COMMENT '学习状态: 0未学 1~5复习阶梯 6熟记' AFTER user_id");
            ensureColumn(conn, stmt, "wd_word", "status_data",
                    "ALTER TABLE wd_word ADD COLUMN status_data datetime DEFAULT NULL COMMENT '进入当前status的时间' AFTER status");

            // 若仍有旧 SM-2 列，先回填 status_data / 映射旧 status（仅此路径做 3→6）
            var rs = conn.getMetaData().getColumns(null, null, "wd_word", "last_study_at");
            boolean hasLegacy = rs.next();
            rs.close();
            if (hasLegacy) {
                stmt.executeUpdate("""
                        UPDATE wd_word SET status_data = COALESCE(last_study_at, first_study_time, create_time, NOW())
                        WHERE status_data IS NULL
                        """);
                // 旧模型：3=熟记 → 新 6
                stmt.executeUpdate("UPDATE wd_word SET status = 6 WHERE status = 3");
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
                              WHEN COALESCE(p.learn_status, 0) = 3 THEN 6
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
        // 仅补缺：库中已有同 provider_key 时 IGNORE，不覆盖后台已配密钥
        stmt.executeUpdate("""
                INSERT IGNORE INTO sys_ai_provider
                (provider_key, display_name, base_url, api_key, sort_num, is_enabled, remark)
                VALUES
                ('deepseek', 'DeepSeek', 'https://api.deepseek.com', '', 1, 1, '密钥可由 yml 引导或后台填写'),
                ('aliyun', '阿里云百炼', 'https://dashscope.aliyuncs.com/compatible-mode', '', 2, 1, '密钥可由 yml 引导或后台填写')
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
                    new Feat("word", "在 JSON 键 \"word\" 写词条数组。仅解析场景中的目标词。元素：word, meaning, usPhonetic, ukPhonetic, rootAffix(可空)。【词性释义格式】meaning=英文词性缩写+\". \"+释义；多词性只用中文分号「；」分隔。允许：n/v/adj/adv/prep/conj/pron/num/int/phr。例：n. 评论家；v. 评论。禁止「名词，意为…」、禁止逗号/斜杠串联。短语用 phr. 释义。例：{\"word\":[{\"word\":\"reviewer\",\"meaning\":\"n. 评论家\",\"usPhonetic\":\"/…/\",\"ukPhonetic\":\"/…/\",\"rootAffix\":\"re- + view + -er\"}]}"),
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

    /**
     * 阅读「单词」功能：收紧 meaning 与 WordPosParser 一致（n. 释义；v. 释义）。
     * 幂等：已含【词性释义格式】则跳过。
     */
    private void migrateWordFeaturePosMeaningContract(Connection conn, Statement stmt) {
        try {
            var tables = conn.getMetaData().getTables(null, null, "sys_ai_feature", new String[]{"TABLE"});
            if (!tables.next()) {
                tables.close();
                return;
            }
            tables.close();
            String prompt = """
                    在 JSON 键 "word" 写词条数组。仅解析场景中的目标词。元素：word, meaning, usPhonetic, ukPhonetic, rootAffix(可空)。
                    【词性释义格式】meaning=英文词性缩写+". "+释义；多词性只用中文分号「；」分隔。允许：n/v/adj/adv/prep/conj/pron/num/int/phr。例：n. 评论家；v. 评论。禁止「名词，意为…」、禁止逗号/斜杠串联。短语用 phr. 释义。
                    例：{"word":[{"word":"reviewer","meaning":"n. 评论家","usPhonetic":"/…/","ukPhonetic":"/…/","rootAffix":"re- + view + -er"}]}
                    """;
            try (var ps = conn.prepareStatement(
                    "UPDATE sys_ai_feature SET prompt = ?, update_time = NOW() "
                            + "WHERE feature_key = 'word' AND is_deleted = 0 "
                            + "AND (prompt IS NULL OR prompt NOT LIKE '%【词性释义格式】%')")) {
                ps.setString(1, prompt.trim());
                int n = ps.executeUpdate();
                if (n > 0) {
                    log.info("数据库迁移完成: sys_ai_feature.word 词性释义格式收紧，共 {} 行", n);
                }
            }
        } catch (Exception e) {
            log.warn("迁移 sys_ai_feature.word 词性释义格式失败: {}", e.getMessage());
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

    /** 已有库写入 writing_grade 场景壳（仅缺省时插入；英文壳一次性改为中文） */
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
                    你是雅思学术写作（IELTS Academic Writing）考官兼写作教练，题型为 {task_type}。
                    请批改作文；产出 polishedEssay（保留学生原意的润色版）与 modelEssay（同题高分范文）。
                    只返回一个 JSON 对象，键包括：
                    overallBand, taskAchievement 或 taskResponse, coherence, lexical, grammar,
                    summary, priorities（3–5 条字符串）, annotations（[{excerpt,issue,fix}]）,
                    polishedEssay, modelEssay。
                    分数为 0–9，可 0.5 步进。评语须具体可执行。回复语言遵循 {language}。
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
            try (var ps = conn.prepareStatement("""
                    UPDATE sys_ai_scene_prompt
                    SET system_prompt = ?, remark = ?, update_time = NOW()
                    WHERE scene_key = 'writing_grade' AND is_deleted = 0
                      AND system_prompt LIKE '%You are an IELTS Academic Writing%'
                    """)) {
                ps.setString(1, prompt.trim());
                ps.setString(2, "占位符 {global_constraint}/{language}/{task_type}");
                int n = ps.executeUpdate();
                if (n > 0) {
                    log.info("数据库迁移完成: writing_grade 提示词已改为中文，共 {} 行", n);
                }
            }
        } catch (Exception e) {
            log.warn("写入 writing_grade 场景提示词失败: {}", e.getMessage());
        }
    }

    private void ensureTranslationPracticeTable(Connection conn, Statement stmt) throws Exception {
        var tables = conn.getMetaData().getTables(null, null, "wd_translation_practice", new String[]{"TABLE"});
        boolean exists = tables.next();
        tables.close();
        if (exists) {
            return;
        }
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS `wd_translation_practice` (
                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                  `user_id` bigint NOT NULL COMMENT '用户ID',
                  `article_id` bigint DEFAULT NULL COMMENT '已废弃：文章专练ID',
                  `title` varchar(120) NOT NULL DEFAULT '' COMMENT '用户自命名',
                  `direction` varchar(16) NOT NULL COMMENT 'en2zh|zh2en',
                  `pool_mode` varchar(16) NOT NULL DEFAULT 'learned' COMMENT 'learned|mastered',
                  `source_words_json` mediumtext COMMENT '目标词JSON',
                  `theme` varchar(120) DEFAULT NULL COMMENT '雅思主题',
                  `prompt_text` mediumtext NOT NULL COMMENT '题目',
                  `reference_answer` mediumtext COMMENT '参考译文',
                  `body_text` mediumtext COMMENT '用户译文',
                  `status` varchar(16) NOT NULL DEFAULT 'draft' COMMENT 'draft|graded',
                  `word_count` int NOT NULL DEFAULT 0 COMMENT '词数/字数',
                  `overall_band` decimal(3,1) DEFAULT NULL COMMENT '总分0-9',
                  `score_json` mediumtext COMMENT '评分详情JSON',
                  `polished_answer` mediumtext COMMENT 'AI润色译文',
                  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  `is_deleted` tinyint NOT NULL DEFAULT 0,
                  PRIMARY KEY (`id`),
                  KEY `idx_user_update` (`user_id`, `update_time`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='翻译练习稿'
                """);
        log.info("数据库表初始化完成: wd_translation_practice");
    }

    private void ensureTranslationGradeTable(Connection conn, Statement stmt) throws Exception {
        var tables = conn.getMetaData().getTables(null, null, "wd_translation_grade", new String[]{"TABLE"});
        boolean exists = tables.next();
        tables.close();
        if (exists) {
            return;
        }
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS `wd_translation_grade` (
                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                  `practice_id` bigint NOT NULL COMMENT '练习ID',
                  `user_id` bigint NOT NULL COMMENT '用户ID',
                  `direction` varchar(16) DEFAULT NULL COMMENT '批改当时方向 en2zh|zh2en',
                  `prompt_text` mediumtext COMMENT '批改当时题目',
                  `source_words_json` mediumtext COMMENT '批改当时目标词JSON',
                  `body_text` mediumtext COMMENT '批改当时译文',
                  `word_count` int NOT NULL DEFAULT 0 COMMENT '词数',
                  `overall_band` decimal(3,1) DEFAULT NULL COMMENT '总分0-9',
                  `score_json` mediumtext COMMENT '评分详情JSON',
                  `polished_answer` mediumtext COMMENT 'AI润色',
                  `reference_answer` mediumtext COMMENT '参考译文',
                  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  `is_deleted` tinyint NOT NULL DEFAULT 0,
                  PRIMARY KEY (`id`),
                  KEY `idx_practice_id` (`practice_id`, `id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='翻译批改历史'
                """);
        log.info("数据库表初始化完成: wd_translation_grade");
    }

    private void ensureShadowingClipTable(Connection conn, Statement stmt) throws Exception {
        var tables = conn.getMetaData().getTables(null, null, "wd_shadowing_clip", new String[]{"TABLE"});
        boolean exists = tables.next();
        tables.close();
        if (exists) {
            return;
        }
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS `wd_shadowing_clip` (
                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                  `user_id` bigint NOT NULL COMMENT '用户ID',
                  `title` varchar(120) NOT NULL DEFAULT '' COMMENT '用户自命名',
                  `audio_key` varchar(512) NOT NULL COMMENT '音频COS key',
                  `audio_mime` varchar(64) DEFAULT NULL COMMENT 'MIME',
                  `original_name` varchar(255) DEFAULT NULL COMMENT '原始文件名',
                  `file_size` bigint NOT NULL DEFAULT 0 COMMENT '字节',
                  `duration_ms` int NOT NULL DEFAULT 0 COMMENT '时长毫秒',
                  `asr_status` varchar(16) NOT NULL DEFAULT 'pending' COMMENT 'pending|processing|ready|failed',
                  `asr_error` varchar(512) DEFAULT NULL COMMENT '失败原因',
                  `transcript_json` mediumtext COMMENT '台词JSON [{text,startMs,endMs}]',
                  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  `is_deleted` tinyint NOT NULL DEFAULT 0,
                  PRIMARY KEY (`id`),
                  KEY `idx_user_update` (`user_id`, `update_time`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跟读音频篇（ASR台词）'
                """);
        log.info("数据库表初始化完成: wd_shadowing_clip");
    }

    /** 确保 sys_sentence_pattern 表存在 */
    private void ensureSentencePatternTable(Connection conn, Statement stmt) {
        try {
            var tables = conn.getMetaData().getTables(null, null, "sys_sentence_pattern", new String[]{"TABLE"});
            boolean exists = tables.next();
            tables.close();
            if (!exists) {
                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS `sys_sentence_pattern` (
                          `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                          `pattern_key` varchar(64) NOT NULL COMMENT '句式key（唯一，创建后不可改）',
                          `type` varchar(16) NOT NULL COMMENT '类型 written|spoken',
                          `level` varchar(8) NOT NULL COMMENT '等级 L1~L5',
                          `description` varchar(512) NOT NULL COMMENT '句式描述',
                          `example` varchar(512) DEFAULT NULL COMMENT '句式示例',
                          `status` tinyint NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
                          `sort_num` int NOT NULL DEFAULT 0 COMMENT '排序',
                          `create_by` bigint DEFAULT NULL,
                          `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          `update_by` bigint DEFAULT NULL,
                          `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                          `is_deleted` tinyint NOT NULL DEFAULT 0,
                          PRIMARY KEY (`id`),
                          UNIQUE KEY `uk_pattern_key` (`pattern_key`),
                          KEY `idx_level_status` (`level`, `status`)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='句式管理表'
                        """);
                log.info("数据库表创建完成: sys_sentence_pattern");
            }
            // 兼容已建表：补 pattern_key 列
            ensureColumn(conn, stmt, "sys_sentence_pattern", "pattern_key",
                    "ALTER TABLE `sys_sentence_pattern` ADD COLUMN `pattern_key` varchar(64) NOT NULL DEFAULT '' COMMENT '句式key（唯一，创建后不可改）' AFTER `id`");
            // 兼容已建表：补 type 列
            ensureColumn(conn, stmt, "sys_sentence_pattern", "type",
                    "ALTER TABLE `sys_sentence_pattern` ADD COLUMN `type` varchar(16) NOT NULL DEFAULT 'written' COMMENT '类型 written|spoken' AFTER `pattern_key`");
            // 兼容已建表：补 example 列
            ensureColumn(conn, stmt, "sys_sentence_pattern", "example",
                    "ALTER TABLE `sys_sentence_pattern` ADD COLUMN `example` varchar(512) DEFAULT NULL COMMENT '句式示例' AFTER `description`");
            // 补唯一索引（已存在则忽略）
            try {
                stmt.execute("ALTER TABLE `sys_sentence_pattern` ADD UNIQUE KEY `uk_pattern_key` (`pattern_key`)");
                log.info("数据库索引初始化完成: sys_sentence_pattern.uk_pattern_key");
            } catch (Exception ignored) {
                // 索引已存在
            }
            // 迁移：将旧 description（含"例：xxx"）拆分为纯 description + example
            var migrateRs = stmt.executeQuery("SELECT COUNT(*) FROM sys_sentence_pattern WHERE example IS NULL AND is_deleted = 0");
            migrateRs.next();
            if (migrateRs.getInt(1) > 0) {
                String[][] seedData = {
                        {"simple_sv", "主谓简单句：主语 + 不及物动词，无宾语。", "The sun rises."},
                        {"simple_svo", "主谓宾简单句：主语 + 及物动词 + 宾语。", "I read books."},
                        {"simple_svp", "主系表简单句：主语 + 系动词 + 表语。", "She is happy."},
                        {"simple_svOO", "主谓双宾语：主语+动词+间接宾语+直接宾语。", "He gave me a gift."},
                        {"simple_svoc", "主谓宾补：主语+动词+宾语+宾语补足语。", "We keep the room clean."},
                        {"compound_and", "并列句and连接：简单句 + and + 简单句，表并列递进。", "I like reading and I enjoy travelling."},
                        {"compound_but", "并列句but连接：简单句 + but + 简单句，表转折。", "It is old but it works well."},
                        {"adv_clause_time", "时间状语从句：when/after/before引导时间从句。", "When I arrived, he left."},
                        {"adv_clause_condition", "条件状语从句：if引导条件从句，主将从现。", "If it rains, we will stay home."},
                        {"att_clause_restrict", "限制性定语从句：that/which/who修饰名词，不可逗号隔开。", "The book that I bought is good."},
                        {"att_clause_non_restrict", "非限制性定语从句：which引导，逗号隔开，补充说明。", "The house, which we visited, is historic."},
                        {"noun_clause_object", "宾语从句：that引导作动词宾语。", "I think that he is right."},
                        {"noun_clause_appositive", "同位语从句：that解释抽象名词。", "The news that he won surprised us."},
                        {"non_verb_to_do", "to-do不定式作目的状语。", "I study hard to pass the exam."},
                        {"non_verb_doing", "doing现在分词作伴随状语。", "He sat there reading a novel."},
                        {"non_verb_done", "done过去分词作状语表被动。", "Seen from the hill, the city looks beautiful."},
                        {"passive_general", "被动语态：be + done。", "The letter was written yesterday."},
                        {"subjunctive_if", "if虚拟条件句，与现在事实相反。", "If I were you, I would take the chance."},
                        {"inversion_partial", "部分倒装：否定词置于句首。", "Never have I seen such a place."},
                        {"absolute_construction", "独立主格结构：名词 + 分词，书面高阶句式。", "Weather permitting, we will go hiking."}
                };
                try (var ps = conn.prepareStatement(
                        "UPDATE sys_sentence_pattern SET description = ?, example = ? WHERE pattern_key = ? AND is_deleted = 0")) {
                    for (String[] d : seedData) {
                        ps.setString(1, d[1]);
                        ps.setString(2, d[2]);
                        ps.setString(3, d[0]);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                log.info("数据库迁移完成: sys_sentence_pattern 拆分 description/example 共 20 行");
            }
            migrateRs.close();
            // 迁移：补充口语种子数据（已有书面数据但缺口语时）
            var spokenRs = stmt.executeQuery("SELECT COUNT(*) FROM sys_sentence_pattern WHERE type = 'spoken' AND is_deleted = 0");
            spokenRs.next();
            if (spokenRs.getInt(1) == 0) {
                stmt.execute("""
                        INSERT INTO `sys_sentence_pattern` (`pattern_key`, `type`, `level`, `description`, `example`, `status`, `sort_num`, `create_by`) VALUES
                        ('spk_ellipsis_question', 'spoken', 'L1', '口语省略疑问句，省略主语助动词。仅限口语，写作禁用。', '(Are you) Coming tonight?', 1, 101, 0),
                        ('spk_ellipsis_answer', 'spoken', 'L1', '口语简答碎片句，无完整主谓。仅限口语，写作禁止单独使用。', 'Sounds great.', 1, 102, 0),
                        ('spk_contraction_informal', 'spoken', 'L1', '非正式缩略gonna/wanna/gotta。仅口语，书面严禁。', 'I''m gonna leave.', 1, 103, 0),
                        ('spk_short_response', 'spoken', 'L1', '简短回应句式。对话应答，写作不要单独作为句子。', 'Not really. Probably not.', 1, 104, 0),
                        ('spk_opinion_personally', 'spoken', 'L2', '口语表达个人观点框架。', 'Personally, I think it is a good idea.', 1, 105, 0),
                        ('spk_gap_filler_reflect', 'spoken', 'L2', '口语思考缓冲句式，争取思考时间。', 'That''s an interesting question, let me think.', 1, 106, 0),
                        ('spk_contrast_having_said_that', 'spoken', 'L2', '口语转折框架 having said that。', 'I like city life. Having said that, it is noisy.', 1, 107, 0),
                        ('spk_speculation_reckon', 'spoken', 'L2', '口语委婉推测 I reckon / I suppose。', 'I reckon most young people prefer short-form videos.', 1, 108, 0),
                        ('spk_example_frame', 'spoken', 'L2', '口语举例子框架。', 'A good example of this would be my hometown.', 1, 109, 0),
                        ('spk_cleft_what_i_like', 'spoken', 'L3', '口语分裂强调句 what I like is…。', 'What I enjoy most is hanging out with friends.', 1, 110, 0),
                        ('spk_wish_structure', 'spoken', 'L3', '口语wish表达愿望遗憾。', 'I wish I had more free time.', 1, 111, 0),
                        ('spk_conditional_hypothetical_spoken', 'spoken', 'L3', '口语虚拟假设讨论。', 'If I had more time, I would travel more often.', 1, 112, 0),
                        ('spk_relative_which_is_something', 'spoken', 'L3', '口语特色非限定从句 which is something…。', 'I love hiking, which is something I do every weekend.', 1, 113, 0),
                        ('spk_on_one_hand_on_other', 'spoken', 'L4', '口语正反对比框架 on the one hand…on the other hand。适合讨论思辨话题。', 'On the one hand, city life is convenient; on the other hand, it is stressful.', 1, 114, 0),
                        ('spk_point_to_make', 'spoken', 'L4', '正式口头表达论点。', 'The point I''m trying to make is that urban life has drawbacks.', 1, 115, 0)
                        """);
                log.info("数据库种子数据完成: sys_sentence_pattern 口语共 15 行");
            }
            spokenRs.close();
            // 表为空时插入种子数据
            var countRs = stmt.executeQuery("SELECT COUNT(*) FROM sys_sentence_pattern WHERE is_deleted = 0");
            countRs.next();
            if (countRs.getInt(1) == 0) {
                stmt.execute("""
                        INSERT INTO `sys_sentence_pattern` (`pattern_key`, `type`, `level`, `description`, `example`, `status`, `sort_num`, `create_by`) VALUES
                        ('simple_sv', 'written', 'L1', '主谓简单句：主语 + 不及物动词，无宾语。', 'The sun rises.', 1, 1, 0),
                        ('simple_svo', 'written', 'L1', '主谓宾简单句：主语 + 及物动词 + 宾语。', 'I read books.', 1, 2, 0),
                        ('simple_svp', 'written', 'L1', '主系表简单句：主语 + 系动词 + 表语。', 'She is happy.', 1, 3, 0),
                        ('simple_svOO', 'written', 'L1', '主谓双宾语：主语+动词+间接宾语+直接宾语。', 'He gave me a gift.', 1, 4, 0),
                        ('simple_svoc', 'written', 'L1', '主谓宾补：主语+动词+宾语+宾语补足语。', 'We keep the room clean.', 1, 5, 0),
                        ('compound_and', 'written', 'L2', '并列句and连接：简单句 + and + 简单句，表并列递进。', 'I like reading and I enjoy travelling.', 1, 6, 0),
                        ('compound_but', 'written', 'L2', '并列句but连接：简单句 + but + 简单句，表转折。', 'It is old but it works well.', 1, 7, 0),
                        ('adv_clause_time', 'written', 'L2', '时间状语从句：when/after/before引导时间从句。', 'When I arrived, he left.', 1, 8, 0),
                        ('adv_clause_condition', 'written', 'L2', '条件状语从句：if引导条件从句，主将从现。', 'If it rains, we will stay home.', 1, 9, 0),
                        ('att_clause_restrict', 'written', 'L2', '限制性定语从句：that/which/who修饰名词，不可逗号隔开。', 'The book that I bought is good.', 1, 10, 0),
                        ('att_clause_non_restrict', 'written', 'L3', '非限制性定语从句：which引导，逗号隔开，补充说明。', 'The house, which we visited, is historic.', 1, 11, 0),
                        ('noun_clause_object', 'written', 'L3', '宾语从句：that引导作动词宾语。', 'I think that he is right.', 1, 12, 0),
                        ('noun_clause_appositive', 'written', 'L3', '同位语从句：that解释抽象名词。', 'The news that he won surprised us.', 1, 13, 0),
                        ('non_verb_to_do', 'written', 'L3', 'to-do不定式作目的状语。', 'I study hard to pass the exam.', 1, 14, 0),
                        ('non_verb_doing', 'written', 'L3', 'doing现在分词作伴随状语。', 'He sat there reading a novel.', 1, 15, 0),
                        ('non_verb_done', 'written', 'L3', 'done过去分词作状语表被动。', 'Seen from the hill, the city looks beautiful.', 1, 16, 0),
                        ('passive_general', 'written', 'L2', '被动语态：be + done。', 'The letter was written yesterday.', 1, 17, 0),
                        ('subjunctive_if', 'written', 'L3', 'if虚拟条件句，与现在事实相反。', 'If I were you, I would take the chance.', 1, 18, 0),
                        ('inversion_partial', 'written', 'L4', '部分倒装：否定词置于句首。', 'Never have I seen such a place.', 1, 19, 0),
                        ('absolute_construction', 'written', 'L4', '独立主格结构：名词 + 分词，书面高阶句式。', 'Weather permitting, we will go hiking.', 1, 20, 0),
                        ('spk_ellipsis_question', 'spoken', 'L1', '口语省略疑问句，省略主语助动词。仅限口语，写作禁用。', '(Are you) Coming tonight?', 1, 101, 0),
                        ('spk_ellipsis_answer', 'spoken', 'L1', '口语简答碎片句，无完整主谓。仅限口语，写作禁止单独使用。', 'Sounds great.', 1, 102, 0),
                        ('spk_contraction_informal', 'spoken', 'L1', '非正式缩略gonna/wanna/gotta。仅口语，书面严禁。', 'I''m gonna leave.', 1, 103, 0),
                        ('spk_short_response', 'spoken', 'L1', '简短回应句式。对话应答，写作不要单独作为句子。', 'Not really. Probably not.', 1, 104, 0),
                        ('spk_opinion_personally', 'spoken', 'L2', '口语表达个人观点框架。', 'Personally, I think it is a good idea.', 1, 105, 0),
                        ('spk_gap_filler_reflect', 'spoken', 'L2', '口语思考缓冲句式，争取思考时间。', 'That''s an interesting question, let me think.', 1, 106, 0),
                        ('spk_contrast_having_said_that', 'spoken', 'L2', '口语转折框架 having said that。', 'I like city life. Having said that, it is noisy.', 1, 107, 0),
                        ('spk_speculation_reckon', 'spoken', 'L2', '口语委婉推测 I reckon / I suppose。', 'I reckon most young people prefer short-form videos.', 1, 108, 0),
                        ('spk_example_frame', 'spoken', 'L2', '口语举例子框架。', 'A good example of this would be my hometown.', 1, 109, 0),
                        ('spk_cleft_what_i_like', 'spoken', 'L3', '口语分裂强调句 what I like is…。', 'What I enjoy most is hanging out with friends.', 1, 110, 0),
                        ('spk_wish_structure', 'spoken', 'L3', '口语wish表达愿望遗憾。', 'I wish I had more free time.', 1, 111, 0),
                        ('spk_conditional_hypothetical_spoken', 'spoken', 'L3', '口语虚拟假设讨论。', 'If I had more time, I would travel more often.', 1, 112, 0),
                        ('spk_relative_which_is_something', 'spoken', 'L3', '口语特色非限定从句 which is something…。', 'I love hiking, which is something I do every weekend.', 1, 113, 0),
                        ('spk_on_one_hand_on_other', 'spoken', 'L4', '口语正反对比框架 on the one hand…on the other hand。适合讨论思辨话题。', 'On the one hand, city life is convenient; on the other hand, it is stressful.', 1, 114, 0),
                        ('spk_point_to_make', 'spoken', 'L4', '正式口头表达论点。', 'The point I''m trying to make is that urban life has drawbacks.', 1, 115, 0)
                        """);
                log.info("数据库种子数据完成: sys_sentence_pattern 共 35 行");
            }
            countRs.close();
            // 迁移：sort_num 统一乘以 10，为后续插入留出空间（幂等：检测 simple_sv 是否仍为旧值 1）
            var sortCheckRs = stmt.executeQuery("SELECT COUNT(*) FROM sys_sentence_pattern WHERE pattern_key = 'simple_sv' AND sort_num = 1 AND is_deleted = 0");
            sortCheckRs.next();
            if (sortCheckRs.getInt(1) > 0) {
                String[][] sortData = {
                        {"simple_sv", "10"}, {"simple_svo", "20"}, {"simple_svp", "30"},
                        {"simple_svOO", "40"}, {"simple_svoc", "50"}, {"compound_and", "60"},
                        {"compound_but", "70"}, {"adv_clause_time", "80"}, {"adv_clause_condition", "90"},
                        {"att_clause_restrict", "100"}, {"att_clause_non_restrict", "110"}, {"noun_clause_object", "120"},
                        {"noun_clause_appositive", "130"}, {"non_verb_to_do", "140"}, {"non_verb_doing", "150"},
                        {"non_verb_done", "160"}, {"passive_general", "170"}, {"subjunctive_if", "180"},
                        {"inversion_partial", "190"}, {"absolute_construction", "200"},
                        {"spk_ellipsis_question", "1010"}, {"spk_ellipsis_answer", "1020"}, {"spk_contraction_informal", "1030"},
                        {"spk_short_response", "1040"}, {"spk_opinion_personally", "1050"}, {"spk_gap_filler_reflect", "1060"},
                        {"spk_contrast_having_said_that", "1070"}, {"spk_speculation_reckon", "1080"}, {"spk_example_frame", "1090"},
                        {"spk_cleft_what_i_like", "1100"}, {"spk_wish_structure", "1110"}, {"spk_conditional_hypothetical_spoken", "1120"},
                        {"spk_relative_which_is_something", "1130"}, {"spk_on_one_hand_on_other", "1140"}, {"spk_point_to_make", "1150"}
                };
                try (var ps = conn.prepareStatement("UPDATE sys_sentence_pattern SET sort_num = ? WHERE pattern_key = ? AND is_deleted = 0")) {
                    for (String[] d : sortData) {
                        ps.setInt(1, Integer.parseInt(d[1]));
                        ps.setString(2, d[0]);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                log.info("数据库迁移完成: sys_sentence_pattern sort_num 扩展共 35 行");
            }
            sortCheckRs.close();
        } catch (Exception e) {
            log.warn("创建 sys_sentence_pattern 表失败: {}", e.getMessage());
        }
    }

    /** 确保 wd_user_sentence_pattern_stat 表存在 */
    private void ensureUserSentencePatternStatTable(Connection conn, Statement stmt) {
        try {
            var tables = conn.getMetaData().getTables(null, null, "wd_user_sentence_pattern_stat", new String[]{"TABLE"});
            boolean exists = tables.next();
            tables.close();
            if (!exists) {
                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS `wd_user_sentence_pattern_stat` (
                          `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                          `user_id` bigint NOT NULL COMMENT '用户ID',
                          `pattern_key` varchar(64) NOT NULL COMMENT '句式key',
                          `pattern_description` varchar(512) NOT NULL COMMENT '句式描述（快照）',
                          `total_count` int NOT NULL DEFAULT 0 COMMENT '总练习次数',
                          `correct_count` int NOT NULL DEFAULT 0 COMMENT '正确次数',
                          `wrong_count` int NOT NULL DEFAULT 0 COMMENT '错误次数',
                          `last_error` varchar(500) DEFAULT NULL COMMENT '最近一次错误说明',
                          `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                          `is_deleted` tinyint NOT NULL DEFAULT 0,
                          PRIMARY KEY (`id`),
                          UNIQUE KEY `uk_user_pattern` (`user_id`, `pattern_key`),
                          KEY `idx_user_id` (`user_id`),
                          KEY `idx_update_time` (`update_time`)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户句式训练统计'
                        """);
                log.info("数据库表创建完成: wd_user_sentence_pattern_stat");
            } else {
                // 幂等迁移：确保已有表有 last_error 列
                ensureColumn(conn, stmt, "wd_user_sentence_pattern_stat", "last_error",
                        "ALTER TABLE wd_user_sentence_pattern_stat ADD COLUMN last_error varchar(500) DEFAULT NULL COMMENT '最近一次错误说明' AFTER wrong_count");
            }
        } catch (Exception e) {
            log.warn("创建 wd_user_sentence_pattern_stat 表失败: {}", e.getMessage());
        }
    }

    /** AI 句式猜测流水表 */
    private void ensureSentencePatternGuessTable(Connection conn, Statement stmt) {
        try {
            var tables = conn.getMetaData().getTables(null, null, "wd_sentence_pattern_guess", new String[]{"TABLE"});
            boolean exists = tables.next();
            tables.close();
            if (!exists) {
                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS `wd_sentence_pattern_guess` (
                          `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                          `user_id` bigint NOT NULL COMMENT '用户ID',
                          `practice_id` bigint NOT NULL COMMENT '关联翻译练习ID',
                          `guess_key` varchar(100) DEFAULT NULL COMMENT 'AI给的猜测key',
                          `guess_desc` varchar(500) DEFAULT NULL COMMENT 'AI给的猜测描述',
                          `user_sentence` text COMMENT '用户当时的译文',
                          `direction` varchar(20) DEFAULT NULL COMMENT '翻译方向',
                          `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          `is_deleted` tinyint NOT NULL DEFAULT 0,
                          PRIMARY KEY (`id`),
                          KEY `idx_user_id` (`user_id`),
                          KEY `idx_practice_id` (`practice_id`),
                          KEY `idx_create_time` (`create_time`)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI句式猜测流水'
                        """);
                log.info("数据库表创建完成: wd_sentence_pattern_guess");
            }
        } catch (Exception e) {
            log.warn("创建 wd_sentence_pattern_guess 表失败: {}", e.getMessage());
        }
    }

    /** 翻译场景提示词（仅缺省时插入；英文壳一次性改为中文） */
    private void seedTranslationScenePromptsIfMissing(Connection conn) {
        try {
            var tables = conn.getMetaData().getTables(null, null, "sys_ai_scene_prompt", new String[]{"TABLE"});
            if (!tables.next()) {
                tables.close();
                return;
            }
            tables.close();
            String genPrompt = """
                    {global_constraint}
                    你是翻译出题助手。根据给定词汇和翻译方向，避免生僻词，出一道翻译练习题。
                    {proficiency_block}
                    只返回一行/一小段：
                    题目：<题目内容>
                    """;
            String gradePrompt = """
                    {global_constraint}
                    你是面向非英语母语学习者的雅思取向翻译教练。
                    用 {language} 进行解释。
                    评分：根据雅思标准打分 , 一句话概述
                    解析错误与改进,语言精简
                    参考译文：<用 {target_language}>
                    """;
            String chatPrompt = """
                    {global_constraint}
                    你是 Aiko，面向非英语母语学习者的翻译教练。
                    回答简洁、使用 {language}。
                    相关时优先结合本条练习；
                    拒绝明显与语言学习无关的请求。
                    当前练习记录（相关时作上下文）：{context}
                    """;
            insertScenePromptIfMissing(conn, "translation_generate", "翻译练习出题", genPrompt.trim(),
                    "占位符 {global_constraint}");
            insertScenePromptIfMissing(conn, "translation_grade", "翻译练习批改", gradePrompt.trim(),
                    "占位符 {global_constraint}/{language}/{target_language}");
            insertScenePromptIfMissing(conn, "translation_chat", "翻译练习答疑", chatPrompt.trim(),
                    "占位符 {global_constraint}/{language}/{context}");
            String structuredGradePrompt = """
                    你是英语写作结构化分析助手。根据给定的练习词汇、题目、用户译文和句式表，做三件事并严格以 JSON 返回（不要 Markdown 代码围栏，不要任何解释文字）：
                    1. spellCheck：判断每个练习词汇在用户译文中的拼写是否正确。格式：{"word": true/false}。true=拼写正确，false=拼写错误或未使用。
                    2. patternCheck：判断用户译文中包含了句式表里的哪些句式。完全没用到的句式不要返回。每个命中的句式返回对象：{"correct": true/false, "error": "具体错误说明（仅当 correct=false 时填写，说明哪里用错了、应该怎么改）"}。true=确实用了且语法正确，false=尝试使用但语法错误。
                    3. patternNotFound：如果用户用了句式表里不存在的句式，给出你的猜测（guessKey=简短英文标识，guessDesc=中文描述）。
                    返回格式：
                    {"spellCheck":{"word1":true,"word2":false},"patternCheck":{"simple_sv":{"correct":true},"compound_and":{"correct":false,"error":"缺少连词，两个独立分句不能只用逗号连接"}},"patternNotFound":[{"guessKey":"xxx","guessDesc":"xxx"}]}
                    """;
            insertScenePromptIfMissing(conn, "translation_grade_structured", "翻译练习批改-结构化",
                    structuredGradePrompt.trim(), "异步结构化分析：拼写+句式命中");
            // 已有环境：英文场景壳统一改为中文（不覆盖已手工改成中文的文案）
            try (var ps = conn.prepareStatement("""
                    UPDATE sys_ai_scene_prompt
                    SET system_prompt = ?, remark = ?, update_time = NOW()
                    WHERE scene_key = 'translation_grade' AND is_deleted = 0
                    """)) {
                ps.setString(1, gradePrompt.trim());
                ps.setString(2, "占位符 {global_constraint}/{language}/{target_language}/{proficiency_block}");
                int n = ps.executeUpdate();
                if (n > 0) {
                    log.info("数据库迁移完成: translation_grade 提示词已更新，共 {} 行", n);
                }
            }
            try (var ps = conn.prepareStatement("""
                    UPDATE sys_ai_scene_prompt
                    SET system_prompt = ?, remark = ?, update_time = NOW()
                    WHERE scene_key = 'translation_generate' AND is_deleted = 0
                    """)) {
                ps.setString(1, genPrompt.trim());
                ps.setString(2, "占位符 {global_constraint}/{proficiency_block}");
                int n = ps.executeUpdate();
                if (n > 0) {
                    log.info("数据库迁移完成: translation_generate 提示词已更新，共 {} 行", n);
                }
            }
            try (var ps = conn.prepareStatement("""
                    UPDATE sys_ai_scene_prompt
                    SET system_prompt = ?, remark = ?, update_time = NOW()
                    WHERE scene_key = 'translation_chat' AND is_deleted = 0
                      AND system_prompt LIKE '%You are Aiko, a translation coach%'
                    """)) {
                ps.setString(1, chatPrompt.trim());
                ps.setString(2, "占位符 {global_constraint}/{language}/{context}");
                int n = ps.executeUpdate();
                if (n > 0) {
                    log.info("数据库迁移完成: translation_chat 提示词已改为中文，共 {} 行", n);
                }
            }
        } catch (Exception e) {
            log.warn("写入翻译场景提示词失败: {}", e.getMessage());
        }
    }

    private void ensureStudyDurationTable(Connection conn, Statement stmt) throws Exception {
        var tables = conn.getMetaData().getTables(null, null, "wd_study_duration", new String[]{"TABLE"});
        boolean exists = tables.next();
        tables.close();
        if (exists) {
            return;
        }
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS `wd_study_duration` (
                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                  `user_id` bigint NOT NULL COMMENT '用户ID',
                  `action_type` varchar(32) NOT NULL COMMENT '行为 reading|study|translation|writing',
                  `ref_table` varchar(64) DEFAULT NULL COMMENT '关联业务表名，今日背诵可空',
                  `ref_id` bigint DEFAULT NULL COMMENT '关联业务表主键，今日背诵可空',
                  `start_time` datetime NOT NULL COMMENT '本段开始时间',
                  `end_time` datetime DEFAULT NULL COMMENT '本段结束时间，未结束为空',
                  `duration_seconds` int DEFAULT NULL COMMENT '间隔秒数',
                  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除',
                  PRIMARY KEY (`id`),
                  KEY `idx_user_start` (`user_id`, `start_time`),
                  KEY `idx_user_open` (`user_id`, `end_time`),
                  KEY `idx_ref` (`ref_table`, `ref_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习时长分段'
                """);
        log.info("数据库表初始化完成: wd_study_duration");
    }

    /** 已建表时把 ref 列改为可空（今日背诵无单一 ID） */
    private void ensureStudyDurationRefNullable(Connection conn, Statement stmt) {
        try {
            stmt.execute("ALTER TABLE wd_study_duration MODIFY COLUMN ref_table varchar(64) DEFAULT NULL " +
                    "COMMENT '关联业务表名，今日背诵可空'");
            stmt.execute("ALTER TABLE wd_study_duration MODIFY COLUMN ref_id bigint DEFAULT NULL " +
                    "COMMENT '关联业务表主键，今日背诵可空'");
        } catch (Exception e) {
            log.warn("调整 wd_study_duration.ref 可空失败（可忽略）: {}", e.getMessage());
        }
    }

    /** 阅读空闲结段秒数（不对用户暴露；INSERT IGNORE 幂等） */
    private void seedStudyIdleTimeoutConfig(Connection conn) {
        try (var ps = conn.prepareStatement("""
                INSERT IGNORE INTO sys_base_config
                (config_key, config_value, value_type, category, description, sort_num)
                VALUES (?, '60', 'int', 'study', ?, 3)
                """)) {
            ps.setString(1, "study.idle_timeout_seconds");
            ps.setString(2, "阅读等场景无操作超过该秒数视为离线并结段（不对用户暴露，默认60）。");
            int n = ps.executeUpdate();
            if (n > 0) {
                log.info("数据库种子初始化完成: study.idle_timeout_seconds");
            }
        } catch (Exception e) {
            log.warn("写入 study.idle_timeout_seconds 失败: {}", e.getMessage());
        }
    }

    /** 停留满该秒数才入库（不对用户暴露；默认120=2分钟） */
    private void seedStudyMinValidSecondsConfig(Connection conn) {
        try (var ps = conn.prepareStatement("""
                INSERT IGNORE INTO sys_base_config
                (config_key, config_value, value_type, category, description, sort_num)
                VALUES (?, '120', 'int', 'study', ?, 4)
                """)) {
            ps.setString(1, "study.min_valid_seconds");
            ps.setString(2, "进入埋点页后前端先本地计时；停留满该秒数才写入时长表，未满则丢弃（不对用户暴露，默认120）。");
            int n = ps.executeUpdate();
            if (n > 0) {
                log.info("数据库种子初始化完成: study.min_valid_seconds");
            }
        } catch (Exception e) {
            log.warn("写入 study.min_valid_seconds 失败: {}", e.getMessage());
        }
    }

    private void ensureProficiencyTables(Connection conn, Statement stmt) throws Exception {
        // 直接 IF NOT EXISTS：不依赖 getTables（库名/大小写差异时可能误判已存在而跳过创建）
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS `sys_proficiency_scale` (
                  `id` bigint NOT NULL AUTO_INCREMENT,
                  `code` varchar(32) NOT NULL COMMENT 'ielts|cefr|custom',
                  `name` varchar(64) NOT NULL,
                  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1启用0停用',
                  `sort_num` int NOT NULL DEFAULT 0,
                  `remark` varchar(255) DEFAULT NULL,
                  `create_by` bigint DEFAULT NULL,
                  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                  `update_by` bigint DEFAULT NULL,
                  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  `is_deleted` tinyint NOT NULL DEFAULT 0,
                  PRIMARY KEY (`id`),
                  UNIQUE KEY `uk_code` (`code`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='水平标尺'
                """);
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS `sys_proficiency_level` (
                  `id` bigint NOT NULL AUTO_INCREMENT,
                  `scale_id` bigint NOT NULL,
                  `code` varchar(32) NOT NULL,
                  `name` varchar(64) NOT NULL,
                  `sort_num` int NOT NULL DEFAULT 0,
                  `band_min` decimal(3,1) NOT NULL,
                  `band_max` decimal(3,1) NOT NULL,
                  `generate_constraints` mediumtext COMMENT '出题约束正文',
                  `focus_skills` json DEFAULT NULL,
                  `remark` varchar(255) DEFAULT NULL,
                  `status` tinyint NOT NULL DEFAULT 1,
                  `create_by` bigint DEFAULT NULL,
                  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                  `update_by` bigint DEFAULT NULL,
                  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  `is_deleted` tinyint NOT NULL DEFAULT 0,
                  PRIMARY KEY (`id`),
                  KEY `idx_scale` (`scale_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='水平等级'
                """);
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS `wd_user_proficiency` (
                  `id` bigint NOT NULL AUTO_INCREMENT,
                  `user_id` bigint NOT NULL,
                  `active_scale_code` varchar(32) DEFAULT NULL,
                  `level_code` varchar(32) DEFAULT NULL,
                  `level_name` varchar(64) DEFAULT NULL,
                  `estimated_band` decimal(3,1) DEFAULT NULL,
                  `confidence` varchar(16) DEFAULT NULL COMMENT 'low|medium|high',
                  `sample_count` int NOT NULL DEFAULT 0,
                  `dim_json` json DEFAULT NULL,
                  `weak_skills_json` json DEFAULT NULL,
                  `recent_bands_json` json DEFAULT NULL,
                  `last_analysis_id` bigint DEFAULT NULL,
                  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (`id`),
                  UNIQUE KEY `uk_user` (`user_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户能力快照'
                """);
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS `wd_user_proficiency_analysis` (
                  `id` bigint NOT NULL AUTO_INCREMENT,
                  `user_id` bigint NOT NULL,
                  `trigger_grade_id` bigint DEFAULT NULL,
                  `trigger_source` varchar(32) NOT NULL DEFAULT 'admin_manual',
                  `active_scale_code` varchar(32) DEFAULT NULL,
                  `level_code` varchar(32) DEFAULT NULL,
                  `level_name` varchar(64) DEFAULT NULL,
                  `estimated_band` decimal(3,1) DEFAULT NULL,
                  `confidence` varchar(16) DEFAULT NULL,
                  `sample_count` int NOT NULL DEFAULT 0,
                  `dim_json` json DEFAULT NULL,
                  `weak_skills_json` json DEFAULT NULL,
                  `recent_bands_json` json DEFAULT NULL,
                  `grade_ids_json` json DEFAULT NULL,
                  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                  `create_by` bigint DEFAULT NULL,
                  PRIMARY KEY (`id`),
                  KEY `idx_user_time` (`user_id`, `id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户能力测评流水'
                """);
        log.info("数据库表检查完成: sys_proficiency_scale / sys_proficiency_level / wd_user_proficiency / wd_user_proficiency_analysis");
    }

    private void ensureWordPosStatTable(Statement stmt) throws Exception {
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS `wd_word_pos_stat` (
                  `id` bigint NOT NULL AUTO_INCREMENT,
                  `user_id` bigint NOT NULL,
                  `word_id` bigint DEFAULT NULL COMMENT 'wd_word.id，可空',
                  `word_proto` varchar(128) NOT NULL,
                  `pos` varchar(16) NOT NULL COMMENT 'n|v|adj|adv|phr|other…',
                  `meaning` varchar(512) DEFAULT NULL COMMENT '该词性对应释义',
                  `practice_count` int NOT NULL DEFAULT 0 COMMENT '翻译练习中作为目标词出现次数',
                  `correct_count` int NOT NULL DEFAULT 0 COMMENT '判定正确运用次数',
                  `spell_practice_count` int NOT NULL DEFAULT 0 COMMENT '拼写练写次数',
                  `spell_correct_count` int NOT NULL DEFAULT 0 COMMENT '拼写正确次数',
                  `spell_status` tinyint NOT NULL DEFAULT 0 COMMENT '拼写阶梯0未练1~3复习4熟记',
                  `spell_status_data` datetime DEFAULT NULL COMMENT '进入当前spell_status的学习日',
                  `spell_mastered` tinyint NOT NULL DEFAULT 0 COMMENT '书写是否掌握 0否1是（由spell_status=4同步）',
                  `last_practiced_at` datetime DEFAULT NULL,
                  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (`id`),
                  UNIQUE KEY `uk_user_proto_pos` (`user_id`, `word_proto`, `pos`),
                  KEY `idx_user_practice` (`user_id`, `practice_count`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='词性运用统计（翻译）'
                """);
        log.info("数据库表检查完成: wd_word_pos_stat");
    }

    /** 旧 spell_mastered=1 → spell_status=4（仅补未迁移行） */
    private void migrateSpellStatusFromMastered(Statement stmt) {
        try {
            int n = stmt.executeUpdate("""
                    UPDATE wd_word_pos_stat
                    SET spell_status = 4,
                        spell_status_data = COALESCE(spell_status_data, update_time, create_time, NOW()),
                        spell_mastered = 1
                    WHERE spell_mastered = 1 AND spell_status = 0
                    """);
            if (n > 0) {
                log.info("wd_word_pos_stat 迁移: spell_mastered→spell_status=4 共 {} 行", n);
            }
        } catch (Exception e) {
            log.warn("spell_status 迁移跳过: {}", e.getMessage());
        }
    }

    /** 拼写阶梯缩短遗留：旧 5/6/7 → 先落到 4（熟记），中间档 3/4 在 align 里再处理 */
    private void shortenSpellStudyStatuses(Statement stmt) {
        try {
            int done = stmt.executeUpdate("""
                    UPDATE wd_word_pos_stat
                    SET spell_status = 4,
                        spell_mastered = 1,
                        spell_status_data = COALESCE(spell_status_data, update_time, create_time, NOW())
                    WHERE spell_status IN (5, 6, 7)
                    """);
            if (done > 0) {
                log.info("wd_word_pos_stat 高位状态收拢为熟记=4: {} 行", done);
            }
        } catch (Exception e) {
            log.warn("spell_status 缩短迁移跳过: {}", e.getMessage());
        }
    }

    /**
     * 拼写熟记码对齐为 4：一次性把旧熟记 3 → 4。
     * 用配置标记防重复（否则会把新的 status=3 第二轮复习误升为熟记）。
     */
    private void alignSpellStudyStatuses(Connection conn, Statement stmt) {
        final String flagKey = "migrate.spell_status_mastered_v4";
        try {
            try (var ps = conn.prepareStatement(
                    "SELECT 1 FROM sys_base_config WHERE config_key = ? LIMIT 1")) {
                ps.setString(1, flagKey);
                try (var rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return;
                    }
                }
            }
            int n = stmt.executeUpdate("""
                    UPDATE wd_word_pos_stat
                    SET spell_status = 4,
                        spell_mastered = 1,
                        spell_status_data = COALESCE(spell_status_data, update_time, create_time, NOW())
                    WHERE spell_status = 3 OR spell_mastered = 1
                    """);
            stmt.executeUpdate("""
                    UPDATE wd_word_pos_stat
                    SET spell_mastered = CASE WHEN spell_status = 4 THEN 1 ELSE 0 END
                    """);
            try (var ps = conn.prepareStatement("""
                    INSERT IGNORE INTO sys_base_config
                    (config_key, config_value, value_type, category, description, sort_num)
                    VALUES (?, '1', 'string', 'migrate', ?, 0)
                    """)) {
                ps.setString(1, flagKey);
                ps.setString(2, "spell_status 熟记码已对齐为 4（勿删）");
                ps.executeUpdate();
            }
            if (n > 0) {
                log.info("wd_word_pos_stat 阶梯对齐为熟记=4: {} 行", n);
            }
        } catch (Exception e) {
            log.warn("spell_status 对齐迁移跳过: {}", e.getMessage());
        }
    }

    /**
     * 背单词阶梯对齐为 0~6（6=熟记）：一次性把旧熟记码 5/7 → 6。
     * 用配置标记防重复执行（否则会把新的 status=5 第四轮复习误升为熟记）。
     */
    private void alignWordStudyStatuses(Connection conn, Statement stmt) {
        final String flagKey = "migrate.word_status_mastered_v6";
        try {
            try (var ps = conn.prepareStatement(
                    "SELECT 1 FROM sys_base_config WHERE config_key = ? LIMIT 1")) {
                ps.setString(1, flagKey);
                try (var rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return;
                    }
                }
            }
            int n = stmt.executeUpdate("UPDATE wd_word SET status = 6 WHERE status IN (5, 7)");
            try (var ps = conn.prepareStatement("""
                    INSERT IGNORE INTO sys_base_config
                    (config_key, config_value, value_type, category, description, sort_num)
                    VALUES (?, '1', 'string', 'migrate', ?, 0)
                    """)) {
                ps.setString(1, flagKey);
                ps.setString(2, "word status 熟记码已对齐为 6（勿删）");
                ps.executeUpdate();
            }
            if (n > 0) {
                log.info("wd_word 阶梯对齐为熟记=6: {} 行", n);
            }
        } catch (Exception e) {
            log.warn("word status 对齐迁移跳过: {}", e.getMessage());
        }
    }

    private void seedProficiencyActiveScaleConfig(Connection conn) {
        try (var ps = conn.prepareStatement("""
                INSERT IGNORE INTO sys_base_config
                (config_key, config_value, value_type, category, description, sort_num)
                VALUES (?, 'cefr', 'string', 'proficiency', ?, 10)
                """)) {
            ps.setString(1, "proficiency.active_scale");
            ps.setString(2, "当前生效水平标尺 code：ielts / cefr / custom（默认 cefr）。");
            int n = ps.executeUpdate();
            if (n > 0) {
                log.info("数据库种子初始化完成: proficiency.active_scale");
            }
        } catch (Exception e) {
            log.warn("写入 proficiency.active_scale 失败: {}", e.getMessage());
        }
    }

    private void seedProficiencyScalesAndLevels(Connection conn, Statement stmt) {
        try {
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM sys_proficiency_scale WHERE is_deleted = 0")) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return;
                }
            }
        } catch (Exception e) {
            log.warn("检查 sys_proficiency_scale 种子失败: {}", e.getMessage());
            return;
        }
        try {
            insertScaleWithLevels(conn, "cefr", "CEFR", 1,
                    new String[][]{
                            {"A1", "A1", "0.0", "3.0", "Very short simple sentences (8-12 words). Present simple only. No relative clauses, no passive, no idioms."},
                            {"A2", "A2", "3.0", "4.0", "Short everyday sentences. Present/past simple, basic connectives (and/but/because). Avoid complex noun phrases."},
                            {"B1", "B1", "4.0", "5.0", "Moderate sentence length. Allowed: present perfect, first conditional, simple relative clauses. Avoid advanced academic jargon."},
                            {"B2", "B2", "5.0", "6.5", "IELTS-style but clear. Mix simple/complex sentences; limited passive; common academic vocabulary only."},
                            {"C1", "C1", "6.5", "7.5", "Flexible complex sentences OK; precise academic lexis; still avoid rare idioms and overly dense packing."},
                            {"C2", "C2", "7.5", "9.0", "Near-native flexibility; sophisticated cohesion allowed; keep natural, not deliberately obscure."}
                    });
            insertScaleWithLevels(conn, "ielts", "IELTS Band", 2,
                    new String[][]{
                            {"B4", "Band 4", "0.0", "4.0", "Very basic drills. Short clauses; high-frequency words; no complex grammar."},
                            {"B5", "Band 5", "4.0", "5.0", "Simple paragraph feel. Limited complex sentences; focus on clear meaning over sophistication."},
                            {"B6", "Band 6", "5.0", "6.0", "Generally effective. Some complex structures OK; keep vocabulary accessible."},
                            {"B7", "Band 7", "6.0", "7.0", "Good control. Flexible sentence forms; academic themes with common lexis."},
                            {"B8", "Band 8+", "7.0", "9.0", "Skilled user. Natural complexity allowed; still recycle the given vocabulary."}
                    });
            insertScaleWithLevels(conn, "custom", "产品等级", 3,
                    new String[][]{
                            {"L1", "入门 L1", "0.0", "3.0", "入门：超短句，现在时为主，禁止复杂从句与被动。"},
                            {"L2", "基础 L2", "3.0", "4.0", "基础：日常短句，and/but/because，避免学术难词。"},
                            {"L3", "进阶 L3", "4.0", "5.0", "进阶：可出现现在完成/简单定语从句，句长适中。"},
                            {"L4", "中高 L4", "5.0", "6.0", "中高：简单复合句与常见学术词，避免冷僻表达。"},
                            {"L5", "高阶 L5", "6.0", "7.0", "高阶：灵活句式，连贯自然，仍以给定词为主。"},
                            {"L6", "精通 L6", "7.0", "9.0", "精通：接近母语灵活度，但仍须优先使用练习词表。"}
                    });
            log.info("数据库种子初始化完成: sys_proficiency_scale / sys_proficiency_level");
        } catch (Exception e) {
            log.warn("写入水平标尺种子失败: {}", e.getMessage());
        }
    }

    private void insertScaleWithLevels(Connection conn, String code, String name, int sort,
                                       String[][] levels) throws Exception {
        long scaleId;
        try (var ps = conn.prepareStatement("""
                INSERT INTO sys_proficiency_scale(code, name, status, sort_num, remark, create_time, update_time)
                VALUES (?, ?, 1, ?, 'seed', NOW(), NOW())
                """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, code);
            ps.setString(2, name);
            ps.setInt(3, sort);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new IllegalStateException("no scale id for " + code);
                }
                scaleId = keys.getLong(1);
            }
        }
        try (var ps = conn.prepareStatement("""
                INSERT INTO sys_proficiency_level(
                  scale_id, code, name, sort_num, band_min, band_max,
                  generate_constraints, status, create_time, update_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 1, NOW(), NOW())
                """)) {
            for (int i = 0; i < levels.length; i++) {
                String[] row = levels[i];
                ps.setLong(1, scaleId);
                ps.setString(2, row[0]);
                ps.setString(3, row[1]);
                ps.setInt(4, i + 1);
                ps.setBigDecimal(5, new java.math.BigDecimal(row[2]));
                ps.setBigDecimal(6, new java.math.BigDecimal(row[3]));
                ps.setString(7, row[4]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertScenePromptIfMissing(Connection conn, String sceneKey, String sceneName,
                                            String prompt, String remark) throws Exception {
        try (var ps = conn.prepareStatement("""
                INSERT INTO sys_ai_scene_prompt
                (scene_key, locale, scene_name, system_prompt, remark, status, sort_num, create_time, update_time)
                SELECT ?, 'zh-CN', ?, ?, ?, 1, 5, NOW(), NOW()
                FROM DUAL
                WHERE NOT EXISTS (
                  SELECT 1 FROM sys_ai_scene_prompt
                  WHERE scene_key = ? AND locale = 'zh-CN' AND is_deleted = 0
                )
                """)) {
            ps.setString(1, sceneKey);
            ps.setString(2, sceneName);
            ps.setString(3, prompt);
            ps.setString(4, remark);
            ps.setString(5, sceneKey);
            int n = ps.executeUpdate();
            if (n > 0) {
                log.info("数据库种子初始化完成: {} 场景提示词已写入", sceneKey);
            }
        }
    }

    /** 确保 sys_quote 表存在 */
    private void ensureQuoteTable(Connection conn, Statement stmt) {
        try {
            var tables = conn.getMetaData().getTables(null, null, "sys_quote", new String[]{"TABLE"});
            boolean exists = tables.next();
            tables.close();
            if (!exists) {
                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS `sys_quote` (
                          `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                          `content` varchar(512) NOT NULL COMMENT '语录内容',
                          `author` varchar(128) DEFAULT '' COMMENT '作者/出处（可选）',
                          `status` tinyint NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
                          `sort_num` int NOT NULL DEFAULT 0 COMMENT '排序',
                          `create_by` bigint DEFAULT NULL,
                          `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          `update_by` bigint DEFAULT NULL,
                          `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                          `is_deleted` tinyint NOT NULL DEFAULT 0,
                          PRIMARY KEY (`id`)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统语录（激励语）'
                        """);
                log.info("数据库表创建完成: sys_quote");
            }
        } catch (Exception e) {
            log.warn("创建 sys_quote 表失败: {}", e.getMessage());
        }
    }

    /** 确保 sys_study_tip 表存在 */
    private void ensureStudyTipTable(Connection conn, Statement stmt) {
        try {
            var tables = conn.getMetaData().getTables(null, null, "sys_study_tip", new String[]{"TABLE"});
            boolean exists = tables.next();
            tables.close();
            if (!exists) {
                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS `sys_study_tip` (
                          `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                          `question` varchar(512) NOT NULL COMMENT '问题',
                          `answer` text NOT NULL COMMENT '回答/提示',
                          `category` varchar(64) DEFAULT '' COMMENT '分类（如 vocabulary/reading/grammar）',
                          `status` tinyint NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
                          `sort_num` int NOT NULL DEFAULT 0 COMMENT '排序',
                          `create_by` bigint DEFAULT NULL,
                          `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          `update_by` bigint DEFAULT NULL,
                          `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                          `is_deleted` tinyint NOT NULL DEFAULT 0,
                          PRIMARY KEY (`id`)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自学小Tips（一问一答）'
                        """);
                log.info("数据库表创建完成: sys_study_tip");
            }
        } catch (Exception e) {
            log.warn("创建 sys_study_tip 表失败: {}", e.getMessage());
        }
    }
}
