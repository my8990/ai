# 智慧课堂在线教学平台

面向高校课堂的一体化教学平台，覆盖 **课程管理 → 签到 → 作业 → 测验 → 资料 → 通知 → 讨论 → 成绩 → 直播授课 → AI 助手** 的完整教学闭环。

包含三个端：

| 端 | 目录 | 技术栈 |
|---|---|---|
| 后端服务 | `back/` | Spring Boot 3.2 / MyBatis-Plus / MySQL 8 / Redis / RabbitMQ / WebSocket |
| Web 管理端 | `front/` | Vue 3 / Vite 5 / Element Plus / Pinia / Vue Router |
| 微信小程序端 | `miniapp/` | 原生小程序（学生端 + 教师端） |

---

## 一、功能模块

| 模块 | 能力 |
|---|---|
| 用户与权限 | 注册/登录/微信登录、教师入驻审核、封禁与申诉、JWT 鉴权、角色（admin / teacher / student） |
| 课程 | 创建课程、加课码入课、成员管理、课程信息维护 |
| 签到 | 教师发起签到、二维码签到（防重放）、位置签到、签到统计与明细 |
| 作业 | 发布作业、学生提交、教师批改评分 |
| 测验 | 题库管理、组卷、在线答题、自动判分、成绩记录、收藏题目 |
| 资料 | 课件上传、格式转换（Office/PDF → 图片分页）、在线预览 |
| 通知 | 课程公告发布与已读管理 |
| 讨论 | 发帖、评论、点赞、置顶/精华 |
| 成绩 | 评分规则配置、成绩明细、Excel 导出 |
| 直播 | 开课/结束、幻灯片推送、实时互动（提问/答题）、在线名单、课堂报告、WebSocket 实时同步 |
| AI 助手 | 基于大模型的问答，支持普通模式与 SSE 流式输出 |
| 审计 | 操作审计日志（异步写入）、访问日志、traceId 全链路追踪 |

---

## 二、整体架构

```
                    ┌──────────────┐   ┌──────────────┐
                    │  Vue 3 管理端 │   │ 微信小程序端  │
                    └───────┬──────┘   └───────┬──────┘
                            │  HTTPS / REST    │
                            └────────┬─────────┘
                                     ▼
                        ┌────────────────────────┐
                        │   Spring Boot 单体服务  │
                        │  Controller → Service  │
                        │        → Mapper        │
                        └───┬───────┬────────┬───┘
                            │       │        │
              ┌─────────────▼──┐ ┌──▼─────┐ ┌▼──────────────┐
              │     MySQL 8    │ │ Redis  │ │  RabbitMQ     │
              │  业务数据/审计   │ │会话/限流│ │ 广播/审计异步  │
              └────────────────┘ └────────┘ └───────────────┘
                            │
                    ┌───────▼────────┐
                    │ WebSocket 长连接 │  ← 直播课堂实时推送
                    └────────────────┘
```

后端为**分层单体架构**（Controller / Service / Mapper / Entity / DTO），未做微服务拆分：当前业务体量与团队规模下，单体 + 清晰分层比过度拆分更易维护；横向扩展依赖无状态部署 + Redis/RabbitMQ 共享中间件。

### 关键设计

**1. 直播跨实例广播（策略模式 + 配置驱动）**

`LiveBroadcastBus` 接口下有三个实现，通过 `app.live.broadcast.mode` 切换：

| 实现 | 机制 | 适用 |
|---|---|---|
| `DbBroadcastBus` | 事件表 + 500ms 轮询 | 零依赖、可回放，作为降级方案（默认） |
| `RabbitBroadcastBus` | RabbitMQ Direct 交换机 | 多实例生产部署，延迟低、不写事件表 |
| `RedisBroadcastBus` | Redis Pub/Sub + 3s 节流 | 已有 Redis 但无 RabbitMQ 的部署，P99 延迟 < 50ms |

三个实现由 `@ConditionalOnProperty` 互斥装配，`LiveWebSocketHandler` 只依赖接口，切换实现无需改动业务代码；消息携带 `instanceId`，消费端忽略自身发出的消息，避免同一实例重复推送。Redis 实现额外配置了 5s 重连恢复，订阅断连后自动重新订阅。

**2. 审计日志异步化**

`AuditWriter` 接口同样支持两种实现：本地线程池直写库（默认）与 MQ 投递（`app.mq.audit.enabled=true`）。MQ 模式下：发布确认保证消息到达 Broker → 消费端失败重试 3 次 → 仍失败进入**死信队列**；Broker 不可用时降级直写数据库，保证审计不丢。

**3. 直播状态快照缓存**

`LiveStateCache` 对课堂状态做 2 秒 TTL 的快照，读多写少场景下削峰；提供 `local`（单机 `ConcurrentHashMap`，默认）与 `redis`（多实例共享，SCAN 游标清理）两种实现，同样由配置切换。`presence` 心跳写库降频至 10 秒，降低高频心跳对 MySQL 的压力。

**4. 安全设计**

