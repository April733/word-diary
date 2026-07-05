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
