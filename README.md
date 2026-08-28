# 1v1 在线对战连连看

一个双人在线连连看小游戏：两名用户登录 → 匹配进入同一房间 → 拿到同一份初始棋盘 →
实时消除图案并看到对方操作 → 对局结束后由服务端结算胜负并更新排行榜。
## [演示视频](https://github.com/Haily-11/Link-Duel/tree/demo.mp4)
## 技术栈

| 层 | 选型 |
|---|---|
| 前端 | Vue 3 + TypeScript + Vite + Pinia + axios |
| 后端 | Spring Boot 4.1.1（Java 17+）+ WebSocket |
| 数据库 | MySQL 8.0（用户 / 对局记录 / 最终结算） |
| 缓存 | Redis 7.0（匹配队列 / 房间实时状态 / 排行榜 / 结算幂等） |
| 环境 | Docker Compose 启动 MySQL 与 Redis |

## 快速启动

> 前置要求：已安装 JDK 17+、Maven、Node.js 18+、Docker（Docker Desktop）。

```bash
# 1. 启动依赖（MySQL 8.0 + Redis 7.0）
cp .env.example .env          # 可选，默认值与后端一致
docker compose up -d

# 2. 启动后端（等待 MySQL 就绪后；自动建表 + 种子账号）
mvn spring-boot:run

# 3. 启动前端（另开一个终端）
cd frontend
npm install
npm run dev
```

打开浏览器访问 **http://localhost:5173**。

> 若不使用 Docker、而是连接本机已安装的 MySQL/Redis，请用环境变量覆盖连接信息，例如：
> `DB_PASSWORD=你的MySQL密码 mvn spring-boot:run`（后端默认 `DB_PASSWORD=root`、`REDIS_HOST=localhost`）。
> 连接参数与 `.env.example` / `application.yml` 中的占位符一一对应。

### 两个账号匹配对局

1. 打开两个浏览器窗口（或一个普通窗口 + 一个无痕窗口）。
2. 窗口 A：点击「Player A」快速登录 → 点击「开始 1v1 匹配」。
3. 窗口 B：点击「Player B」快速登录 → 点击「开始 1v1 匹配」。
4. 匹配成功后双方自动进入**同一份 8×8 棋盘**，任意一方消除一对图案，另一方会实时看到闪烁与分数变化。
5. 棋盘清空、认输或断线超时后，服务端结算并在大厅展示排行榜。

### 初始账号

| 账号 | 密码 |
|---|---|
| `player_a@example.com` | `Test123456!` |
| `player_b@example.com` | `Test123456!` |

### 运行测试

```bash
# 需先启动 MySQL + Redis（集成测试会加载完整 Spring 上下文）
mvn test
```

## 胜负与积分规则（服务端结算）

- 每成功消除一对图案，操作方本局得分 **+10**。
- 对局结束条件（任一）：棋盘清空（`CLEAR`）、一方认输（`SURRENDER`）、一方断线超过 **60 秒**（`DISCONNECT`）。
- 结算：本局得分高者胜，同分平局。胜方额外 +20 积分、平局各 +10 积分，负方仅累计本局得分。
- 排行榜按累计积分排序（Redis ZSET），胜场 / 积分同步持久化到 MySQL。
- 结算具备**幂等性**：Redis `SETNX` 快速去重 + MySQL `room_id` 唯一约束，重复请求 / 服务重启不会把同一局结算两次。

## 目录结构

```
Linkgame/
├── docker-compose.yml        # MySQL + Redis
├── .env.example              # 环境变量样例
├── README.md / DESIGN.md / AI_USAGE.md
├── pom.xml                   # Spring Boot 后端
├── src/                      # 后端 Java 源码
└── frontend/                 # Vue 3 前端
```

后端关键源码路径：

- `service/LinkSearchService` —— 连连看 BFS 消去算法（≤2 次转弯）与路径折点
- `service/BoardService` —— 棋盘生成、死局检测与洗牌
- `service/GameService` —— 走子校验、幂等结算、排行榜、断线重连
- `service/MatchService` —— Redis 匹配队列
- `handler/GameWebSocketHandler` —— WebSocket 消息分发
- `controller/AuthController` / `RankingController` —— 登录与排行榜 REST 接口

## WebSocket 协议速览

前端连接 `ws://localhost:8080/ws/game?token=<登录返回的token>`。

上行（type）：`MATCH_START`、`TRY_ELIMINATE {roomId, p1, p2}`、`SURRENDER`、`CANCEL_MATCH`、`PING`。

下行（type）：`MATCH_WAITING`、`MATCH_SUCCESS`、`RECONNECT_STATE`、`ELIMINATE_SYNC`、
`ELIMINATE_REJECT`、`GAME_OVER`、`OPPONENT_DISCONNECTED`、`OPPONENT_RECONNECTED`、`PONG`。

更多设计细节见 [DESIGN.md](./DESIGN.md)。
