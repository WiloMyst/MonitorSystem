-- 创建数据库并指定字符集，防止中文乱码
CREATE DATABASE IF NOT EXISTS `monitor_system` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `monitor_system`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 1. 设备信息表 (device_info)
-- ----------------------------
DROP TABLE IF EXISTS `device_info`;
CREATE TABLE `device_info`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_code` varchar(50) NOT NULL COMMENT '设备编号',
  `device_type` varchar(100) NULL DEFAULT NULL COMMENT '设备类型',
  `status` int(2) NULL DEFAULT 0 COMMENT '状态(0正常 1异常 2离线)',
  `temperature` decimal(5, 2) NULL DEFAULT NULL COMMENT '实时温度',
  `update_time` datetime NULL DEFAULT NULL COMMENT '最后更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_device_code`(`device_code`) USING BTREE COMMENT '设备编号唯一索引'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 ROW_FORMAT = Dynamic COMMENT = '设备信息表';

-- 插入设备初始数据
INSERT INTO `device_info` VALUES (1, 'ATM-SN-001', '市南分行营业部-智能柜员机', 0, 38.50, '2026-05-11 02:01:01');
INSERT INTO `device_info` VALUES (2, 'SRV-DB-MASTER', '核心交易库主节点服务器', 1, 88.50, '2026-05-11 09:35:21');
INSERT INTO `device_info` VALUES (3, '5G-BS-SB-045', '市北区台东商圈-5G宏基站', 2, NULL, '2026-05-11 02:01:01');
INSERT INTO `device_info` VALUES (4, 'RT-CORE-LC', '李沧区数据中心-核心路由器', 0, 42.10, '2026-05-11 02:01:01');
INSERT INTO `device_info` VALUES (5, 'UPS-ROOM-A', 'A栋机房不间断电源(UPS)', 0, 35.00, '2026-05-11 02:01:01');

-- ----------------------------
-- 2. 系统用户表 (sys_user)
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL COMMENT '登录账号',
  `password` varchar(100) NOT NULL COMMENT '登录密码(哈希值)',
  `salt` varchar(20) NOT NULL COMMENT '密码加密盐',
  `real_name` varchar(50) NULL DEFAULT NULL COMMENT '真实姓名',
  `role_code` varchar(20) NULL DEFAULT 'USER' COMMENT '角色',
  `status` int(2) NULL DEFAULT 1 COMMENT '状态(1正常 0禁用)',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_username`(`username`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 ROW_FORMAT = Dynamic COMMENT = '系统用户表';

-- 插入管理员数据。
-- 注意：假设密码为 123456，盐为 abc123。
-- SHA256(123456abc123) 的哈希值为：1cdfc5dbd09aeb78018dc3bcda41e6c2fbdebb5be640e7d583bf4c0c1737e6da
INSERT INTO `sys_user` VALUES (1, 'admin', '1cdfc5dbd09aeb78018dc3bcda41e6c2fbdebb5be640e7d583bf4c0c1737e6da', 'abc123', '超级管理员', 'ADMIN', 1, '2026-05-11 02:15:08');

-- ----------------------------
-- 3. 系统提示词配置表 (sys_prompt)
-- ----------------------------
DROP TABLE IF EXISTS `sys_prompt`;
CREATE TABLE `sys_prompt`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `prompt_code` varchar(50) NOT NULL COMMENT '提示词业务编码',
  `content` text NOT NULL COMMENT '提示词模板内容',
  `description` varchar(200) NULL DEFAULT NULL COMMENT '描述用途',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_prompt_code`(`prompt_code`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 ROW_FORMAT = Dynamic COMMENT = 'AI动态提示词配置表';

-- 插入 RAG 核心提示词
INSERT INTO `sys_prompt` VALUES (1, 'device_rag', '你是一个专业的工业设备排障AI助手，根据【用户问题】来回答问题。\n\n【知识库上下文】\n{context}\n\n【用户问题】\n{question}', '设备排障 RAG 核心提示词', '2026-05-12 12:25:26');

-- ----------------------------
-- 4. 操作日志表 (sys_oper_log) - 之前漏掉的建表语句
-- ----------------------------
DROP TABLE IF EXISTS `sys_oper_log`;
CREATE TABLE `sys_oper_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `title` varchar(50) DEFAULT NULL COMMENT '模块标题',
  `business_type` varchar(20) DEFAULT NULL COMMENT '业务类型',
  `method` varchar(100) DEFAULT NULL COMMENT '方法名称',
  `request_method` varchar(10) DEFAULT NULL COMMENT '请求方式',
  `oper_name` varchar(50) DEFAULT NULL COMMENT '操作人员',
  `oper_url` varchar(255) DEFAULT NULL COMMENT '请求URL',
  `oper_ip` varchar(50) DEFAULT NULL COMMENT '主机地址',
  `status` int(1) DEFAULT 0 COMMENT '操作状态（1正常 0异常）',
  `error_msg` varchar(2000) DEFAULT NULL COMMENT '错误消息',
  `cost_time` bigint(20) DEFAULT 0 COMMENT '执行耗时(毫秒)',
  `oper_time` datetime DEFAULT NULL COMMENT '操作时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB CHARACTER SET=utf8mb4 ROW_FORMAT=Dynamic COMMENT='操作审计日志表';

-- ----------------------------
-- 11. 设备指标时序表 (device_metric)
-- ----------------------------
DROP TABLE IF EXISTS `device_metric`;
CREATE TABLE `device_metric` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_code` varchar(50) NOT NULL COMMENT '设备编号',
  `metric_type` varchar(30) NOT NULL DEFAULT 'temperature' COMMENT '指标类型(temperature/vibration/pressure/voltage)',
  `metric_value` decimal(10,2) NOT NULL COMMENT '指标值',
  `recorded_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '采集时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_device_metric`(`device_code`, `metric_type`, `recorded_at`) USING BTREE COMMENT '设备指标联合索引'
) ENGINE=InnoDB CHARACTER SET=utf8mb4 ROW_FORMAT=Dynamic COMMENT='设备指标时序表';

