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
  `model_name` varchar(64) NOT NULL COMMENT '模型名称，如 deepseek-chat',
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