- JWT 无状态鉴权 + Redis 黑名单支持主动注销；令牌 ID 每次签发唯一
- 登录失败次数限制与临时锁定（Redis 计数）
- 签到二维码 HMAC-SHA256 签名 + 时间桶，防篡改与重放
- 文件上传：扩展名白名单 + 文件头魔数校验 + 时效签名 URL 访问
- Redis 故障降级策略分级：**鉴权类 fail-closed（拒绝）**，体验类 fail-open（放行）
- 启动期配置校验：密钥长度/占位符检查，`strict-config` 开启时缺失配置直接拒绝启动
- 所有业务随机码（加课码、课堂码、签到码）使用 `SecureRandom`，避免可预测序列
- 微信 `openid` 不写入任何对外 VO，也不作为账号密码使用（微信用户使用不可逆随机占位密码）

**5. 并发与一致性**

所有"先查后插"的写入路径统一改为**依赖数据库唯一索引兜底 + 捕获 `DuplicateKeyException` 重试**，而不是预检查：

| 场景 | 唯一索引 | 冲突处理 |
|---|---|---|
| 创建课程生成加课码 | `uk_code` | 重新生成，最多 5 次 |
| 开课生成课堂码 | `uk_join_code_active` | 重新生成，最多 20 次 |
| 注册 / 微信建号 | `uk_username`、`uk_phone`、`uk_student_no` | 转友好提示或换名重试 |
| 加入课程 | `uk_course_student` | 提示"已加入该课程" |
| 提交测验 | `uk_record_question` | 提示"已提交过该测验" |
| 签到提交 | `uk_checkin_student` | 提示"不可重复签到" |

课堂码唯一索引使用**生成列** `active_flag`（进行中为 1，已结束/删除为 NULL），这样只约束"同时进行中的课堂码唯一"，历史课堂码仍可复用；索引在 `init.sql` 中幂等创建，且仅在存量数据无冲突时才添加，避免升级启动失败。

**6. 外部依赖隔离**

- 高德逆地理的本地缓存使用**有界 LRU（上限 2000 条）**，负结果只缓存 30 秒 TTL，避免一次超时把坐标永久标记为无效、也避免缓存无限增长
- 地址解析在事务外执行，`RestTemplate` 统一注入单例并配置 3s 连接 / 5s 读取超时，避免外部 HTTP 抖动长期占用数据库连接导致连接池耗尽

---

## 三、快速开始

### 方式一：Docker Compose 一键启动（推荐）

需要 Docker 24+ 与 Docker Compose v2，建议可用内存 **4 GB 以上**（MySQL + Redis + RabbitMQ + JVM 同时运行）。

```bash
# 在仓库根目录
docker compose up -d --build
```

可选：通过环境变量注入外部服务密钥（不注入也能启动，仅对应功能不可用）

```bash
# Linux / macOS
AI_API_KEY=sk-xxx AMAP_KEY=xxx WECHAT_APPID=xxx WECHAT_SECRET=xxx docker compose up -d --build
```

Windows PowerShell：

```powershell
$env:AI_API_KEY="sk-xxx"; $env:AMAP_KEY="xxx"; docker compose up -d --build
```

启动后：

| 服务 | 地址 |
|---|---|
| 后端 API | http://localhost:8080/api |
| 健康检查 | http://localhost:8080/api/actuator/health |
| 接口文档（Knife4j） | http://localhost:8080/api/doc.html |
| RabbitMQ 管理台 | http://localhost:15672 （默认 guest / guest） |

默认管理员账号 `admin`，初始密码见 `db/init.sql` 种子数据（默认 `123456`），**生产环境请立即修改**。

> 未配置 `AI_API_KEY` 时 AI 助手接口返回服务不可用；未配置 `AMAP_KEY` 时签到仅保存前端上报地址，不做逆地理补全。其余功能不受影响。

### 方式二：本地开发

前置依赖：JDK 17、Maven 3.8+、MySQL 8、Redis 7、RabbitMQ 3（RabbitMQ 可选，不启用 MQ 模式则不需要）。

```bash
# 1. 启动 MySQL / Redis（RabbitMQ 按需）
docker compose up -d mysql redis rabbitmq

# 2. 配置本地密钥（该文件已被 .gitignore 排除）
cd back
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
# 编辑 application-local.yml，填入 DB 密码、JWT 密钥、AI Key 等

# 3. 启动后端
mvn spring-boot:run

# 4. 启动 Web 前端
cd ../front
npm install
npm run dev

# 5. 小程序端：用微信开发者工具导入 miniapp/ 目录
```

> 若未配置 `JWT_SECRET` / `CHECKIN_QR_SECRET`，服务会在启动阶段直接失败并提示缺失项——这是刻意的 fail-fast 设计。

---

## 四、配置说明

所有配置项均支持环境变量覆盖，敏感信息不写入仓库。