INSERT INTO `device_metric` (`device_code`, `metric_type`, `metric_value`, `recorded_at`) VALUES
('ATM-SN-001', 'temperature', 36.20, '2026-05-11 00:00:00'),
('ATM-SN-001', 'temperature', 37.10, '2026-05-11 04:00:00'),
('ATM-SN-001', 'temperature', 38.50, '2026-05-11 08:00:00'),
('ATM-SN-001', 'temperature', 37.80, '2026-05-11 12:00:00'),
('ATM-SN-001', 'temperature', 36.90, '2026-05-11 16:00:00'),
('ATM-SN-001', 'temperature', 37.40, '2026-05-11 20:00:00'),
('SRV-DB-MASTER', 'temperature', 62.00, '2026-05-11 00:00:00'),
('SRV-DB-MASTER', 'temperature', 71.30, '2026-05-11 04:00:00'),
('SRV-DB-MASTER', 'temperature', 79.80, '2026-05-11 08:00:00'),
('SRV-DB-MASTER', 'temperature', 85.20, '2026-05-11 09:00:00'),
('SRV-DB-MASTER', 'temperature', 88.50, '2026-05-11 09:35:00'),
('SRV-DB-MASTER', 'temperature', 86.10, '2026-05-11 10:00:00'),
('SRV-DB-MASTER', 'temperature', 82.40, '2026-05-11 14:00:00'),
('SRV-DB-MASTER', 'temperature', 78.90, '2026-05-11 20:00:00'),
('RT-CORE-LC', 'temperature', 40.10, '2026-05-11 00:00:00'),
('RT-CORE-LC', 'temperature', 41.30, '2026-05-11 06:00:00'),
('RT-CORE-LC', 'temperature', 42.10, '2026-05-11 12:00:00'),
('RT-CORE-LC', 'temperature', 41.50, '2026-05-11 18:00:00'),
('RT-CORE-LC', 'temperature', 40.80, '2026-05-11 23:00:00'),
('UPS-ROOM-A', 'voltage', 220.30, '2026-05-11 00:00:00'),
('UPS-ROOM-A', 'voltage', 219.80, '2026-05-11 06:00:00'),
('UPS-ROOM-A', 'voltage', 218.50, '2026-05-11 12:00:00'),
('UPS-ROOM-A', 'voltage', 220.10, '2026-05-11 18:00:00'),
('UPS-ROOM-A', 'voltage', 219.60, '2026-05-11 23:00:00'),
('RT-CORE-LC', 'vibration', 0.12, '2026-05-11 00:00:00'),
('RT-CORE-LC', 'vibration', 0.15, '2026-05-11 08:00:00'),
('RT-CORE-LC', 'vibration', 0.18, '2026-05-11 16:00:00'),
('RT-CORE-LC', 'vibration', 0.14, '2026-05-11 23:00:00'),
('SRV-DB-MASTER', 'vibration', 0.08, '2026-05-11 00:00:00'),
('SRV-DB-MASTER', 'vibration', 0.12, '2026-05-11 04:00:00'),
('SRV-DB-MASTER', 'vibration', 0.25, '2026-05-11 08:00:00'),
('SRV-DB-MASTER', 'vibration', 0.38, '2026-05-11 09:00:00'),
('SRV-DB-MASTER', 'vibration', 0.45, '2026-05-11 09:35:00'),
('SRV-DB-MASTER', 'vibration', 0.40, '2026-05-11 10:00:00'),
('SRV-DB-MASTER', 'vibration', 0.30, '2026-05-11 14:00:00'),
('SRV-DB-MASTER', 'vibration', 0.15, '2026-05-11 20:00:00'),
('ATM-SN-001', 'vibration', 0.05, '2026-05-11 00:00:00'),
('ATM-SN-001', 'vibration', 0.06, '2026-05-11 04:00:00'),
('ATM-SN-001', 'vibration', 0.07, '2026-05-11 08:00:00'),
('ATM-SN-001', 'vibration', 0.06, '2026-05-11 12:00:00'),
('ATM-SN-001', 'vibration', 0.05, '2026-05-11 16:00:00'),
('ATM-SN-001', 'vibration', 0.06, '2026-05-11 20:00:00'),
('UPS-ROOM-A', 'pressure', 101.30, '2026-05-11 00:00:00'),
('UPS-ROOM-A', 'pressure', 101.25, '2026-05-11 06:00:00'),
('UPS-ROOM-A', 'pressure', 101.20, '2026-05-11 12:00:00'),
('UPS-ROOM-A', 'pressure', 101.28, '2026-05-11 18:00:00'),
('UPS-ROOM-A', 'pressure', 101.22, '2026-05-11 23:00:00'),
('SRV-DB-MASTER', 'pressure', 101.35, '2026-05-11 00:00:00'),
('SRV-DB-MASTER', 'pressure', 101.40, '2026-05-11 04:00:00'),
('SRV-DB-MASTER', 'pressure', 101.50, '2026-05-11 08:00:00'),
('SRV-DB-MASTER', 'pressure', 101.60, '2026-05-11 09:00:00'),
('SRV-DB-MASTER', 'pressure', 101.55, '2026-05-11 14:00:00'),
('SRV-DB-MASTER', 'pressure', 101.38, '2026-05-11 20:00:00'),
('RT-CORE-LC', 'pressure', 101.28, '2026-05-11 00:00:00'),
('RT-CORE-LC', 'pressure', 101.30, '2026-05-11 06:00:00'),
('RT-CORE-LC', 'pressure', 101.32, '2026-05-11 12:00:00'),
('RT-CORE-LC', 'pressure', 101.29, '2026-05-11 18:00:00'),
('RT-CORE-LC', 'pressure', 101.27, '2026-05-11 23:00:00'),
('ATM-SN-001', 'voltage', 220.10, '2026-05-11 00:00:00'),
('ATM-SN-001', 'voltage', 219.80, '2026-05-11 04:00:00'),
('ATM-SN-001', 'voltage', 219.50, '2026-05-11 08:00:00'),
('ATM-SN-001', 'voltage', 220.00, '2026-05-11 12:00:00'),
('ATM-SN-001', 'voltage', 219.90, '2026-05-11 16:00:00'),
('ATM-SN-001', 'voltage', 220.20, '2026-05-11 20:00:00'),
('SRV-DB-MASTER', 'voltage', 220.50, '2026-05-11 00:00:00'),
('SRV-DB-MASTER', 'voltage', 219.90, '2026-05-11 04:00:00'),
('SRV-DB-MASTER', 'voltage', 218.80, '2026-05-11 08:00:00'),
('SRV-DB-MASTER', 'voltage', 217.50, '2026-05-11 09:00:00'),
('SRV-DB-MASTER', 'voltage', 216.80, '2026-05-11 09:35:00'),
('SRV-DB-MASTER', 'voltage', 218.20, '2026-05-11 10:00:00'),
('SRV-DB-MASTER', 'voltage', 219.50, '2026-05-11 14:00:00'),
('SRV-DB-MASTER', 'voltage', 220.10, '2026-05-11 20:00:00'),
('5G-BS-SB-045', 'voltage', 48.10, '2026-05-11 00:00:00'),
('5G-BS-SB-045', 'voltage', 48.05, '2026-05-11 04:00:00'),
('5G-BS-SB-045', 'voltage', 47.90, '2026-05-11 08:00:00'),
('5G-BS-SB-045', 'voltage', 0.00, '2026-05-11 09:00:00'),
('5G-BS-SB-045', 'voltage', 0.00, '2026-05-11 12:00:00'),
('5G-BS-SB-045', 'voltage', 0.00, '2026-05-11 20:00:00');

