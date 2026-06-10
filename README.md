# 分布式数据中心智能监控与预警平台

[![license](https://img.shields.io/badge/license-MIT-blue)](https://github.com/WiloMyst/MonitorSystem/blob/master/LICENSE)
[![GitHub repo size](https://img.shields.io/github/repo-size/WiloMyst/MonitorSystem)](https://github.com/WiloMyst/MonitorSystem)
![Java](https://img.shields.io/badge/Java-17-blue.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)
![Vue](https://img.shields.io/badge/Vue-3.x-4fc08d.svg)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ed.svg)

## 项目简介

本项目是一个面向数据中心和工业物联网场景的**设备监控与智能排障平台**。Java 后端作为核心业务层，负责设备数据采集、高并发削峰、缓存防击穿、权限认证和审计日志等基础能力，同时通过 SSE 流式转发对接独立的 AI 微服务，为运维人员提供智能故障诊断和排障建议。

---

## 核心技术栈

* **核心框架：** Java 17 + Spring Boot 3.3.4
* **持久层：** MyBatis-Plus 3.5.5 + MySQL 8.0
* **权限认证：** Sa-Token 1.38 (轻量级 RBAC 鉴权)
* **缓存：** Redis + Redisson 3.27.0 (分布式锁) + Caffeine 3.1.8 (本地二级缓存)
* **消息队列：** RabbitMQ (Topic 交换机 + 手动 ACK)
* **AI 服务对接：** WebFlux SSE 流式代理 + 限流 + 熔断 + 降级
* **前端：** Vue 3 + Vite + Element Plus + ECharts
* **容器化：** Docker + Docker Compose (6 服务一键编排)

---

## 系统架构

```
┌─────────────┐     ┌──────────────────────────────────────────────────┐
│   Nginx     │────▶│              Java 后端 (Spring Boot)              │
│  (前端/代理) │     │  设备监控 │ 权限认证 │ 审计日志 │ AI 代理网关    │
└─────────────┘     └──────────┬───────────────────────┬──────────────┘
                               │ SSE 流式转发          │ 内部 API 回调
                               ▼                       │
                    ┌──────────────────────┐            │
                    │  AI 微服务 (独立部署) │◀───────────┘
                    │  意图分类 / RAG /     │
                    │  多 Agent 协作        │
                    └──────────────────────┘
```

---

## 技术亮点

### 1. RabbitMQ 异步削峰：IoT 设备数据上报

硬件网关并发上报数据时，如果直接写库很容易把数据库连接池打满。本项目用 RabbitMQ 做了缓冲：

* **异步解耦：** 网关上报数据后直接扔进 RabbitMQ Topic 交换机，毫秒级返回，不占用 Web 容器线程（见 `IotMockController`）
* **手动 ACK + 重试：** 消费端不用 Auto-ACK，而是处理完再 `channel.basicAck`。异常时 `basicNack` 拒绝并重入队列，确保预警数据零丢失（见 `DeviceMessageReceiver`）
* **Redis + MySQL 双写：** 消费者先极速写 Redis 缓存（供 AI 微服务查询），再异步落库 MySQL，同时根据温度阈值自动生成告警

### 2. Redisson 分布式锁防缓存击穿

监控大屏首页是高并发读场景，缓存失效瞬间可能有大量请求同时穿透到数据库：

* **DCL 双重检查：** 先查 Redis，未命中时通过 Redisson 加锁，拿到锁后再查一次 Redis（可能已被其他节点重建），避免重复建缓存（见 `DeviceInfoServiceImpl`）
* **看门狗续期：** 不写死锁超时时间，利用 Redisson 底层 Watchdog 自动续期，防止业务未执行完锁就过期
* **安全释放：** `finally` 块中严格校验 `lock.isHeldByCurrentThread()`，只释放自己加的锁，避免误删其他线程的锁

### 3. AI 服务代理网关：限流 + 熔断 + 降级

Java 后端作为前端和 AI 微服务之间的 BFF 层，不是简单地透传请求，而是加了三层防护：

* **滑动窗口限流：** 基于 Caffeine 缓存实现按用户维度的双层限流（秒级 3 次 + 分钟级 10 次），防止个别用户刷爆 AI 接口（见 `AiRateLimiter`）
* **三态熔断器：** CLOSED → OPEN → HALF_OPEN 状态机，连续 5 次失败后熔断 30 秒，之后放 2 个探测请求，成功则恢复，失败则继续熔断（见 `AiCircuitBreaker`）
* **自动降级：** AI 微服务不可用时，自动降级到本地 `FallbackRagServiceImpl`，返回引导性提示，前端展示降级模式标识（见 `AiController`）
* **会话归属校验：** 校验 `conversation_id` 的用户前缀，防止越权访问他人对话记录

### 4. 提示词三级缓存：热更新不写死 Prompt

AI 的 Prompt 不写死在代码里，而是基于 Redis + Caffeine + MySQL 做了三级缓存，管理员在后台修改后秒级生效：

* **L1 Redis：** 分布式缓存，管理员修改时清空 key 触发热重载
* **L2 Caffeine：** JVM 本地缓存，Redis 不可用时兜底
* **L3 MySQL：** 持久化存储，缓存全部未命中时回源
* **降级兜底：** 获取锁超时时走 Caffeine，再没有数据则返回内置通用模板（见 `SysPromptServiceImpl`）

### 5. 零侵入异步审计日志

用自定义 `@Log` 注解 + Spring AOP 实现操作日志自动记录，不侵入业务代码：

* **全异步落库：** 在 `LogAspect` 中用 `CompletableFuture.runAsync()` 异步写日志，主线程不等待
* **跨线程身份传递：** 在主线程提前从 Sa-Token 的 ThreadLocal 拿到用户身份，传给异步任务，解决异步线程拿不到登录信息的问题
* **异常脱敏：** `GlobalExceptionHandler` 拦截所有异常，500 错误向前端只返回"系统繁忙"，完整堆栈留在后端日志，不暴露服务器路径和 SQL 结构

### 6. 内部接口安全防护

AI 微服务回调 Java 后端的内部接口（设备状态查询、故障分析等）需要安全隔离：

* **密钥校验：** `InternalApiInterceptor` 校验请求头中的 `X-Internal-Secret`，防止外部直接访问 `/api/internal/**` 接口
* **AI 请求链路追踪：** BFF 层为每个 AI 请求注入 `X-Trace-Id`，便于全链路排查

---

## 工程结构

```
monitor-system/
├── src/main/java/org/example/monitorsystem/
│   ├── MonitorSystemApplication.java         # 启动类
│   ├── core/
│   │   ├── database/                         # MyBatis-Plus 配置 & 自动填充
│   │   ├── exception/                        # 统一异常体系 (BusinessException + GlobalExceptionHandler)
│   │   ├── security/                         # Sa-Token 配置 / AI 限流器 / AI 熔断器 / 内部接口拦截
│   │   └── web/                              # 统一响应封装 (Result)
│   └── modules/
│       ├── device/                            # 设备模块
│       │   ├── config/RabbitMqConfig.java     #   MQ 交换机/队列/绑定配置
│       │   ├── controller/
│       │   │   ├── DeviceController.java      #   设备查询/分页/告警接口
│       │   │   └── IotMockController.java     #   IoT 网关数据上报入口
│       │   ├── mq/DeviceMessageReceiver.java  #   MQ 消费者 (手动ACK + Redis双写 + 告警生成)
│       │   ├── service/impl/
│       │   │   ├── DeviceInfoServiceImpl.java #   设备查询 (Redisson DCL 防击穿)
│       │   │   ├── DeviceMetricServiceImpl.java
│       │   │   └── DeviceAlertServiceImpl.java
│       │   ├── entity/                        #   设备/指标/告警实体
│       │   ├── mapper/                        #   MyBatis-Plus Mapper
│       │   └── model/                         #   DTO / VO
│       ├── ai/                                # AI 代理模块
│       │   ├── controller/
│       │   │   ├── AiController.java          #   AI 聊天 BFF (SSE 代理 + 限流 + 熔断 + 降级)
│       │   │   ├── InternalDeviceController.java  # 内部接口: 设备状态查询
│       │   │   ├── InternalFaultController.java   # 内部接口: 故障分析
│       │   │   └── InternalMaintenanceController.java # 内部接口: 维修进度
│       │   ├── service/
│       │   │   ├── IRagService.java           #   RAG 服务接口
│       │   │   └── FallbackRagServiceImpl.java#   降级实现
│       │   └── entity/                        #   故障报告/维修工单实体
│       └── system/                            # 系统管理模块
│           ├── auth/                          #   认证 (Sa-Token 登录/注册)
│           ├── log/                           #   审计日志 (@Log 注解 + AOP 异步落库)
│           └── prompt/                        #   提示词管理 (三级缓存 + 热更新)
├── src/main/resources/
│   ├── application.yml                        # 主配置
│   ├── application-dev.yml                    # 开发环境
│   └── application-prod.yml                   # 生产环境
└── pom.xml
```

---

## AI 微服务对接

本项目的智能排障能力由独立的 AI 微服务提供（基于 FastAPI + LangChain + LangGraph），Java 后端通过以下方式与之交互：

* **SSE 流式转发：** 前端请求 `/api/ai/ask`，Java 后端用 WebFlux WebClient 透传到 AI 微服务的 `/api/ai/ask`，流式返回 SSE 事件
* **内部 API 回调：** AI 微服务通过 `/api/internal/**` 接口回调 Java 后端，实时查询设备状态、告警记录、维修进度等业务数据
* **提示词回源：** AI 微服务启动时和运行时通过 HTTP 回调 Java 端获取最新的系统提示词

AI 微服务的详细技术文档见 [ai-service/README.md](ai-service/README.md)。

---

## 快速启动

### 1. 环境准备
* 安装 [Docker](https://www.docker.com/) 和 [Docker Compose](https://docs.docker.com/compose/)

### 2. 配置环境变量
在项目根目录创建 `.env` 文件：
```env
# 大模型 API Key (必填，根据 MODEL_PROVIDER 选择对应的 Key)
ZHIPU_API_KEY=你的_API_KEY

# 模型供应商 (可选，默认 zhipu，支持: zhipu / wenxin / tongyi / pangu / deepseek / spark)
MODEL_PROVIDER=zhipu

# 微服务间通信密钥
INTERNAL_API_SECRET=你的内部通信密钥
```

### 3. 一键启动
```bash
docker-compose up -d
```

### 4. 访问服务
| 服务 | 地址 |
|------|------|
| 监控大屏 (前端) | http://localhost |
| Java 后端 API | http://localhost:8080 |
| AI 微服务 | http://localhost:8000 |
| RabbitMQ 管理面板 | http://localhost:15672 |

---

## 压测数据

受限于本地单机环境（16 核 32G），在单节点部署下测试：

- **并发削峰：** 模拟 5000 个 IoT 终端并发上报温度数据，通过 RabbitMQ 缓冲后 MySQL 写入 QPS 稳定在 800+，无死锁和连接池耗尽，消息零丢失
- **大屏防击穿：** 1000 个并发线程同时请求大屏数据接口，Redisson 分布式锁拦截后仅 1 个线程穿透到数据库，其余 999 个线程 30ms 内从 Redis 获取缓存，接口平均响应 50ms 以内