| 环境变量 | 说明 | 默认值 |
|---|---|---|
| `DB_URL` | JDBC 连接串 | `jdbc:mysql://localhost:3306/ai_assistant...` |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库账号密码 | `root` / 空 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis 连接 | `localhost` / `6379` / 空 |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` / `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | RabbitMQ 连接 | `localhost` / `5672` / `guest` / `guest` |
| `JWT_SECRET` | JWT 签名密钥，**至少 32 字节** | 无（必填） |
| `JWT_EXPIRATION` | 令牌有效期（毫秒） | `86400000` |
| `CHECKIN_QR_SECRET` | 签到二维码密钥，**至少 16 字节** | 无（必填） |
| `FILE_SIGN_SECRET` | 文件 URL 签名密钥 | 回退使用 `JWT_SECRET` |
| `WECHAT_APPID` / `WECHAT_SECRET` | 微信小程序登录 | 空 |
| `AI_API_KEY` / `AI_MODEL` / `AI_BASE_URL` | 大模型服务 | 空 / `deepseek-flash` / `https://api.deepseek.com` |
| `AMAP_KEY` | 高德地图（签到定位） | 空 |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | 允许跨域来源 | `http://localhost:*,http://127.0.0.1:*` |
| `STRICT_CONFIG` | 严格配置校验（生产建议开启） | `false` |
| `SECURITY_REDIS_FAIL_CLOSED` | 鉴权链路 Redis 故障时是否拒绝请求 | `true` |
| `LIVE_BROADCAST_MODE` | 直播广播实现：`db` / `rabbit` / `redis` | `db` |
| `LIVE_CACHE_MODE` | 课堂状态缓存：`local` / `redis` | `local` |
| `MQ_AUDIT_ENABLED` | 审计日志走 MQ | `false` |
| `MQ_HEALTH_ENABLED` | RabbitMQ 纳入健康检查 | `false` |
| `HTTP_CONNECT_TIMEOUT_MS` / `HTTP_READ_TIMEOUT_MS` | 外部 HTTP（高德、微信）超时 | `3000` / `5000` |
| `FILE_UPLOAD_DIR` / `LOG_PATH` | 上传目录 / 日志目录 | `uploads` / `logs` |

---

## 五、接口约定

- 统一前缀 `/api`，统一响应体：

```json
{ "code": 200, "message": "success", "data": { } }
```

- `code` 与 HTTP 状态码一致（400/401/403/404/409/413/415/500/503），由 `GlobalExceptionHandler` 统一转换
- 鉴权：请求头 `Authorization: Bearer <token>`
- 每个响应携带 `X-Trace-Id`，与日志中的 `traceId` 对应，便于链路排查

---

## 六、测试

```bash
cd back
mvn test
```

当前覆盖：JWT 签发/解析/篡改拒绝、签到二维码防重放、上传文件魔数校验。

计划补充：基于 Testcontainers 的 MySQL + Redis + RabbitMQ 集成测试，覆盖登录失败锁定、加课码并发冲突、直播 ticket 一次性消费等核心链路。

---

## 七、目录结构

```
.
├── back/                        # 后端服务
│   ├── src/main/java/com/example/ai/
│   │   ├── controller/          # 接口层
│   │   ├── service/             # 业务层（接口 + impl）
│   │   ├── mapper/              # MyBatis-Plus Mapper
│   │   ├── entity/              # 数据库实体
│   │   ├── dto/                 # 请求/响应对象
│   │   ├── security/            # JWT、鉴权拦截器、角色、用户上下文
│   │   ├── audit/               # 访问日志、操作审计
│   │   ├── mq/                  # RabbitMQ 拓扑与配置
│   │   ├── websocket/           # 直播 WebSocket 与广播总线
│   │   ├── cache/               # 课堂状态快照缓存
│   │   ├── config/              # 各类配置与启动校验
│   │   ├── common/              # 统一返回、错误码、异常处理
│   │   └── util/                # JWT、IP、文件校验、PDF 等工具
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── logback-spring.xml
│   │   └── db/init.sql
│   └── src/test/                # 单元测试
├── front/                       # Vue 3 管理端
├── miniapp/                     # 微信小程序端
├── docker-compose.yml
└── .github/workflows/ci.yml
```

---

## 八、已知限制与后续规划

| 项 | 现状 | 计划 |
|---|---|---|
| 数据库变更 | 启动时执行 `db/init.sql` | 迁移至 Flyway 版本化管理，生产关闭自动 DDL |
| 文件存储 | 本地磁盘 | 抽象存储接口，生产切换对象存储（MinIO / OSS） |
| 限流 | 仅登录失败锁定 | 接入接口级限流与幂等键 |
| 可观测性 | 日志 + Actuator 健康检查 | 接入 Prometheus 指标与结构化 JSON 日志 |
| 角色常量 | 部分使用字符串字面量 | 统一收敛至 `Roles` 常量 / 枚举 |
| 令牌策略 | 单一长有效期 token | 短期 access token + refresh token |

---

## 九、安全提示

- `application-local.yml`、`*.local.js`、数据库备份、小程序私有配置均已加入 `.gitignore`，**请勿提交任何真实密钥**
- 生产部署务必设置 `STRICT_CONFIG=true`，并关闭接口文档（Knife4j）暴露
- 默认管理员密码仅供本地演示，上线前必须修改