SET FOREIGN_KEY_CHECKS = 1;


USE `monitor_system`;

-- ----------------------------
-- 5. 对话会话表 (conversation)
-- ----------------------------
DROP TABLE IF EXISTS `conversation`;
CREATE TABLE `conversation` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `conversation_id` varchar(64) NOT NULL COMMENT '会话唯一标识',
  `user_id` varchar(64) NOT NULL DEFAULT 'default_user' COMMENT '用户标识',
  `summary` text DEFAULT NULL COMMENT '对话摘要',
  `message_count` int(11) NOT NULL DEFAULT 0 COMMENT '消息条数',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_conversation_id`(`conversation_id`) USING BTREE COMMENT '会话ID唯一索引',
  INDEX `idx_user_id`(`user_id`) USING BTREE COMMENT '用户标识索引'
) ENGINE=InnoDB CHARACTER SET=utf8mb4 ROW_FORMAT=Dynamic COMMENT='对话会话表';

-- ----------------------------
-- 6. 对话消息表 (conversation_message)
-- ----------------------------
DROP TABLE IF EXISTS `conversation_message`;
CREATE TABLE `conversation_message` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `conversation_id` varchar(64) NOT NULL COMMENT '会话唯一标识',
  `role` varchar(20) NOT NULL COMMENT '角色(human/ai/system)',
  `content` text NOT NULL COMMENT '消息内容',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_conversation_id`(`conversation_id`) USING BTREE COMMENT '会话ID索引'
) ENGINE=InnoDB CHARACTER SET=utf8mb4 ROW_FORMAT=Dynamic COMMENT='对话消息表';

