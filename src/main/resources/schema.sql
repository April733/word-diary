-- =============================================
-- Word Diary 数据库初始化脚本
-- =============================================

-- 用户表
CREATE TABLE IF NOT EXISTS `wd_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户主键',
  `username` varchar(32) NOT NULL COMMENT '账号',
  `nickname` varchar(32) DEFAULT '' COMMENT '昵称',
  `password` varchar(64) NOT NULL COMMENT '密码',
  `email` varchar(128) DEFAULT '' COMMENT '邮箱地址',
  `phone` varchar(20) DEFAULT '' COMMENT '手机号',
  `role` varchar(16) NOT NULL DEFAULT 'user' COMMENT '角色 user|admin',
  `preferred_locale` varchar(10) NOT NULL DEFAULT 'zh-CN' COMMENT '偏好语言',
  `preferred_ai_region` varchar(16) NOT NULL DEFAULT 'auto' COMMENT 'AI线路偏好 auto|cn|global',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '修改人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除 1已删除 0未删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 单词本表（book_id 为空=独立单词本；非空=仅属于该阅读书籍，不可再属其他书）
CREATE TABLE IF NOT EXISTS `wd_word_book` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '单词本ID',
  `user_id` bigint NOT NULL COMMENT '所属用户ID',
  `book_id` bigint DEFAULT NULL COMMENT '所属阅读书籍ID，NULL=独立单词本',
  `source_type` varchar(16) NOT NULL DEFAULT '阅读' COMMENT '来源分类：阅读|听力',
  `book_name` varchar(64) NOT NULL COMMENT '单词本名称',
  `book_desc` varchar(255) DEFAULT '' COMMENT '单词本简介',
  `sort_num` int NOT NULL DEFAULT 0 COMMENT '排序权重',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1参与每日学习 0不参与',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '修改人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除 1已删除 0未删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_book_id` (`book_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单词本表';

-- 单词库（个人私有单词表）
CREATE TABLE IF NOT EXISTS `wd_word` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '单词主键',
  `notebook_id` bigint NOT NULL COMMENT '归属单词本ID',
  `word_proto` varchar(64) NOT NULL COMMENT '单词/短语原型',
  `us_phonetic` varchar(64) DEFAULT '' COMMENT '美式音标',
  `uk_phonetic` varchar(64) DEFAULT '' COMMENT '英式音标',
  `part_speech_mean` varchar(255) DEFAULT '' COMMENT '词性+中文含义',
  `sentence_example` text COMMENT '例句',
  `root_affix` varchar(512) DEFAULT '' COMMENT '词根词缀解析',
  `entry_type` varchar(16) NOT NULL DEFAULT 'word' COMMENT 'word|phrase',
  `source_book_id` bigint DEFAULT NULL COMMENT '来源书籍ID',
  `source_snippet` text COMMENT '采集时原文片段',
  `source_location` varchar(255) DEFAULT '' COMMENT '定位信息',
  `remark` varchar(512) DEFAULT '' COMMENT '用户备注',
  `is_public` tinyint NOT NULL DEFAULT 1 COMMENT '是否公开 1公开 0私有',
  `user_id` bigint DEFAULT NULL COMMENT '创建用户ID，NULL表示系统预置',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '学习状态: 0未学 1~6复习阶梯 7熟记',
  `status_data` datetime DEFAULT NULL COMMENT '进入当前status的时间；0时为创建时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '修改人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除 1已删除 0未删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_word_proto` (`word_proto`),
  KEY `idx_notebook_id` (`notebook_id`),
  KEY `idx_user_status_data` (`user_id`, `status`, `status_data`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单词库';

-- =============================================
-- 系统基础配置（全局可调，含说明）
-- =============================================
CREATE TABLE IF NOT EXISTS `sys_base_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `config_key` varchar(64) NOT NULL COMMENT '配置键，如 study.review_unlock_hour',
  `config_value` varchar(512) NOT NULL COMMENT '配置值',
  `value_type` varchar(16) NOT NULL DEFAULT 'string' COMMENT '值类型: string/int/bool/json',
  `category` varchar(32) NOT NULL DEFAULT 'general' COMMENT '分类: study/general/ai',
  `description` varchar(512) NOT NULL DEFAULT '' COMMENT '配置说明（用途、取值范围）',
  `sort_num` int NOT NULL DEFAULT 0 COMMENT '同分类内排序',
  `is_enabled` tinyint NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`),
  KEY `idx_category` (`category`, `sort_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统基础配置表';

-- 用户学习偏好
CREATE TABLE IF NOT EXISTS `wd_user_study_pref` (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `daily_new_limit` int NOT NULL DEFAULT 20 COMMENT '每日新词上限',
  `review_unlock_hour` tinyint NOT NULL DEFAULT 7 COMMENT '当日复习最早出现时刻(时)',
  `auto_spread_new` tinyint NOT NULL DEFAULT 0 COMMENT '已废弃：禁止均摊到未来',
  `timezone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户学习偏好';

-- 大模型调用历史记录
CREATE TABLE IF NOT EXISTS `wd_ai_call_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '调用用户ID',
  `model_name` varchar(64) NOT NULL COMMENT '模型名称，如 deepseek-v4-flash / qwen-turbo',
  `call_scene` varchar(32) NOT NULL DEFAULT '' COMMENT '调用场景: word_parse / reading_chat / feature',
  `input_text` text COMMENT '用户原始输入（选中文本/单词列表/提问）',
  `input_char_count` int NOT NULL DEFAULT 0 COMMENT '用户原始输入字符数',
  `input_word_count` int NOT NULL DEFAULT 0 COMMENT '用户原始输入词数（中英混合）',
  `assembled_prompt_chars` int NOT NULL DEFAULT 0 COMMENT '拼装后 system+user 提示词总字符数',
  `prompt_tokens` int NOT NULL DEFAULT 0 COMMENT '提示词token数量（模型返回 usage）',
  `completion_tokens` int NOT NULL DEFAULT 0 COMMENT '返回token数量（模型返回 usage，含 reasoning + content）',
  `reasoning_tokens` int NOT NULL DEFAULT 0 COMMENT '思考token数量（reasoning tokens，仅推理模型有）',
  `content_tokens` int NOT NULL DEFAULT 0 COMMENT '可见内容token数量（= completionTokens - reasoningTokens，给用户看的实际内容）',
  `total_tokens` int NOT NULL DEFAULT 0 COMMENT '总token数量',
  `estimated_cost` decimal(14,8) NOT NULL DEFAULT 0 COMMENT '按当时模型单价估算的成本（与 currency 一致）',
  `system_prompt` text COMMENT '系统提示词（拼装后）',
  `user_prompt` text COMMENT '用户提示词（拼装后）',
  `response_data` mediumtext COMMENT '大模型返回的原始数据',
  `duration_ms` int DEFAULT NULL COMMENT '调用耗时（毫秒）',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '修改人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除 1已删除 0未删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_call_scene` (`call_scene`),
  KEY `idx_model_name` (`model_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='大模型调用历史记录';

-- =============================================
-- AI 供应商接入点（base-url / api-key，后台可配）
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
('aliyun', '阿里云百炼', 'https://dashscope.aliyuncs.com/compatible-mode', '', 2, 1, '密钥可由 yml 引导或后台填写');

-- =============================================
-- AI 模型目录（与场景解耦，可被多个场景复用）
-- =============================================
CREATE TABLE IF NOT EXISTS `sys_ai_model` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `model_key` varchar(64) NOT NULL COMMENT '模型名称，对齐调用日志 model_name',
  `display_name` varchar(128) NOT NULL DEFAULT '' COMMENT '模型展示名',
  `provider` varchar(64) NOT NULL DEFAULT '' COMMENT '对齐 sys_ai_provider.provider_key',
  `currency` varchar(8) NOT NULL DEFAULT 'CNY' COMMENT '公开价币种，统一 CNY',
  `input_price_per_million` decimal(14,6) NOT NULL DEFAULT 0 COMMENT '输入单价：每百万 prompt tokens',
  `output_price_per_million` decimal(14,6) NOT NULL DEFAULT 0 COMMENT '输出单价：每百万 completion tokens',
  `sort_num` int NOT NULL DEFAULT 0 COMMENT '排序',
  `is_enabled` tinyint NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `remark` varchar(512) DEFAULT '' COMMENT '价格来源备注',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_key` (`model_key`),
  KEY `idx_enabled` (`is_enabled`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI模型目录与Token单价';

INSERT IGNORE INTO `sys_ai_model`
(`model_key`, `display_name`, `provider`, `currency`,
 `input_price_per_million`, `output_price_per_million`, `sort_num`, `is_enabled`, `remark`)
VALUES
('deepseek-v4-flash', 'DeepSeek V4 Flash', 'deepseek', 'CNY',
 1.0, 2.0, 1, 1, '官方价：输入1元/百万(缓存未命中)、输出2元/百万'),
('qwen-turbo', 'Qwen Turbo', 'aliyun', 'CNY',
 0.3, 0.6, 2, 1, '阿里云百炼中国内地：输入0.3元/百万、输出0.6元/百万(非思考)');

-- =============================================
-- AI 场景 → 模型绑定（多场景可共用同一模型）
-- =============================================
CREATE TABLE IF NOT EXISTS `sys_ai_scene` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `scene_key` varchar(32) NOT NULL COMMENT '场景标识，对齐 call_scene',
  `scene_name` varchar(64) NOT NULL COMMENT '场景描述',
  `region` varchar(16) NOT NULL DEFAULT 'cn' COMMENT '路由区 cn|global',
  `model_key` varchar(64) NOT NULL COMMENT '对应 sys_ai_model.model_key',
  `temperature` decimal(4,2) DEFAULT NULL COMMENT '采样温度，空=供应商默认',
  `max_tokens` int DEFAULT NULL COMMENT '最大生成token，空=默认',
  `thinking_mode` varchar(16) DEFAULT NULL COMMENT '思考模式 enabled|disabled|空=默认',
  `thinking_budget` int DEFAULT NULL COMMENT '思考token预算',
  `reasoning_effort` varchar(16) DEFAULT NULL COMMENT 'reasoning_effort low|medium|high',
  `response_format` varchar(32) DEFAULT NULL COMMENT 'response_format json_object|text|空=不传',
  `sort_num` int NOT NULL DEFAULT 0,
  `is_enabled` tinyint NOT NULL DEFAULT 1,
  `remark` varchar(512) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scene_region` (`scene_key`, `region`),
  KEY `idx_model_key` (`model_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI调用场景与模型映射（按地区）';

-- 场景种子由 DatabaseInitConfig（先迁移 region/唯一键，再 INSERT）写入，
-- 避免已有库仍为 uk_scene_key 时，schema.sql 插入 cn+global 双行失败。

-- =============================================
-- 书籍表（阅读器）
-- =============================================
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='共享书籍资源表';

CREATE TABLE IF NOT EXISTS `wd_book` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '所属用户ID',
  `title` varchar(256) NOT NULL COMMENT '书名',
  `author` varchar(128) DEFAULT '' COMMENT '作者',
  `cover_url` varchar(512) DEFAULT '' COMMENT '封面图URL',
  `file_path` varchar(512) NOT NULL COMMENT '文件在服务器上的存储路径',
  `resource_id` bigint DEFAULT NULL COMMENT '共享资源ID',
  `file_type` varchar(16) NOT NULL DEFAULT 'epub' COMMENT '文件类型 epub/pdf/txt',
  `file_size` bigint DEFAULT 0 COMMENT '文件大小(字节)',
  `total_chapters` int DEFAULT 0 COMMENT '总章节数',
  `current_chapter` int DEFAULT 0 COMMENT '当前阅读章节索引',
  `current_cfi` varchar(256) DEFAULT '' COMMENT '当前阅读位置(EPUB CFI)',
  `progress_percent` decimal(5,2) DEFAULT 0.00 COMMENT '阅读进度百分比',
  `last_read_at` datetime DEFAULT NULL COMMENT '最近阅读时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '修改人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除 1已删除 0未删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_resource_id` (`resource_id`),
  KEY `idx_user_deleted_read` (`user_id`, `is_deleted`, `last_read_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='书籍表';

-- =============================================
-- 划线/笔记表（阅读器）
-- =============================================
CREATE TABLE IF NOT EXISTS `wd_annotation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `book_id` bigint NOT NULL COMMENT '所属书籍ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `chapter_index` int NOT NULL DEFAULT 0 COMMENT '章节索引',
  `cfi_start` varchar(256) NOT NULL COMMENT 'EPUB CFI 起始位置',
  `cfi_end` varchar(256) DEFAULT '' COMMENT 'EPUB CFI 结束位置',
  `text_content` text NOT NULL COMMENT '被划线的原文内容',
  `note` text COMMENT '用户笔记',
  `color` varchar(16) DEFAULT '#FFD700' COMMENT '高亮颜色',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '修改人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除 1已删除 0未删除',
  PRIMARY KEY (`id`),
  KEY `idx_book_user` (`book_id`, `user_id`),
  KEY `idx_chapter` (`book_id`, `chapter_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='划线笔记表';

-- =============================================
-- PDF 批注表（Canvas 覆盖层，坐标归一化 0~1，不修改原始 PDF）
-- =============================================
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

-- =============================================
-- 用户阅读设置表（阅读器偏好）
-- =============================================
CREATE TABLE IF NOT EXISTS `wd_reader_settings` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '所属用户ID',
  `font_size` int NOT NULL DEFAULT 16 COMMENT '字体大小(px)',
  `line_height` decimal(3,1) NOT NULL DEFAULT 1.6 COMMENT '行间距',
  `background_color` varchar(16) NOT NULL DEFAULT '#ffffff' COMMENT '背景颜色',
  `text_color` varchar(16) NOT NULL DEFAULT '#1a1a2e' COMMENT '文字颜色',
  `flow` varchar(16) NOT NULL DEFAULT 'paginated' COMMENT '翻页方式: paginated/scrolled-doc',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '修改人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除 1已删除 0未删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户阅读设置表';

-- =============================================
-- 种子数据：系统基础配置
-- =============================================
INSERT IGNORE INTO `sys_base_config` (`config_key`, `config_value`, `value_type`, `category`, `description`, `sort_num`) VALUES
('study.review_unlock_hour', '7', 'int', 'study',
 '今日到期的复习任务从几点开始出现在「今日复习」列表（0-23）。常见取值 7 或 8；修改后立即生效。', 1),
('study.daily_new_limit_default', '20', 'int', 'study',
 '用户未单独设置时的默认「每日新词」上限（预留，学习改造时使用）。', 2),
('study.idle_timeout_seconds', '60', 'int', 'study',
 '阅读等场景无操作超过该秒数视为离线并结段（不对用户暴露，默认60）。', 3),
('study.min_valid_seconds', '120', 'int', 'study',
 '进入埋点页后前端先本地计时；停留满该秒数才写入时长表，未满则丢弃（不对用户暴露，默认120）。', 4);

-- =============================================
-- AI 阅读功能配置表（已废弃：功能目录与 prompt 改由前端常量 + 场景提示词承担；表保留不 DROP）
-- =============================================
CREATE TABLE IF NOT EXISTS `sys_ai_feature` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `feature_key` varchar(32) NOT NULL COMMENT '功能标识，如 word, meaning, collocation',
  `feature_name` varchar(32) NOT NULL COMMENT '功能名称，中文源文，用户可见',
  `description` varchar(255) NOT NULL COMMENT '功能描述，中文源文',
  `name_i18n` json DEFAULT NULL COMMENT '标签多语言 JSON: en/id/th/vi',
  `description_i18n` json DEFAULT NULL COMMENT '描述多语言 JSON: en/id/th/vi',
  `prompt` text COMMENT 'AI提示词（废弃，代码不再读取）',
  `sort_num` int NOT NULL DEFAULT 0 COMMENT '排序权重',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '修改人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除 1已删除 0未删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_feature_key` (`feature_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI阅读功能配置表（废弃，代码路径已停用）';

-- 种子数据：历史默认功能（运行时不再读取；保留兼容旧库）
INSERT IGNORE INTO `sys_ai_feature` (`feature_key`, `feature_name`, `description`, `prompt`, `sort_num`) VALUES
('word', '单词', '讲解重难点单词的词性、含义和用法', '在 JSON 键 \"word\" 写词条数组。仅解析场景中的目标词。元素：word, meaning, usPhonetic, ukPhonetic, rootAffix(可空)。【词性释义格式】meaning=英文词性缩写+\". \"+释义；多词性只用中文分号「；」分隔。允许：n/v/adj/adv/prep/conj/pron/num/int/phr。例：n. 评论家；v. 评论。禁止「名词，意为…」、禁止逗号/斜杠串联。短语用 phr. 释义。例：{\"word\":[{\"word\":\"reviewer\",\"meaning\":\"n. 评论家\",\"usPhonetic\":\"/…/\",\"ukPhonetic\":\"/…/\",\"rootAffix\":\"re- + view + -er\"}]}', 1),
('meaning', '释义', '翻译和释义选中的句子或段落', '在 JSON 键 \"meaning\" 写整句/段落译文（字符串）。例：{\"meaning\":\"…\"}', 2),
('collocation', '固搭', '分析固定搭配和短语用法', '在 JSON 键 \"collocation\" 写固定搭配数组。元素：word, meaning。例：{\"collocation\":[{\"word\":\"examples of\",\"meaning\":\"…的例子\"}]}', 3),
('sentence_pattern', '句式', '分析句式结构与句子骨架', '在 JSON 顶层键 \"sentence_pattern\" 按三行填写：句式 / 句子类型 / 功能。例：{\"sentence_pattern\":\"句式：…\\\\n句子类型：…\\\\n功能：…\"}', 4),
('phonetic', '连读音标', '分析发音技巧：连读、吞音、重读、不发音字母等', '在 JSON 键 \"phonetic\" 写连读/吞音/重读等发音说明（字符串）。例：{\"phonetic\":\"…\"}', 5),
('root_affix', '词根词缀', '分析词根词缀构成和词义演变', '在 JSON 键 \"root_affix\" 写词条数组，含 rootAffix。仅解析场景中的目标词。例：{\"root_affix\":[{\"word\":\"…\",\"meaning\":\"…\",\"rootAffix\":\"…\"}]}', 6),
('example', '例句', '提供类似例句帮助理解用法', '在 JSON 键 \"example\" 写例句/用法数组。例：{\"example\":[{\"word\":\"…\",\"meaning\":\"例句与说明\"}]}', 7),
('grammar', '语法', '分析语法点（时态、语态、从句类型等）', '在 JSON 顶层键 \"grammar\" 填写语法分析（可用\\\\n）。例：{\"grammar\":\"时态…\\\\n语态…\\\\n主谓结构…\"}', 8);

-- =============================================
-- 用户 AI 功能偏好表（记住用户勾选了哪些功能）
-- =============================================
CREATE TABLE IF NOT EXISTS `wd_user_ai_preference` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `feature_keys` varchar(1024) NOT NULL DEFAULT '' COMMENT '用户启用的功能feature_key列表，JSON数组，如 ["word","collocation","example"]',
  `feature_order` varchar(1024) NOT NULL DEFAULT '' COMMENT '用户自定义功能排序，JSON数组，如 ["word","example","collocation"]',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户AI功能偏好表';

-- =============================================
-- AI 对话会话表
-- =============================================
CREATE TABLE IF NOT EXISTS `wd_ai_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '所属用户ID',
  `book_id` bigint DEFAULT NULL COMMENT '关联书籍ID，按书隔离会话',
  `grade_id` bigint DEFAULT NULL COMMENT '翻译批改记录ID，按练习记录隔离答疑',
  `title` varchar(256) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除 1已删除 0未删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_book` (`user_id`, `book_id`),
  KEY `idx_user_book_time` (`user_id`, `book_id`, `update_time` DESC),
  KEY `idx_user_grade` (`user_id`, `grade_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话会话表';

-- =============================================
-- AI 对话消息表
-- =============================================
CREATE TABLE IF NOT EXISTS `wd_ai_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_id` bigint NOT NULL COMMENT '所属会话ID',
  `parent_id` bigint DEFAULT NULL COMMENT '关联的问题ID（assistant消息指向对应user消息的id）',
  `role` varchar(20) NOT NULL COMMENT '角色: user / assistant',
  `content` text NOT NULL COMMENT '消息内容',
  `feature_key` varchar(255) DEFAULT NULL COMMENT '触发此消息的AI功能标识',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除 1已删除 0未删除',
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话消息表';

-- =============================================
-- 用户反馈表
-- =============================================
CREATE TABLE IF NOT EXISTS `wd_feedback` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID，NULL表示匿名用户',
  `username` varchar(128) NOT NULL COMMENT '用户名（匿名用户填写的昵称或邮箱）',
  `reporter_email` varchar(128) DEFAULT '' COMMENT '反馈者邮箱',
  `subject` varchar(256) DEFAULT '' COMMENT '反馈标题',
  `description` text NOT NULL COMMENT '反馈内容',
  `error_detail` text COMMENT '错误详情',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '处理状态：0待处理 1已处理',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除 1已删除 0未删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户反馈表';

-- =============================================
-- 侵权 / 违法内容投诉通知（合规留存）
-- =============================================
CREATE TABLE IF NOT EXISTS `wd_abuse_notice` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `notice_no` varchar(32) NOT NULL COMMENT '通知编号（对外回执）',
  `notice_type` varchar(16) NOT NULL COMMENT 'copyright版权投诉 | illegal违法举报',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0待审 1合格待处理 2已处理 3驳回 4关闭',
  `submitter_user_id` bigint DEFAULT NULL COMMENT '提交账号用户ID（登录提交时写入）',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='侵权投诉/违法举报通知';

CREATE TABLE IF NOT EXISTS `wd_abuse_notice_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `notice_id` bigint NOT NULL COMMENT '通知ID',
  `event_type` varchar(32) NOT NULL COMMENT 'received|qualified|reviewing|removed|restricted|rejected|closed|note',
  `event_detail` text COMMENT '事件详情/证据摘要',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人，NULL=系统/投诉人提交',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件时间点',
  PRIMARY KEY (`id`),
  KEY `idx_notice_time` (`notice_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='侵权投诉处理时间线（证据留存）';

-- =============================================
-- 雅思写作练习稿
-- =============================================
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='雅思写作练习稿';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='写作批改历史';

-- =============================================
-- 学习时长分段（进入/暂停/空闲/退出各结一段）
-- =============================================
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习时长分段';

-- =============================================
-- 跟读音频（ASR 台词，与阅读无关）
-- =============================================
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跟读音频篇（ASR台词）';

-- =============================================
-- AI 场景系统提示词（按 locale 可配置，覆盖代码默认）
-- =============================================
CREATE TABLE IF NOT EXISTS `sys_ai_scene_prompt` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `scene_key` varchar(32) NOT NULL COMMENT '场景: word_parse/reading_chat/feature',
  `locale` varchar(10) NOT NULL DEFAULT 'zh-CN' COMMENT '语言',
  `scene_name` varchar(64) NOT NULL DEFAULT '' COMMENT '场景展示名',
  `system_prompt` mediumtext NOT NULL COMMENT '系统提示词，feature/reading_chat 可用 {context}；feature 可用 {feature_prompts}',
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

-- 初始化 AI 场景提示词（包含 shadowing_chat 跟读对话场景）
INSERT IGNORE INTO `sys_ai_scene_prompt`
(`scene_key`, `locale`, `scene_name`, `system_prompt`, `remark`, `status`, `sort_num`)
VALUES
('word_parse', 'zh-CN', '单词解析', '你是一个专业的英语词汇学习助手。请用中文解释用户提供的单词或短语，包括：\n\n- 音标（美式/英式）\n- 词性和基本含义\n- 常见搭配和用法\n- 例句（至少2个）\n- 词根词缀解析（如果有）\n\n目标词：{context}\n\n请简洁清晰地回答，帮助用户理解和记忆这个单词。', '单词解析场景', 1, 1),
('reading_chat', 'zh-CN', '阅读对话', '你是一个专业的英语阅读与语言学习助手。用户正在阅读英文内容，请用中文回答与语言学习相关的问题。\n\n你可以讲解：\n- 文章中出现的单词、短语、语法结构\n- 语境中的用法和搭配\n- 文化背景和表达习惯\n\n当前阅读的文本是：{context}\n\n请根据用户的问题，用简洁清晰的方式解答。', '阅读对话场景', 1, 2),
('translation_chat', 'zh-CN', '翻译答疑', '你是 Aiko，翻译教练。回答语言学习相关问题\n（本条练习、词汇辨析、语法、用法、翻译技巧）。\n一般英语学习问答可以回答；仅拒绝明显跑题的请求。\n请用中文回复。', '翻译答疑场景', 1, 3),
('translation_generate', 'zh-CN', '翻译出题', '你根据学习者已掌握的词汇，设计雅思风格的英语翻译练习题。\n受众：非英语母语学习者。\n请出一道练习：自然的一句或一小段，复用给定单词/短语，主题接近雅思学术\n（教育、环境、科技、城市生活、工作、健康等）。\n只返回一个 JSON 对象（不要 Markdown 代码围栏），键包括：\ndirection, theme, promptText, referenceAnswer, targetWords。\n优先使用给定词汇；篇幅适中；避免超纲生僻词。\n严格语言锁（覆盖全局「只用一种语言」规则）：\npromptText = 方向的源语言；referenceAnswer = 方向的目标语言；\n不要把教练用语混进 promptText 或 referenceAnswer。', '翻译出题场景', 1, 4),
('writing_grade', 'zh-CN', '写作批改', '你是雅思学术写作考官兼写作教练，题型为 {task_type}。\n请批改作文，并产出保留学生原意的润色稿。\n同时提供同题高分范文（标明为范文，不是润色稿）。\n\n只返回一个 JSON 对象（不要 Markdown 代码围栏），键包括：\n- overallBand（数字，0–9，可 0.5 步进）\n- taskAchievement/taskResponse（数字）\n- coherence（数字）\n- lexical（数字）\n- grammar（数字）\n- summary（短评）\n- priorities（3–5 条可执行改进建议，影响最大的在前）\n- annotations（[{excerpt, issue, fix}]）\n- polishedEssay（学生作文的改进版）\n- modelEssay（同题高分范文）\n未读完作文不得臆造分数。评语须具体可执行。', '写作批改场景', 1, 5),
('feature', 'zh-CN', '功能分析', '你是一个专业的英语语言学习助手。用户选中的文本可能包含多个需要分析的维度。\n\n请根据请求的功能标签，对以下文本进行多维度分析：\n{context}\n\n功能要求：{feature_prompts}\n\n请严格按照功能要求的格式返回结果，每个功能独立分析，保持结构清晰。', '功能分析场景', 1, 6),
('shadowing_chat', 'zh-CN', '跟读对话', '你是一个专业的英语听力学习助手。用户正在跟读一段英文音频，请用中文回答与语言学习相关的问题。\n\n你可以讲解：\n- 台词中出现的单词、短语、语法结构\n- 发音要点和连读现象\n- 语境中的用法和搭配\n- 听力理解技巧\n\n当前跟读的音频台词是：{context}\n\n请根据用户的问题，用简洁清晰的方式解答，帮助用户更好地理解和掌握这段内容。', '跟读音频AI问答场景', 1, 7),
('shadowing_asr', 'zh-CN', '跟读ASR', '你是一个专业的英语语音识别助手。请对用户提供的音频转录结果进行校对和优化。\n\n原始转录：{context}\n\n请：\n- 修正明显的识别错误\n- 优化标点符号\n- 保持原文语义不变\n\n返回优化后的文本。', '跟读ASR场景', 1, 8);

-- =============================================
-- 系统语录（激励语，客户端随机展示）
-- =============================================
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统语录（激励语）';

-- =============================================
-- 自学小 Tips（一问一答，管理员维护，客户端暂不展示）
-- =============================================
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自学小Tips（一问一答）';
