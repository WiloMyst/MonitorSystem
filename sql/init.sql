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

SET FOREIGN_KEY_CHECKS = 1;