-- ----------------------------
-- 7. 维修工单表 (maintenance_order)
-- ----------------------------
DROP TABLE IF EXISTS `maintenance_order`;
CREATE TABLE `maintenance_order` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_id` varchar(64) NOT NULL COMMENT '工单编号',
  `device_code` varchar(50) NOT NULL COMMENT '设备编号',
  `fault_description` text NOT NULL COMMENT '故障描述',
  `priority` varchar(20) NOT NULL DEFAULT 'medium' COMMENT '优先级(high/medium/low)',
  `status` varchar(20) NOT NULL DEFAULT 'pending' COMMENT '状态(pending/processing/completed/cancelled)',
  `assigned_team` varchar(100) DEFAULT NULL COMMENT '指派维修团队',
  `result` text DEFAULT NULL COMMENT '维修结果',
  `created_by` varchar(64) NOT NULL DEFAULT 'ai_agent' COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `completed_at` datetime DEFAULT NULL COMMENT '完成时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_order_id`(`order_id`) USING BTREE COMMENT '工单编号唯一索引',
  INDEX `idx_device_code`(`device_code`) USING BTREE COMMENT '设备编号索引'
) ENGINE=InnoDB CHARACTER SET=utf8mb4 ROW_FORMAT=Dynamic COMMENT='维修工单表';

