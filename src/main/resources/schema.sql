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
  `completion_tokens` int NOT NULL DEFAULT 0 COMMENT '返回token数量（模型返回 usage）',
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
('aliyun', '阿里云百炼', 'https://dashscope.aliyuncs.com/compatible-mode', '', 2, 1, '密钥可由 yml 引导或后台填写'),
('modelscope', '魔塔 ModelScope', 'https://api-inference.modelscope.cn', '', 3, 1, '密钥可由 yml 引导或后台填写');

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
CREATE TABLE IF NOT EXISTS `wd_book` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '所属用户ID',
  `title` varchar(256) NOT NULL COMMENT '书名',
  `author` varchar(128) DEFAULT '' COMMENT '作者',
  `cover_url` varchar(512) DEFAULT '' COMMENT '封面图URL',
  `file_path` varchar(512) NOT NULL COMMENT '文件在服务器上的存储路径',
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
 '用户未单独设置时的默认「每日新词」上限（预留，学习改造时使用）。', 2);

-- =============================================
-- AI 阅读功能配置表（可动态扩展的功能选项）
-- =============================================
CREATE TABLE IF NOT EXISTS `sys_ai_feature` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `feature_key` varchar(32) NOT NULL COMMENT '功能标识，如 word, meaning, collocation',
  `feature_name` varchar(32) NOT NULL COMMENT '功能名称，中文源文，用户可见',
  `description` varchar(255) NOT NULL COMMENT '功能描述，中文源文',
  `name_i18n` json DEFAULT NULL COMMENT '标签多语言 JSON: en/id/th/vi',
  `description_i18n` json DEFAULT NULL COMMENT '描述多语言 JSON: en/id/th/vi',
  `prompt` text COMMENT 'AI提示词，传递给大模型的详细指令',
  `sort_num` int NOT NULL DEFAULT 0 COMMENT '排序权重',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '修改人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除 1已删除 0未删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_feature_key` (`feature_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI阅读功能配置表';

-- 种子数据：默认AI阅读功能
INSERT IGNORE INTO `sys_ai_feature` (`feature_key`, `feature_name`, `description`, `prompt`, `sort_num`) VALUES
('word', '单词', '讲解重难点单词的词性、含义和用法', '在 JSON 键 \"word\" 写词条数组。仅解析场景中的目标词。元素：word, meaning, usPhonetic, ukPhonetic, rootAffix(可空)。例：{\"word\":[{\"word\":\"reviewer\",\"meaning\":\"n. 评论家\",\"usPhonetic\":\"/…/\",\"ukPhonetic\":\"/…/\",\"rootAffix\":\"re- + view + -er\"}]}', 1),
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
  `title` varchar(256) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除 1已删除 0未删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_book` (`user_id`, `book_id`),
  KEY `idx_user_book_time` (`user_id`, `book_id`, `update_time` DESC)
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
