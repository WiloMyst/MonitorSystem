# 分布式数据中心智能监控与预警平台

[![license](https://img.shields.io/badge/license-MIT-blue)](https://github.com/WiloMyst/MonitorSystem/blob/master/LICENSE)
[![GitHub repo size](https://img.shields.io/github/repo-size/WiloMyst/MonitorSystem)](https://github.com/WiloMyst/MonitorSystem)
![Java](https://img.shields.io/badge/Java-17-blue.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)
![Vue](https://img.shields.io/badge/Vue-3.x-4fc08d.svg)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ed.svg)
![AI](https://img.shields.io/badge/Spring%20AI-RAG-orange.svg)

## 项目简介
本项目是一个面向大型企业数据中心、工业物联网（IoT）设备及通信基站的**高并发监控与智能排障平台**。
系统集成了实时数据大屏采集、异步削峰处理、分布式缓存控制，并融合了大模型能力（RAG 检索增强生成与 Function Calling），为运维人员提供“全天候监控 + 智能故障诊断”的一站式闭环解决方案。

---

## 核心技术栈

### 后端 (Backend)
* **核心框架：** Java 17 + Spring Boot 3.3.4
* **持久层：** MyBatis-Plus 3.5.5 + MySQL 8.0
* **权限认证：** Sa-Token (轻量级 RBAC 鉴权)
* **缓存与分布式锁：** Redis + Redisson 3.27.0
* **消息队列：** RabbitMQ
* **AI 核心引擎：** Spring AI + RedisVectorStore + 智谱 GLM-4 大模型

### 前端 (Frontend)
* **核心框架：** Vue 3 + Vite
* **UI 组件库：** Element Plus
* **数据可视化：** ECharts
* **状态管理与路由：** Pinia + Vue Router

### 运维与部署 (DevOps)
* **容器化：** Docker + Docker Compose 一键编排部署
* **反向代理：** Nginx

---

## 项目架构

本项目针对工业物联网 (IoT) 数据洪峰、高并发监控大屏、以及传统 LLM 滞后性等企业痛点，系统在底层架构与代码规范上进行了防御性设计： 

### 1.  [流量削峰与高可用] 海量 IoT 设备数据上报架构

传统直连写库方案在面对硬件网关并发洪峰时极易引发数据库连接池耗尽与服务雪崩。本项目重构了数据接入层：

* **异步解耦与削峰：** 抛弃 Controller 直连 MySQL，采用 RabbitMQ Topic 交换机 作为缓冲池。硬件网关上报数据后毫秒级响应并断开，保护核心 Web 容器不被拖垮 (见 `IotMockController`)。
* **可靠性投递与防御性消费：** 在消费者端摒弃 Auto-ACK，采用手动 ACK 机制 (`channel.basicAck`)。结合极速 Redis 缓存写入与 MySQL 异步状态机流转，即便发生异常也能触发 `basicNack` 拒绝并重试，确保硬件预警数据零丢失 (见 `DeviceMessageReceiver`)。 

### 2.  [缓存防击穿] 监控大屏的高并发读链路优化

针对监控大屏初始化时可能存在的“缓存击穿”与“雪崩”风险，构建了缓存安全防线：

* **Redisson 分布式双重检查锁 (DCL)：** 在大屏数据重建链路上，利用 `RedissonClient` 加锁。应对多节点并发刷新，确保瞬间只有一个线程穿透到 MySQL。 
* **看门狗机制与安全释放：** 摒弃写死的锁超时时间，利用 Redisson 底层看门狗 (Watchdog) 自动续期；在 `finally` 块中严格校验 `lock.isHeldByCurrentThread()`，杜绝误删锁灾难 (见 `DeviceInfoServiceImpl`)。 

### 3.  [AI-Native 运维] RAG 与 Function Calling 的融合

针对大模型“数据滞后”与“幻觉”的致命弱点，本项目融合了 RAG 与 Function Calling： 

* **企业数据不出域 (RAG)：** 利用 Spring AI 结合本地 `RedisVectorStore`，将企业内部《设备排障手册》向量化。断绝公网检索，解决私有化故障码排查痛点 (见 `RagServiceImpl`)。 
* **打破大模型数据滞后壁垒 (Function Calling)：** 封装大模型工具调用能力，当用户询问“某个具体设备的实时状态”时，AI 会自动识别意图，回调 Java 本地 `queryDeviceStatus` 接口实时查库，将数据转化为自然语言响应 (见 `AiFunctionConfig`)。打破了传统大模型只能回答静态知识的壁垒，使 LLM 具备了感知物理世界实时状态的能力。
* **提示词热更新引擎：** AI 的 Prompt 不写死在代码中，而是基于 Redis + MySQL 构建了支持动态刷新的缓存树，实现大模型人设与业务逻辑的秒级热加载 (见 `SysPromptServiceImpl`)。 

### 4.  [合规与审计] 零侵入异步审计日志与安全底座

遵循企业审计与合规要求，设计了系统的切面与异常机制： 

* **跨线程上下文传递的 AOP 日志：** 自定义 `@Log` 注解结合 Spring AOP，并利用 `CompletableFuture` 实现日志全异步落库。在主线程提前捕获 Sa-Token 的 `ThreadLocal` 身份上下文，解决异步线程数据丢失问题，保障业务接口 100% 纯净与低延迟 (见 `LogAspect`)。 
* **异常脱敏与全局防御：** 统一定义 `GlobalExceptionHandler`，拦截 JSR-303 参数校验与业务异常。对于未知的 500 系统崩溃，向前端统一掩蔽为“系统繁忙”，将完整堆栈截留至后端日志，杜绝服务器物理目录与 SQL 结构泄露风险。 

### 5.  [云原生交付] 容器化部署闭环

* 编写了具备多阶段构建的前后端 `Dockerfile`。 
*  `docker-compose.yml` 配合 `init.sql` 实现 MySQL 数据库的冷启动自动初始化。一键拉起包括 Redis Stack、RabbitMQ、Nginx 反向代理在内的整套微服务矩阵。

### **6. [性能基准] 本地单机压测表现**

受限于个人本地单节点开发机的硬件瓶颈（16核 32G 内存），在单机部署环境下进行压测：

- **并发削峰验证：** 模拟 5000 个 IoT 终端并发上报温度数据，通过 RabbitMQ 缓冲，MySQL 写入 QPS 稳定在 800+，无任何死锁与连接池耗尽现象，消息零丢失。
- **大屏防击穿验证：** 使用 Jmeter 开启 1000 个并发线程同时请求大屏数据接口，命中 Redisson 分布式锁拦截，仅 1 个线程穿透至数据库执行 SQL，其余 999 个线程在 30ms 内从 Redis 成功获取缓存，接口平均响应时间控制在 50ms 以内。

---

## 快速启动

本项目采用工程化设计，支持基于 `docker-compose` 的全栈一键拉起，无需在宿主机配置复杂的开发环境。

### 1. 环境准备
* 安装 [Docker](https://www.docker.com/) 和 [Docker Compose](https://docs.docker.com/compose/)。

### 2. 配置环境变量
在项目根目录创建 `.env` 文件，并填入大模型的 API Key：
```env
# 智谱 AI API Key
ZHIPU_API_KEY=你的真实_API_KEY