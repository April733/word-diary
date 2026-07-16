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
  `is_member` tinyint NOT NULL DEFAULT 0 COMMENT '是否会员 1是 0否',
  `member_tier` int NOT NULL DEFAULT 1 COMMENT '会员等级，关联 sys_member_tier.tier',
  `preferred_locale` varchar(10) NOT NULL DEFAULT 'zh-CN' COMMENT '偏好语言',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '修改人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除 1已删除 0未删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 单词本表
CREATE TABLE IF NOT EXISTS `wd_word_book` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '单词本ID',
  `user_id` bigint NOT NULL COMMENT '所属用户ID',
  `book_name` varchar(64) NOT NULL COMMENT '单词本名称',
  `book_desc` varchar(255) DEFAULT '' COMMENT '单词本简介',
  `sort_num` int NOT NULL DEFAULT 0 COMMENT '排序权重',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1正常 0停用',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '修改人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除 1已删除 0未删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
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
  `is_public` tinyint NOT NULL DEFAULT 1 COMMENT '是否公开 1公开 0私有',
  `user_id` bigint DEFAULT NULL COMMENT '创建用户ID，NULL表示系统预置',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '修改人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除 1已删除 0未删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_word_proto` (`word_proto`),
  KEY `idx_notebook_id` (`notebook_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单词库';

-- 艾宾浩斯复习间隔配置
CREATE TABLE IF NOT EXISTS `sys_ebbinghaus_node` (
  `id` tinyint NOT NULL AUTO_INCREMENT COMMENT '复习层级ID',
  `node_name` varchar(30) NOT NULL COMMENT '阶段名称',
  `day_interval` int NOT NULL COMMENT '距初次学习间隔天数',
  `sort` tinyint NOT NULL COMMENT '排序顺序',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '修改人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除 1已删除 0未删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='艾宾浩斯复习间隔配置';

-- 单词学习状态配置
CREATE TABLE IF NOT EXISTS `sys_learn_status` (
  `status` tinyint NOT NULL COMMENT '状态编码',
  `status_name` varchar(20) NOT NULL COMMENT '状态名称',
  `remark` varchar(100) DEFAULT '' COMMENT '备注说明',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '修改人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除 1已删除 0未删除',
  PRIMARY KEY (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单词学习状态配置';

-- 个人单词背诵学习计划
CREATE TABLE IF NOT EXISTS `wd_learn_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '所属用户',
  `notebook_id` bigint NOT NULL COMMENT '所属单词本',
  `word_id` bigint NOT NULL COMMENT '关联私有单词ID',
  `learn_status` tinyint NOT NULL DEFAULT 0 COMMENT '关联学习状态',
  `current_level` tinyint NOT NULL DEFAULT 0 COMMENT '当前艾宾浩斯层级',
  `first_study_time` datetime DEFAULT NULL COMMENT '首次学习时间',
  `next_review_time` datetime DEFAULT NULL COMMENT '下次复习时间',
  `review_times` int NOT NULL DEFAULT 0 COMMENT '累计复习次数',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '修改人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除 1已删除 0未删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_review` (`user_id`, `next_review_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个人单词背诵学习计划';

-- 背诵行为记录
CREATE TABLE IF NOT EXISTS `wd_learn_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `notebook_id` bigint NOT NULL COMMENT '单词本ID',
  `word_id` bigint NOT NULL COMMENT '单词ID',
  `study_type` tinyint NOT NULL COMMENT '1初次学习 2复习',
  `result` tinyint NOT NULL COMMENT '0未记住 1记住',
  `study_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '学习时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '修改人',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除 1已删除 0未删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='背诵行为记录';

-- 大模型调用历史记录
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
-- 会员等级配置表
-- =============================================
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

-- 种子数据：默认会员等级
INSERT IGNORE INTO `sys_member_tier` (`tier`, `tier_name`, `max_bookshelves`, `max_wordbooks`, `max_words_per_book`, `daily_import_limit`, `daily_ai_reading_limit`, `is_ai_enabled`) VALUES
(1, '免费版', 3, 3, 200, 10, 50, 1),
(2, '会员版', 6, 6, 400, 20, 100, 1);

-- =============================================
-- 邀请码表
-- =============================================
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

-- =============================================
-- 用户权益记录表
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
-- 种子数据：艾宾浩斯复习节点配置
-- =============================================
INSERT IGNORE INTO `sys_ebbinghaus_node` (`id`, `node_name`, `day_interval`, `sort`) VALUES
(0, '初学', 0, 0),
(1, '第1天复习', 1, 1),
(2, '第2天复习', 2, 2),
(3, '第4天复习', 4, 3),
(4, '第7天复习', 7, 4),
(5, '第15天复习', 15, 5),
(6, '第30天复习', 30, 6);

-- =============================================
-- 种子数据：学习状态字典
-- =============================================
INSERT IGNORE INTO `sys_learn_status` (`status`, `status_name`, `remark`) VALUES
(0, '未学习', '单词已录入，尚未进行初次学习'),
(1, '已初学', '已完成初次学习，等待首次复习'),
(2, '复习中', '已进入艾宾浩斯复习周期'),
(3, '已熟记', '已通过全部复习节点，进入熟记状态'),
(4, '遗忘重学', '复习时未记住，需重新开始学习');

-- =============================================
-- AI 阅读功能配置表（可动态扩展的功能选项）
-- =============================================
CREATE TABLE IF NOT EXISTS `sys_ai_feature` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `feature_key` varchar(32) NOT NULL COMMENT '功能标识，如 word, meaning, collocation',
  `feature_name` varchar(32) NOT NULL COMMENT '功能名称，用户可见',
  `description` varchar(255) NOT NULL COMMENT '功能描述，给用户看的简短说明',
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
('word', '单词', '讲解重难点单词的词性、含义和用法', '对句子中出现的重难点单词进行讲解，包括词性、含义、用法等。要求简洁，不要啰嗦。', 1),
('meaning', '释义', '翻译和释义选中的句子或段落', '对选中的句子或段落进行翻译和释义，帮助理解整体意思。要求简洁，不要啰嗦。', 2),
('collocation', '固搭', '分析固定搭配和短语用法', '分析句子中的固定搭配和短语用法，说明其含义和使用场景。要求简洁，不要啰嗦。', 3),
('sentence_pattern', '句式', '分析语法结构和句式特点', '分析句子中的语法结构和句式特点，帮助理解语法。要求简洁，不要啰嗦。', 4),
('phonetic', '连读音标', '分析发音技巧：连读、吞音、重读、不发音字母等', '分析选中句子的发音技巧，包括：连读（linking）、吞音/省略（elision）、重读/弱读（stressed/unstressed）、哪个字母不发音（silent letters）、轻音/浊化等。给出具体的发音音标和技巧说明。要求简洁，不要啰嗦。', 5),
('root_affix', '词根词缀', '分析词根词缀构成和词义演变', '分析句子中单词的词根词缀构成，帮助记忆和理解词义演变。要求简洁，不要啰嗦。', 6),
('example', '例句', '提供类似例句帮助理解用法', '根据选中的内容，提供类似的例句帮助理解用法。要求简洁，不要啰嗦。', 7);

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