-- ----------------------------
-- 8. 故障报告表 (fault_report)
-- ----------------------------
DROP TABLE IF EXISTS `fault_report`;
CREATE TABLE `fault_report` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `report_id` varchar(64) NOT NULL COMMENT '报告编号',
  `device_code` varchar(50) NOT NULL COMMENT '设备编号',
  `fault_description` text NOT NULL COMMENT '故障描述',
  `severity` varchar(20) NOT NULL DEFAULT 'medium' COMMENT '严重程度(critical/high/medium/low)',
  `summary` text DEFAULT NULL COMMENT '故障概述',
  `impact_assessment` text DEFAULT NULL COMMENT '影响评估',
  `recommended_actions` text DEFAULT NULL COMMENT '建议措施(JSON数组)',
  `created_by` varchar(64) NOT NULL DEFAULT 'ai_agent' COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_report_id`(`report_id`) USING BTREE COMMENT '报告编号唯一索引',
  INDEX `idx_device_code`(`device_code`) USING BTREE COMMENT '设备编号索引'
) ENGINE=InnoDB CHARACTER SET=utf8mb4 ROW_FORMAT=Dynamic COMMENT='故障报告表';

-- ----------------------------
-- 9. Agent评估结果表 (agent_eval_result)
-- ----------------------------
DROP TABLE IF EXISTS `agent_eval_result`;
CREATE TABLE `agent_eval_result` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `evaluation_id` varchar(64) NOT NULL COMMENT '评估ID',
  `question` text NOT NULL COMMENT '评估问题',
  `composite_score` decimal(6,4) NOT NULL DEFAULT 0.0000 COMMENT '综合评分',
  `trajectory_score` decimal(6,4) DEFAULT NULL COMMENT '轨迹评分',
  `tool_call_score` decimal(6,4) DEFAULT NULL COMMENT '工具调用评分',
  `end_to_end_score` decimal(6,4) DEFAULT NULL COMMENT '端到端评分',
  `detail_json` text DEFAULT NULL COMMENT '评估详情(JSON)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_evaluation_id`(`evaluation_id`) USING BTREE COMMENT '评估ID唯一索引'
) ENGINE=InnoDB CHARACTER SET=utf8mb4 ROW_FORMAT=Dynamic COMMENT='Agent评估结果表';

-- ----------------------------
-- 10. 设备告警表 (device_alert)
-- ----------------------------
DROP TABLE IF EXISTS `device_alert`;
CREATE TABLE `device_alert` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `alert_id` varchar(64) NOT NULL COMMENT '告警唯一标识',
  `device_code` varchar(50) NOT NULL COMMENT '设备编号',
  `alert_type` varchar(50) NOT NULL COMMENT '告警类型(device_status/temperature/offline/vibration)',
  `severity` varchar(20) NOT NULL DEFAULT 'warning' COMMENT '严重程度(critical/warning/info)',
  `message` varchar(500) NOT NULL COMMENT '告警描述',
  `acknowledged` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已确认(0未确认 1已确认)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '告警时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_alert_id`(`alert_id`) USING BTREE COMMENT '告警ID唯一索引',
  INDEX `idx_device_code`(`device_code`) USING BTREE COMMENT '设备编号索引',
  INDEX `idx_severity`(`severity`) USING BTREE COMMENT '严重程度索引',
  INDEX `idx_created_at`(`created_at`) USING BTREE COMMENT '告警时间索引'
) ENGINE=InnoDB CHARACTER SET=utf8mb4 ROW_FORMAT=Dynamic COMMENT='设备告警表';

INSERT INTO `device_alert` VALUES (1, 'ALT-SRV-DB-MASTER-001', 'SRV-DB-MASTER', 'temperature', 'critical', '核心交易库主节点服务器 温度过高: 88.50°C', 0, '2026-05-11 09:35:21');
INSERT INTO `device_alert` VALUES (2, 'ALT-SRV-DB-MASTER-002', 'SRV-DB-MASTER', 'device_status', 'critical', '核心交易库主节点服务器 状态异常', 0, '2026-05-11 09:35:21');
INSERT INTO `device_alert` VALUES (3, 'ALT-5G-BS-SB-045-001', '5G-BS-SB-045', 'offline', 'critical', '市北区台东商圈-5G宏基站 已离线', 0, '2026-05-11 02:10:00');
INSERT INTO `device_alert` VALUES (4, 'ALT-ATM-SN-001-001', 'ATM-SN-001', 'temperature', 'warning', '市南分行营业部-智能柜员机 温度偏高: 38.50°C', 1, '2026-05-11 02:05:00');
INSERT INTO `device_alert` VALUES (5, 'RT-CORE-LC-001', 'RT-CORE-LC', 'vibration', 'info', '李沧区数据中心-核心路由器 检测到轻微振动', 1, '2026-05-11 01:30:00');