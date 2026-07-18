-- =============================================
-- Word Diary 数据库迁移/变更脚本
-- =============================================
-- 说明：当 schema.sql 中新增字段，但数据库已存在该表时，
-- CREATE TABLE IF NOT EXISTS 不会自动添加新列，
-- 需要手动执行以下 ALTER TABLE 语句。
-- =============================================

-- 用户表：新增会员标识字段
ALTER TABLE wd_user ADD COLUMN is_member TINYINT NOT NULL DEFAULT 0 COMMENT '是否会员 1是 0否';

-- 大模型调用历史记录表（新建）
CREATE TABLE IF NOT EXISTS `wd_ai_call_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '调用用户ID',
  `model_name` varchar(64) NOT NULL COMMENT '模型名称，如 deepseek-v4-flash',
  `prompt_tokens` int NOT NULL DEFAULT 0 COMMENT '提示词token数量',
  `completion_tokens` int NOT NULL DEFAULT 0 COMMENT '返回token数量',
  `total_tokens` int NOT NULL DEFAULT 0 COMMENT '总token数量',
  `system_prompt` text COMMENT '系统提示词',
  `user_prompt` text COMMENT '用户提示词',
  `response_data` mediumtext COMMENT '大模型返回的原始数据',
  `duration_ms` int DEFAULT NULL COMMENT '调用耗时（毫秒）',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '修改人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除 1已删除 0未删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='大模型调用历史记录';

-- 书籍表：新增 file_size 字段
ALTER TABLE wd_book ADD COLUMN file_size bigint DEFAULT 0 COMMENT '文件大小(字节)' AFTER file_type;

-- 用户 AI 功能偏好表：新增 feature_order 字段（保存用户自定义排序）
ALTER TABLE wd_user_ai_preference ADD COLUMN feature_order VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '用户自定义功能排序，JSON数组，如 ["word","example","collocation"]' AFTER feature_keys;

-- 更新连读音标功能描述（更精确的发音技巧分析要求）
UPDATE sys_ai_feature SET description = '分析选中句子的发音技巧，包括：连读（linking）、吞音/省略（elision）、重读/弱读（stressed/unstressed）、哪个字母不发音（silent letters）、轻音/浊化等。给出具体的发音音标和技巧说明。要求简洁，不要啰嗦。' WHERE feature_key = 'phonetic';

-- =============================================
-- 2026-07-12: 拆分 description 和 prompt
-- =============================================
-- 1. 添加 prompt 字段
ALTER TABLE sys_ai_feature ADD COLUMN prompt TEXT COMMENT 'AI提示词，传递给大模型的详细指令' AFTER description;

-- 多语言标签（中文源文在 feature_name/description；其它语言 AI 同步）
-- ALTER TABLE sys_ai_feature ADD COLUMN name_i18n json DEFAULT NULL COMMENT '标签多语言 JSON: en/id/th/vi' AFTER description;
-- ALTER TABLE sys_ai_feature ADD COLUMN description_i18n json DEFAULT NULL COMMENT '描述多语言 JSON: en/id/th/vi' AFTER name_i18n;

-- 2. 将现有 description 迁移到 prompt（因为旧数据都是AI提示词）
UPDATE sys_ai_feature SET prompt = description;

-- 3. 更新 description 为用户友好的简短说明
UPDATE sys_ai_feature SET description = '讲解重难点单词的词性、含义和用法' WHERE feature_key = 'word';
UPDATE sys_ai_feature SET description = '翻译和释义选中的句子或段落' WHERE feature_key = 'meaning';
UPDATE sys_ai_feature SET description = '分析固定搭配和短语用法' WHERE feature_key = 'collocation';
UPDATE sys_ai_feature SET description = '分析语法结构和句式特点' WHERE feature_key = 'sentence_pattern';
UPDATE sys_ai_feature SET description = '分析发音技巧：连读、吞音、重读、不发音字母等' WHERE feature_key = 'phonetic';
UPDATE sys_ai_feature SET description = '分析词根词缀构成和词义演变' WHERE feature_key = 'root_affix';
UPDATE sys_ai_feature SET description = '提供类似例句帮助理解用法' WHERE feature_key = 'example';

-- =============================================
-- 2026-07-13: 会员等级体系
-- =============================================
-- 1. 用户表新增 member_tier 字段
ALTER TABLE wd_user ADD COLUMN member_tier int NOT NULL DEFAULT 1 COMMENT '会员等级，关联 sys_member_tier.tier' AFTER is_member;

-- 2. 会员等级配置表
CREATE TABLE IF NOT EXISTS `sys_member_tier` (
  `tier` int NOT NULL COMMENT '会员等级',
  `tier_name` varchar(32) NOT NULL COMMENT '等级名称',
  `max_bookshelves` int NOT NULL DEFAULT 3 COMMENT '书架数量上限',
  `max_wordbooks` int NOT NULL DEFAULT 3 COMMENT '单词本数量上限',
  `max_words_per_book` int NOT NULL DEFAULT 200 COMMENT '每个单词本单词上限',
  `daily_import_limit` int NOT NULL DEFAULT 10 COMMENT '每日批量导入次数上限',
  `daily_ai_reading_limit` int NOT NULL DEFAULT 50 COMMENT '每日AI阅读次数上限',
  `is_ai_enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用AI功能 1是 0否',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '修改人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除 1已删除 0未删除',
  PRIMARY KEY (`tier`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员等级配置表';

-- 3. 种子数据
INSERT IGNORE INTO `sys_member_tier` (`tier`, `tier_name`, `max_bookshelves`, `max_wordbooks`, `max_words_per_book`, `daily_import_limit`, `daily_ai_reading_limit`, `is_ai_enabled`) VALUES
(1, '免费版', 3, 3, 200, 10, 50, 1),
(2, '会员版', 6, 6, 400, 20, 100, 1);

-- 4. 邀请码表
CREATE TABLE IF NOT EXISTS `wd_invite_code` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `seq_no` int NOT NULL COMMENT '序号',
  `invite_code` varchar(32) NOT NULL COMMENT '邀请码',
  `member_tier` int NOT NULL DEFAULT 1 COMMENT '对应会员等级，关联 sys_member_tier.tier',
  `used_by` bigint DEFAULT NULL COMMENT '核销用户ID，NULL表示未核销',
  `used_time` datetime DEFAULT NULL COMMENT '核销时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '修改人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除 1已删除 0未删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invite_code` (`invite_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邀请码表';

-- 5. 已有邀请码表的：新增 member_tier 字段
ALTER TABLE wd_invite_code ADD COLUMN member_tier int NOT NULL DEFAULT 1 COMMENT '对应会员等级，关联 sys_member_tier.tier' AFTER invite_code;

-- =============================================
-- 2026-07-13: 用户权益记录表
-- =============================================
CREATE TABLE IF NOT EXISTS `wd_user_benefit` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `tier_snapshot` int NOT NULL COMMENT '会员等级快照',
  `max_bookshelves` int NOT NULL COMMENT '书架数量上限',
  `max_wordbooks` int NOT NULL COMMENT '单词本数量上限',
  `max_words_per_book` int NOT NULL COMMENT '每个单词本单词上限',
  `daily_import_limit` int NOT NULL COMMENT '每日批量导入次数上限',
  `daily_ai_reading_limit` int NOT NULL COMMENT '每日AI阅读次数上限',
  `is_ai_enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用AI功能 1是 0否',
  `channel` varchar(32) NOT NULL COMMENT '获取渠道: invite_code/purchase/gift/admin',
  `channel_ref_id` bigint DEFAULT NULL COMMENT '渠道关联ID，如邀请码ID、订单ID等',
  `acquired_at` datetime NOT NULL COMMENT '获取时间',
  `expired_at` datetime DEFAULT NULL COMMENT '过期时间，NULL表示永久',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '修改人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除 1已删除 0未删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_acquired` (`user_id`, `acquired_at` DESC),
  KEY `idx_user_channel` (`user_id`, `channel`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户权益记录表';

-- =============================================
-- 2026-07-16: AI 调用成本计量字段（暂不做会员，先记 token）
-- =============================================
ALTER TABLE wd_ai_call_log ADD COLUMN call_scene varchar(32) NOT NULL DEFAULT '' COMMENT '调用场景: word_parse / reading_chat / feature' AFTER model_name;
ALTER TABLE wd_ai_call_log ADD COLUMN input_text text COMMENT '用户原始输入（选中文本/单词列表/提问）' AFTER call_scene;
ALTER TABLE wd_ai_call_log ADD COLUMN input_char_count int NOT NULL DEFAULT 0 COMMENT '用户原始输入字符数' AFTER input_text;
ALTER TABLE wd_ai_call_log ADD COLUMN input_word_count int NOT NULL DEFAULT 0 COMMENT '用户原始输入词数（中英混合）' AFTER input_char_count;
ALTER TABLE wd_ai_call_log ADD COLUMN assembled_prompt_chars int NOT NULL DEFAULT 0 COMMENT '拼装后 system+user 提示词总字符数' AFTER input_word_count;

-- =============================================
-- 2026-07-16: 修复 sys_ebbinghaus_node 脏数据
-- 原因：种子用 id=0，MySQL 自增会当成“自动生成”，每次启动重复插入「初学」
-- 处理：清空后写入标准 7 档（id 从 1 起，按 sort 排序供学习逻辑使用）
-- =============================================
DELETE FROM sys_ebbinghaus_node;
INSERT INTO `sys_ebbinghaus_node` (`id`, `node_name`, `day_interval`, `sort`, `is_deleted`) VALUES
(1, '初学', 0, 0, 0),
(2, '第1天复习', 1, 1, 0),
(3, '第2天复习', 2, 2, 0),
(4, '第4天复习', 4, 3, 0),
(5, '第7天复习', 7, 4, 0),
(6, '第15天复习', 15, 5, 0),
(7, '第30天复习', 30, 6, 0);
ALTER TABLE sys_ebbinghaus_node AUTO_INCREMENT = 8;

-- =============================================
-- 2026-07-16: AI 模型定价表 + 调用日志估算成本
-- =============================================
ALTER TABLE wd_ai_call_log ADD COLUMN estimated_cost decimal(14,8) NOT NULL DEFAULT 0 COMMENT '按当时模型单价估算的成本' AFTER total_tokens;

CREATE TABLE IF NOT EXISTS `sys_ai_model` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `model_key` varchar(64) NOT NULL COMMENT '模型标识，需与调用日志 model_name 对齐',
  `display_name` varchar(128) NOT NULL DEFAULT '' COMMENT '展示名称',
  `provider` varchar(64) NOT NULL DEFAULT '' COMMENT '供应商',
  `currency` varchar(8) NOT NULL DEFAULT 'CNY' COMMENT '计价币种',
  `input_price_per_million` decimal(14,6) NOT NULL DEFAULT 0 COMMENT '输入单价：每百万 prompt tokens',
  `output_price_per_million` decimal(14,6) NOT NULL DEFAULT 0 COMMENT '输出单价：每百万 completion tokens',
  `is_default` tinyint NOT NULL DEFAULT 0 COMMENT '是否默认模型',
  `is_enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用',
  `remark` varchar(512) DEFAULT '' COMMENT '备注',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_key` (`model_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI模型采购定价表';

INSERT IGNORE INTO `sys_ai_model`
(`model_key`, `display_name`, `provider`, `currency`, `input_price_per_million`, `output_price_per_million`, `is_default`, `is_enabled`, `remark`)
VALUES
('deepseek-v4-flash', 'DeepSeek V4 Flash', 'deepseek', 'CNY', 0, 0, 0, 1, '阅读解析等场景使用，请填写真实单价'),
('qwen-turbo', 'Qwen Turbo', 'aliyun', 'CNY', 0, 0, 1, 1, '单词批量导入等场景使用，请填写真实单价');

-- 硬删除早期占位脏数据（deepseek-chat / unknown）
DELETE FROM `sys_ai_model` WHERE `model_key` IN ('deepseek-chat', 'unknown');
DELETE FROM `wd_ai_call_log` WHERE `model_name` IN ('deepseek-chat', 'unknown');

-- 将运行时默认模型切到 qwen-turbo（已有库可重复执行）
UPDATE `sys_ai_model` SET `is_default` = 0 WHERE `is_deleted` = 0;
INSERT IGNORE INTO `sys_ai_model`
(`model_key`, `display_name`, `provider`, `currency`, `input_price_per_million`, `output_price_per_million`, `is_default`, `is_enabled`, `remark`)
VALUES
('qwen-turbo', 'Qwen Turbo', 'aliyun', 'CNY', 0, 0, 1, 1, '单词批量导入等场景使用，请填写真实单价');
UPDATE `sys_ai_model` SET `is_default` = 1, `is_enabled` = 1, `display_name` = 'Qwen Turbo', `provider` = 'aliyun',
  `remark` = '定价目录；实际调用以场景配置为准', `update_time` = NOW()
WHERE `model_key` = 'qwen-turbo' AND `is_deleted` = 0;

-- ========== AI 场景 → 模型映射 ==========
CREATE TABLE IF NOT EXISTS `sys_ai_scene` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `scene_key` varchar(32) NOT NULL COMMENT '场景标识，对齐 call_scene',
  `scene_name` varchar(64) NOT NULL COMMENT '场景描述',
  `model_key` varchar(64) NOT NULL COMMENT '对应 sys_ai_model.model_key',
  `sort_num` int NOT NULL DEFAULT 0,
  `is_enabled` tinyint NOT NULL DEFAULT 1,
  `remark` varchar(512) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scene_key` (`scene_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI调用场景与模型映射';

INSERT IGNORE INTO `sys_ai_scene`
(`scene_key`, `scene_name`, `model_key`, `sort_num`, `is_enabled`, `remark`)
VALUES
('feature', '阅读解析（选中文字 AI 功能）', 'deepseek-v4-flash', 1, 1, '阅读器勾选单词/释义等功能 → DeepSeek'),
('word_parse', '单词批量导入', 'qwen-turbo', 2, 1, '单词本批量导入 / AI 补全空缺 → 阿里 Qwen-Turbo'),
('reading_chat', '阅读自由问答', 'deepseek-v4-flash', 3, 1, '阅读器右侧自由提问 → DeepSeek'),
('default', '未匹配场景回退', 'deepseek-v4-flash', 99, 1, '未知 scene 时使用');

UPDATE `sys_ai_scene` SET `model_key` = 'deepseek-v4-flash', `update_time` = NOW()
WHERE `scene_key` IN ('feature', 'reading_chat', 'default') AND `is_deleted` = 0;
UPDATE `sys_ai_scene` SET `model_key` = 'qwen-turbo', `update_time` = NOW()
WHERE `scene_key` = 'word_parse' AND `is_deleted` = 0;

-- =============================================
-- 2026-07-16: 系统基础配置表
-- =============================================
CREATE TABLE IF NOT EXISTS `sys_base_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `config_key` varchar(64) NOT NULL COMMENT '配置键',
  `config_value` varchar(512) NOT NULL COMMENT '配置值',
  `value_type` varchar(16) NOT NULL DEFAULT 'string' COMMENT '值类型: string/int/bool/json',
  `category` varchar(32) NOT NULL DEFAULT 'general' COMMENT '分类',
  `description` varchar(512) NOT NULL DEFAULT '' COMMENT '配置说明',
  `sort_num` int NOT NULL DEFAULT 0 COMMENT '排序',
  `is_enabled` tinyint NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统基础配置表';

INSERT IGNORE INTO `sys_base_config` (`config_key`, `config_value`, `value_type`, `category`, `description`, `sort_num`) VALUES
('study.review_unlock_hour', '7', 'int', 'study',
 '今日到期的复习任务从几点开始出现在「今日复习」列表（0-23）。常见取值 7 或 8；修改后立即生效。', 1),
('study.daily_new_limit_default', '20', 'int', 'study',
 '用户未单独设置时的默认「每日新词」上限（预留，学习改造时使用）。', 2);

-- =============================================
-- 2026-07-16: 学习系统 P1（SM-2 + 强制复习 + 三档反馈）
-- =============================================
ALTER TABLE wd_learn_plan ADD COLUMN interval_days decimal(8,2) NOT NULL DEFAULT 0 COMMENT '当前复习间隔(天)' AFTER review_times;
ALTER TABLE wd_learn_plan ADD COLUMN ease_factor decimal(4,2) NOT NULL DEFAULT 2.50 COMMENT '难度系数' AFTER interval_days;
ALTER TABLE wd_learn_plan ADD COLUMN rep_count int NOT NULL DEFAULT 0 COMMENT '连续成功复习次数' AFTER ease_factor;
ALTER TABLE wd_learn_plan ADD COLUMN lapse_count int NOT NULL DEFAULT 0 COMMENT '遗忘次数' AFTER rep_count;
ALTER TABLE wd_learn_plan ADD COLUMN last_feedback tinyint DEFAULT NULL COMMENT '最近一次反馈' AFTER lapse_count;
ALTER TABLE wd_learn_plan ADD COLUMN last_study_at datetime DEFAULT NULL COMMENT '最近一次正式学习时间' AFTER last_feedback;
ALTER TABLE wd_learn_plan ADD COLUMN priority_score int NOT NULL DEFAULT 0 COMMENT '复习优先级分' AFTER last_study_at;
ALTER TABLE wd_learn_plan ADD COLUMN scheduled_new_date date DEFAULT NULL COMMENT '计划作为今日新词出现的日期' AFTER priority_score;
ALTER TABLE wd_learn_plan ADD COLUMN new_alloc_seq int DEFAULT NULL COMMENT '在该日新词队列中的顺序' AFTER scheduled_new_date;

ALTER TABLE wd_learn_record ADD COLUMN feedback tinyint NOT NULL DEFAULT 1 COMMENT '0忘记 1认识 2模糊' AFTER result;
ALTER TABLE wd_learn_record ADD COLUMN study_mode tinyint NOT NULL DEFAULT 1 COMMENT '1正式每日 2自由回顾' AFTER feedback;
ALTER TABLE wd_learn_record ADD COLUMN affects_schedule tinyint NOT NULL DEFAULT 1 COMMENT '1更新计划 0仅记录' AFTER study_mode;

CREATE TABLE IF NOT EXISTS `wd_user_study_pref` (
  `user_id` bigint NOT NULL,
  `daily_new_limit` int NOT NULL DEFAULT 20,
  `review_unlock_hour` tinyint NOT NULL DEFAULT 7,
  `auto_spread_new` tinyint NOT NULL DEFAULT 1,
  `timezone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户学习偏好';

CREATE TABLE IF NOT EXISTS `wd_study_daily` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `study_date` date NOT NULL,
  `review_total` int NOT NULL DEFAULT 0,
  `review_done` int NOT NULL DEFAULT 0,
  `new_total` int NOT NULL DEFAULT 0,
  `new_done` int NOT NULL DEFAULT 0,
  `review_phase_done` tinyint NOT NULL DEFAULT 0,
  `status` tinyint NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`, `study_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日学习会话汇总';

CREATE TABLE IF NOT EXISTS `wd_study_queue` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `study_date` date NOT NULL,
  `plan_id` bigint NOT NULL,
  `queue_type` varchar(16) NOT NULL,
  `phase` varchar(16) NOT NULL DEFAULT 'review',
  `sort_order` int NOT NULL DEFAULT 0,
  `state` tinyint NOT NULL DEFAULT 0,
  `insert_reason` varchar(32) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_date_type` (`user_id`, `study_date`, `queue_type`, `state`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='当日学习队列';

UPDATE wd_learn_plan SET learn_status = 0 WHERE learn_status = 4;
UPDATE wd_learn_record SET feedback = result WHERE feedback = 1 AND result != 1;

-- 历史数据：current_level -> rep_count，估算 interval_days
UPDATE wd_learn_plan SET rep_count = GREATEST(current_level, 0) WHERE rep_count = 0 AND current_level > 0;
UPDATE wd_learn_plan SET interval_days = GREATEST(DATEDIFF(DATE(next_review_time), CURDATE()), 1)
WHERE interval_days = 0 AND next_review_time IS NOT NULL AND learn_status IN (1, 2);
UPDATE wd_learn_plan SET ease_factor = 2.50 WHERE ease_factor IS NULL OR ease_factor = 0;

-- ========== 阅读收录：单词来源字段（旧版曾用 wd_book_notebook_link，已改为 wd_word_book.book_id）==========
ALTER TABLE `wd_word`
  ADD COLUMN `entry_type` varchar(16) NOT NULL DEFAULT 'word' COMMENT 'word|phrase' AFTER `root_affix`,
  ADD COLUMN `source_book_id` bigint DEFAULT NULL COMMENT '来源书籍ID' AFTER `entry_type`,
  ADD COLUMN `source_snippet` text COMMENT '采集时原文片段' AFTER `source_book_id`,
  ADD COLUMN `source_location` varchar(255) DEFAULT '' COMMENT '定位信息' AFTER `source_snippet`;

-- ========== sys_ai_model：合并场景 + 模型 + 公开 Token 单价 ==========
-- 开发阶段直接重建，不留 legacy 备份表
DROP TABLE IF EXISTS `sys_ai_scene`;
DROP TABLE IF EXISTS `sys_ai_scene_legacy`;
DROP TABLE IF EXISTS `sys_ai_model_legacy`;

SET @has_scene := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_model' AND COLUMN_NAME = 'scene_key'
);
SET @sql := IF(@has_scene = 0,
  'DROP TABLE IF EXISTS sys_ai_model',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `sys_ai_model` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `scene_key` varchar(32) NOT NULL COMMENT '场景标识',
  `scene_name` varchar(64) NOT NULL COMMENT '场景描述',
  `model_key` varchar(64) NOT NULL COMMENT '模型名称',
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
  UNIQUE KEY `uk_scene_key` (`scene_key`),
  KEY `idx_model_key` (`model_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI场景模型与Token单价';

INSERT INTO `sys_ai_model`
(`scene_key`, `scene_name`, `model_key`, `display_name`, `provider`, `currency`,
 `input_price_per_million`, `output_price_per_million`, `sort_num`, `is_enabled`, `remark`)
VALUES
('feature', '阅读解析（选中文字 AI 功能）', 'deepseek-v4-flash', 'DeepSeek V4 Flash', 'deepseek', 'CNY',
 1.0, 2.0, 1, 1, '官方价 https://api-docs.deepseek.com/zh-cn/quick_start/pricing ：输入1元/百万(缓存未命中)、输出2元/百万'),
('word_parse', '单词批量导入', 'qwen-turbo', 'Qwen Turbo', 'aliyun', 'CNY',
 0.3, 0.6, 2, 1, '阿里云百炼 https://help.aliyun.com/zh/model-studio/model-pricing ：输入0.3元/百万、输出0.6元/百万(非思考)'),
('reading_chat', '阅读自由问答', 'deepseek-v4-flash', 'DeepSeek V4 Flash', 'deepseek', 'CNY',
 1.0, 2.0, 3, 1, '同 deepseek-v4-flash 公开价'),
('default', '未匹配场景回退', 'deepseek-v4-flash', 'DeepSeek V4 Flash', 'deepseek', 'CNY',
 1.0, 2.0, 99, 1, '未知 scene 时回退')
ON DUPLICATE KEY UPDATE
  `scene_name` = VALUES(`scene_name`),
  `model_key` = VALUES(`model_key`),
  `display_name` = VALUES(`display_name`),
  `provider` = VALUES(`provider`),
  `currency` = VALUES(`currency`),
  `input_price_per_million` = VALUES(`input_price_per_million`),
  `output_price_per_million` = VALUES(`output_price_per_million`),
  `sort_num` = VALUES(`sort_num`),
  `is_enabled` = 1,
  `is_deleted` = 0,
  `remark` = VALUES(`remark`),
  `update_time` = NOW();

-- ========== 单词本归属：book_id 一对多，废弃 M:N 关联表 ==========
SET @has_wb_book_id := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wd_word_book' AND COLUMN_NAME = 'book_id'
);
SET @sql_wb_book_id := IF(@has_wb_book_id = 0,
  'ALTER TABLE `wd_word_book` ADD COLUMN `book_id` bigint DEFAULT NULL COMMENT ''所属阅读书籍ID，NULL=独立单词本'' AFTER `user_id`, ADD KEY `idx_book_id` (`book_id`)',
  'SELECT 1');
PREPARE stmt_wb_book_id FROM @sql_wb_book_id;
EXECUTE stmt_wb_book_id;
DEALLOCATE PREPARE stmt_wb_book_id;

SET @has_link_table := (
  SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wd_book_notebook_link'
);
SET @sql_backfill := IF(@has_link_table > 0,
  'UPDATE `wd_word_book` wb
   INNER JOIN (
     SELECT notebook_id, MIN(book_id) AS book_id
     FROM `wd_book_notebook_link`
     GROUP BY notebook_id
   ) t ON wb.id = t.notebook_id
   SET wb.book_id = t.book_id
   WHERE wb.book_id IS NULL AND wb.is_deleted = 0',
  'SELECT 1');
PREPARE stmt_backfill FROM @sql_backfill;
EXECUTE stmt_backfill;
DEALLOCATE PREPARE stmt_backfill;

DROP TABLE IF EXISTS `wd_book_notebook_link`;

-- ========== 已删单词本的孤儿数据清理（一次性修复）==========
UPDATE `wd_word` w
INNER JOIN `wd_word_book` b ON b.id = w.notebook_id AND b.is_deleted = 1
SET w.is_deleted = 1, w.update_time = NOW()
WHERE w.is_deleted = 0;

UPDATE `wd_learn_plan` p
INNER JOIN `wd_word_book` b ON b.id = p.notebook_id AND b.is_deleted = 1
SET p.is_deleted = 1, p.update_time = NOW()
WHERE p.is_deleted = 0;

UPDATE `wd_learn_plan` p
INNER JOIN `wd_word` w ON w.id = p.word_id AND w.is_deleted = 1
SET p.is_deleted = 1, p.update_time = NOW()
WHERE p.is_deleted = 0;

DELETE q FROM `wd_study_queue` q
LEFT JOIN `wd_learn_plan` p ON p.id = q.plan_id AND p.is_deleted = 0
WHERE q.state = 0 AND p.id IS NULL;

-- ========== PDF 批注表 ==========
CREATE TABLE IF NOT EXISTS `wd_pdf_markup` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `book_id` bigint NOT NULL COMMENT '所属书籍ID',
  `user_id` bigint NOT NULL COMMENT '用户ID（仅本人可见）',
  `page_num` int NOT NULL COMMENT 'PDF页码(从1起)',
  `markup_type` varchar(16) NOT NULL COMMENT 'pen|rect|text',
  `geometry_json` text NOT NULL COMMENT '归一化坐标JSON(0~1)',
  `color` varchar(16) NOT NULL DEFAULT '#E53935' COMMENT '颜色',
  `note_text` text COMMENT '文字笔记内容',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_book_user` (`book_id`, `user_id`),
  KEY `idx_book_page` (`book_id`, `user_id`, `page_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PDF批注表';


-- ========== 管理员角色 ==========
ALTER TABLE wd_user ADD COLUMN role varchar(16) NOT NULL DEFAULT 'user' COMMENT '角色 user|admin' AFTER member_tier;
-- 将指定账号升为管理员（按需修改用户名后执行）：
-- UPDATE wd_user SET role = 'admin' WHERE username = 'your_admin' AND is_deleted = 0;

-- ========== AI 场景系统提示词 ==========
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI场景系统提示词';

INSERT IGNORE INTO `sys_ai_scene_prompt` (`scene_key`, `locale`, `scene_name`, `system_prompt`, `remark`, `status`, `sort_num`) VALUES
('word_parse', 'zh-CN', '单词解析',
'{global_constraint}
你是一个专业的英语词典助手。用户会给你一个英文单词/短语列表（JSON数组），请为每个词条补全信息，并以严格的 JSON 数组格式返回。必须返回完整的 JSON 数组，不要有任何额外的解释文字。',
'占位符 {global_constraint}=按语言注入回复规则；未配置其它语言时回退代码内置模板', 1, 1),
('reading_chat', 'zh-CN', '阅读对话',
'{global_constraint}
你是一个专业的英语阅读助手。用户正在阅读英文原著，请用{language}回答用户的问题。
用户当前选中的上下文是："{context}"',
'占位符 {global_constraint}/{language}/{locale}/{context}', 1, 2),
('feature', 'zh-CN', '阅读功能分析',
'{global_constraint}
你是一个专业的英语阅读助手。
{feature_prompts}
用户当前选中的上下文是："{context}"
请直接给出分析结果，不要添加开场白、解释说明或总结。保持简洁。',
'占位符 {global_constraint}/{language}/{feature_prompts}/{context}', 1, 3);

-- 已有环境：把旧版硬编码「始终用中文」换成可按请求语言注入的占位符
UPDATE `sys_ai_scene_prompt`
SET `system_prompt` = REPLACE(
  `system_prompt`,
  '【重要全局规则】请始终使用中文回复用户。无论用户输入何种语言，所有释义、例句翻译、解析说明都必须用中文。英文原文和音标除外。',
  '{global_constraint}'
),
`update_time` = NOW()
WHERE `is_deleted` = 0
  AND `system_prompt` LIKE '%【重要全局规则】请始终使用中文回复用户%';

-- =============================================
-- 2026-07-17: 模型目录与场景绑定拆分
-- 运行时由 DatabaseInitConfig.migrateSplitAiModelAndScene 自动迁移；
-- 新库直接使用 schema.sql 中的 sys_ai_model（无 scene_key）+ sys_ai_scene。
-- =============================================

-- =============================================
-- 2026-07-18: AI 供应商接入点（后台可配 base-url / api-key）
-- 运行时由 DatabaseInitConfig.ensureAiProviderTable 自动建表；亦可手动执行。
-- =============================================
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI供应商接入点';

INSERT IGNORE INTO `sys_ai_provider`
(`provider_key`, `display_name`, `base_url`, `api_key`, `sort_num`, `is_enabled`, `remark`)
VALUES
('deepseek', 'DeepSeek', 'https://api.deepseek.com', '', 1, 1, '密钥可由 yml 引导或后台填写'),
('aliyun', '阿里云百炼', 'https://dashscope.aliyuncs.com/compatible-mode', '', 2, 1, '密钥可由 yml 引导或后台填写'),
('modelscope', '魔塔 ModelScope', 'https://api-inference.modelscope.cn', '', 3, 1, '密钥可由 yml 引导或后台填写');

-- =============================================
-- 2026-07-18: AI 线路地区（与语言分离）
-- 运行时由 DatabaseInitConfig.migrateAiSceneRegion 自动迁移。
-- =============================================
ALTER TABLE wd_user ADD COLUMN preferred_ai_region varchar(16) NOT NULL DEFAULT 'auto' COMMENT 'AI线路偏好 auto|cn|global' AFTER preferred_locale;
ALTER TABLE sys_ai_scene ADD COLUMN region varchar(16) NOT NULL DEFAULT 'cn' COMMENT '路由区 cn|global' AFTER scene_name;
-- 随后需：为每条 cn 场景补 global 行；DROP uk_scene_key；ADD uk_scene_region (scene_key, region)
