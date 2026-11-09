# 架构和安全边界

## 核心组件

- `domain/PolicyNode`：仅包含已知操作符的 AST，不执行任意动态表达式。
- `domain/PolicyValidator`：静态校验叶子数量/深度/认证器是否安装；同一种认证器不允许重复计数。
- `domain/PolicyEvaluator`：纯函数，计算已满足、待完成、不可完成以及当前可用节点。
- `factor/FactorHandler`：密码、短信和扫码的认证器 SPI；认证器不能绕过策略自己签令牌。
- `attempt/AttemptService`：登录状态机、账号绑定、浏览器绑定、限次、挑战和原子状态转移。
- `policy/PolicyService`：MySQL/H2 版本化策略，发布后更新 active_version；运行中的 Attempt 保存不可变策略快照。
- `attempt/*AttemptRepository`：本地内存 CAS 或 Redis Lua CAS（redis 模式支持多个实例）。
- `security/LoginFlowAuthenticationProvider`：只有整个 AST 满足才能消费 Attempt、生成认证主体。
- `security/SessionTokenService`：签发高熵 opaque token，服务器只保留哈希，30 分钟过期。

## 状态变化

```text
IN_PROGRESS
  ├─ 全部条件满足 → SUCCEEDED → （单次完成接口，CAS）→ CONSUMED
  └─ 所有可行路径耗尽 → FAILED
任何状态超过 expiresAt → 不再可访问（存储过期）
```

每个因子独立记录 `failures`、`verified`、`exhausted`、`challengeHash`、`challengeExpiresAt`、`challengeSends`。

`ANY` 的一个分支成功，其他分支即不可再提交；`AT_LEAST` 可在完成 N 个后立即满足；`SEQUENCE` 仅开放第一个未通过的子树。

## 安全防线

1. 不允许客户端传入策略代码选择弱策略；策略由服务端 `app.auth.policy-code` 决定。
2. 每次 attempt 创建绑定服务端查出的唯一用户 ID，不会将不同账户的认证结果拼接。
3. 浏览器登录流程用 HttpOnly、SameSite=Strict Cookie 绑定；二维码由独立的 APP 登录态 Basic 模拟确认。
4. 策略定义只允许白名单操作符；PASSKEY 未安装无法发布包含 PASSKEY 的策略。
5. 密码使用 BCrypt；短信验证码只保存 BCrypt 哈希、120s 过期；二维码仅保存随机 Ticket 哈希、90s 过期。
6. 更新和消费使用原子 CAS；Ticket 和完成接口不可重复使用。
7. 管理后台 Basic 与网页登录 Bearer 分离，不能通过 HTTP Basic 访问 `/api/me` 绕过策略。
8. 政策发布以数据库提交为生效线性化点；新流程直接读取数据库 active_version，多实例共享数据源。

## 本示例尚未实现的生产安全能力

- 真正 WebAuthn/FIDO2 Passkey（包括注册、RP ID/Origin 验证、签名计数及用户验证）。
- 真实 APP OAuth2/OIDC 登录态和安全设备绑定，当前 Basic 只是模拟扫码 APP 已认证。
- 正式短信发送网关（演示场景把验证码打印在服务端控制台）。
- 账户/IP/设备全局速率限制、IP 风险分析、审计日志、登录异常报警。
- 因素独立性及认证保证级别校验。密码 + 使用同一密码模拟的 APP Basic， **不能当成真正独立双因素**。
- 管理发布审批、RBAC 细分、审计和基于多租户/风险的策略路由。
- 数据库迁移框架、HA 运行、Redis 高可用、会话撤销和 SSO、硬化 Cookie 前缀以及 HTTPS 配置。

因此：这是一个具有可运行端到端示例和关键隔离机制的 **教学/开发 MVP**，不是直接投放公网的成品认证中心。
