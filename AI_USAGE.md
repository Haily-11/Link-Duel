# AI 工具使用记录（AI_USAGE）

## 使用的工具

- **Claude Code（CLI）**：用于项目脚手架搭建、后端核心逻辑（算法 / 服务 / WebSocket）、
  前端 Vue 组件、docker-compose 与文档的整体实现。
- **参考文档**：笔试题目与一份 Vue 3 前端参考代码（用于对齐消息协议与页面结构）。

## 使用方式

我采用「**人工定方向，AI 做执行，自己校验并理解**」的方式：

1. 先自己读题，明确 7 项核心功能与 Redis 必须真实解决的问题；
2. 让 AI 搭骨架、写大段样板代码（实体 / 仓储 / 服务 / 组件）；
3. 关键逻辑（BFS 算法、幂等结算、断线重连、匹配队列）我逐行 review，并向 AI 追问实现原理；
4. 每一步都实际编译 / 跑单测验证，而不是直接相信 AI 生成的代码。

## 关键设计决定

| 决定 | 理由 |
|---|---|
| 后端选 Spring Boot（而非 Node） | 更熟悉 Java 生态，JPA + WebSocket 能快速搭建；选型不影响评分 |
| 棋盘用 `10×10 = 8×8 + 一圈空边框` | 用「0 边框」天然支持路径从棋盘外绕行，BFS 无需特判越界 |
| 无回合、双方同时消除（竞速） | 更贴合「双方同时消除并实时看到对方进度」的题意，且实现简单 |
| 权威状态放 Redis、结果落 MySQL | 高频读写走 Redis，结算一次性持久化，幂等用 Redis SETNX + DB 唯一约束 |
| 不用完整 Spring Security，仅引入 BCrypt | 避免过滤器链复杂度；登录 token 用 Redis 会话自管理 |

## 一个亲自解决的问题

**问题：为什么后端一直编译不过，报 `org.springframework.web.socket` 和
`com.fasterxml.jackson.databind` 找不到？**

AI 最初生成的 `GameWebSocketHandler` / `WebSocketConfig` 引用了 WebSocket 与 Jackson 的类，
但 `pom.xml` 里并没有对应依赖（脚手架只勾选了 web / jpa / redis，漏了 websocket）。
我第一次 `mvn compile` 直接失败。

我的排查过程：先看报错里「程序包不存在」指向的是 WebSocket 配置类和 handler 里的
`ObjectMapper`，说明不是语法问题而是缺依赖；再对照 Spring Boot 的 starter 划分，确认
WebSocket 需要独立的 `spring-boot-starter-websocket`。补上该依赖（以及显式引入
`spring-security-crypto` 供 BCrypt 使用）后编译通过。

这件事让我意识到：**AI 生成的代码默认假设依赖已就绪，实际工程里「依赖完整性」必须由人
亲自用编译结果来兜底**，这也正是笔试反复强调「可运行、可验证」的原因。

> 另一个实际遇到的问题：后端第一次启动直接 `APPLICATION FAILED TO START`，报
> `Web server failed to start. Port was already in use`。排查发现是上一次启动残留的
> `java.exe` 进程仍占着端口，用 `netstat -ano | findstr :端口` 定位 PID 后
> `taskkill /PID <pid> /F` 结束即可重启。这提醒我：进程/端口这类「运行态」错误和代码
> 无关，先看日志最后一行报错再动手，别急着改代码。