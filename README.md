# Spring Boot 动态认证策略引擎（可运行 MVP）

支持通过后台 API 动态定义、发布、回滚（发布旧版本）多因子登录策略，并让 **新创建的登录流程即时使用新版本**
。已开始的登录流程继续使用创建时的策略快照。

## 一、技术栈与实现范围

- **Java 17+、Spring Boot 3.5.16、Spring Security 6、Spring Data JPA、Lombok**。
- 本地默认 **H2 内存库 + ConcurrentHashMap/CAS**，无需安装 MySQL/Redis 即可启动。
- 可选 **MySQL 8 + Redis 7（Lua CAS）**，适合演示同一策略数据下的多实例共享状态。
- AST 操作符：`FACTOR`、`ALL`、`ANY`、`AT_LEAST`、`SEQUENCE`。
- 已可演示的因子： **PASSWORD（BCrypt）**、 **SMS_OTP（服务端日志模拟短信供应商）**、 **APP_QR_APPROVAL（已认证 APP 的 Basic
  模拟）**。
- **PASSKEY：预留枚举和扩展接口，但未实现 WebAuthn。** 发布包含它的策略会被拒绝，不能误以为原始指纹已得到支持。
- 多版本策略持久化、发布、回退、进行中流程快照、验证码次数/过期限制、二维码一次性 Ticket、会话令牌发放与单次消费。

> ⚠️ **定位：可直接本地运行的教学/开发 MVP，不是可以直接暴露公网的生产认证服务。** 后文单独列出生产尚需补齐的能力。Spring
> Boot 3.5.x 的社区支持已经结束；选择 3.5.16 是为了与你原计划的 Spring Boot 3 / Spring Security 6
> 技术栈一致，实际新项目上线前应评估升级到仍受支持的版本。

## 二、5 分钟本地启动

环境：JDK 17 或 21、Maven 3.6.3+。

```bash
mvn clean test
mvn spring-boot:run
```

访问 `http://localhost:8080` 下的 `/api/**` 接口。项目仅有 REST API，没有前端登录页面。

本地演示账号（ **只在默认开发配置启用**）：

| 用户  | 密码           | 角色  | 手机（模拟） |
|-------|----------------|-------|--------------|
| alice | `Demo@123456`  | USER  | 13800000000  |
| admin | `Admin@123456` | ADMIN | 13900000000  |

默认登录策略是：

```text
ALL(
    ANY(PASSWORD, APP_QR_APPROVAL),
    SMS_OTP
)
```

含义：密码/APP 扫码任选其一，另外必须短信验证码。完整运行本地演示可执行：

```bash
bash scripts/demo-password-login.sh
```

此脚本 **会发布新策略（只需要密码），改变本地默认策略**，然后自动完成：创建 Attempt → 校验密码 → 完成登录 → Bearer 访问
`/api/me` → 检查重复消费失败。

## 三、手动体验“密码 + 短信”完整流程

下面示例在默认策略下使用 `password` 和 `sms` 叶子节点。如果之前运行过脚本修改了策略，可以发布
`examples/and-password-sms.json` 进行重置。

### 1. 创建登录流程

```bash
curl -i -c cookies.txt -X POST http://localhost:8080/api/login/attempts \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice"}'
```

响应类似：

```json
{
  "attemptId": "<随机ID>",
  "policyCode": "WEB_LOGIN",
  "policyVersion": 1,
  "status": "IN_PROGRESS",
  "expiresAt": "<ISO时间>",
  "available": [
    {
      "nodeId": "password",
      "type": "PASSWORD"
    },
    {
      "nodeId": "qr",
      "type": "APP_QR_APPROVAL"
    },
    {
      "nodeId": "sms",
      "type": "SMS_OTP"
    }
  ],
  "progress": {
    "password": "PENDING",
    "qr": "PENDING",
    "sms": "PENDING"
  },
  "canComplete": false
}
```

记下响应中的 `attemptId`，将下面的 `$ID` 换成你的值。`cookies.txt` 中存储浏览器绑定 Cookie。

### 2. 校验密码

```bash
curl -b cookies.txt -X POST \
  "http://localhost:8080/api/login/attempts/$ID/factors/password/verify" \
  -H 'Content-Type: application/json' \
  -d '{"credential":"Demo@123456"}'
```

通过后 `progress.password=VERIFIED`，由于 `ANY` 已被满足，`qr` 将不再出现在 `available` 中。仍需短信因子。

### 3. 发起短信挑战

```bash
curl -b cookies.txt -X POST \
  "http://localhost:8080/api/login/attempts/$ID/factors/sms/start"
```

**验证码不会返回给浏览器**。本地演示服务会在 Spring Boot 启动控制台打印类似：

```text
[仅本地演示 / NEVER IN PRODUCTION] SMS mobile=13800000000 code=123456
```

读取你这次调用实际输出的验证码，提交：

```bash
curl -b cookies.txt -X POST \
  "http://localhost:8080/api/login/attempts/$ID/factors/sms/verify" \
  -H 'Content-Type: application/json' \
  -d '{"credential":"实际收到的6位验证码"}'
```

若 `canComplete=true`，即满足整棵策略树。

### 4. 消费 Attempt，签发登录令牌

```bash
curl -b cookies.txt -X POST \
  "http://localhost:8080/api/login/attempts/$ID/complete"
```

返回 `accessToken`（只显示一次）。使用它：

```bash
curl -H 'Authorization: Bearer <accessToken>' http://localhost:8080/api/me
```

再次提交相同 Attempt 的 `complete` 应收到 **403**，防止一个流程重复签发多个令牌。

## 四、动态修改登录组合 / 发布即时生效

新增版本：

```bash
curl -u admin:Admin@123456 -X POST \
  http://localhost:8080/api/admin/policies/WEB_LOGIN/draft \
  -H 'Content-Type: application/json' \
  --data-binary @examples/and-password-sms.json
```

响应包含 `version`，例如 `2`。发布版本：

```bash
curl -u admin:Admin@123456 -X POST \
  http://localhost:8080/api/admin/policies/WEB_LOGIN/publish/2
```

读取当前策略：

```bash
curl -u admin:Admin@123456 \
  http://localhost:8080/api/admin/policies/WEB_LOGIN/active
```

读取版本历史：

```bash
curl -u admin:Admin@123456 \
  http://localhost:8080/api/admin/policies/WEB_LOGIN/versions
```

**发布完成后**，下一次调用 `POST /api/login/attempts` 会读取数据库中的新生效版本；旧 Attempt 不更改策略树和原来的
`policyVersion`。发布历史版本可实现回滚，例如 `/publish/1`。

### JSON AST 语法

```json
{
  "op": "AT_LEAST",
  "required": 2,
  "children": [
    {
      "op": "FACTOR",
      "id": "password",
      "factor": "PASSWORD"
    },
    {
      "op": "FACTOR",
      "id": "sms",
      "factor": "SMS_OTP"
    },
    {
      "op": "FACTOR",
      "id": "qr",
      "factor": "APP_QR_APPROVAL"
    }
  ]
}
```

- `FACTOR`：一个可独立提交的认证节点，必须有合法、唯一 `id`。
- `ALL`：所有子节点通过；`ANY`：任意一个子节点通过。
- `AT_LEAST`：至少 N 个 **不同**认证因子通过；`SEQUENCE`：按顺序完成。
- 每棵树最多 32 个节点、8 层深度、16 个叶子。组合节点至少 2 个子节点。
- 示例设计上禁止重复使用同一 `FactorType`，防止把同一个因素重复计为 MFA。
- **不代表任意两种因子天然满足 MFA 保障级别。** 因素独立性还需要结合认证来源、用户验证属性、设备信任等级判断。

可参考 `examples/` 中的策略。`complex-login.json` 专门包含尚不可用的 `PASSKEY`，用来验证发布校验会拒绝未安装的认证器。

## 五、模拟 APP 扫码登录

先发布 `examples/at-least-two.json` 或保持默认版本（它包含 `qr` 因子）。

1. 浏览器创建 Attempt：`POST /api/login/attempts`。
2. 浏览器 `POST /api/login/attempts/$ID/factors/qr/start`，携带 `cookies.txt`。
3. 响应 `qrTicket` 是需要 APP 扫描的随机 Ticket 原文； **它不是 accessToken**，90 秒后过期。
4. APP 端模拟已经认证的 `alice`（这里用 HTTP Basic 模拟现有 APP 登录态）：

```bash
curl -u alice:Demo@123456 -X POST \
  http://localhost:8080/api/app/qr/approve \
  -H 'Content-Type: application/json' \
  -d '{"ticket":"把第2步返回的 qrTicket 填在这里"}'
```

5. 浏览器 `GET /api/login/attempts/$ID` 轮询，看到 `qr=VERIFIED`。若还有 SMS 等条件，继续执行；完全满足后才能调用`complete`。

**APP Basic 只是为了演示跨设备授权，不代表生产环境真的实现了 APP 登录态。** 真正 APP 应使用独立 Token、身份绑定、用户确认和扫码
UI。更不能把“复用同一个密码验证的 Basic”错误地算作独立持有因素。

## 六、MySQL + Redis 运行

本地装有 Docker 时：

```bash
docker compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=demo-redis
```

`demo-redis` 用 MySQL 保存用户和策略，用 Redis 保存 Attempt 与 opaque 会话，仍会启用 **演示用户和控制台短信**
，只可本机测试。MySQL/Redis 端口会映射到宿主机，应限制访问范围。

`application-prod.yml` 是独立的生产环境接线示意：需要先执行 `sql/mysql-schema.sql`（或转成 Flyway 迁移）、导入可信用户身份数据、配置正式
SMS 实现和 APP 认证实现、设置 TLS/秘钥/网络访问策略，并禁用固定演示账号。 **不能只切换 `prod` Profile
就认定符合生产安全要求。**

## 七、主要目录

```text
src/main/java/com/example/authengine/
├── domain/       # AST、校验、纯函数求值（不依赖 Spring）
├── policy/       # 策略 JPA 主表、历史版本、Draft / Publish / Rollback
├── factor/       # 登录因子 SPI + 密码 / 短信 / APP扫码
├── attempt/      # 登录流程、状态、内存/Redis CAS
├── security/     # AuthenticationProvider、独立 SecurityFilterChain、会话
├── api/          # REST Controller、DTO
├── user/         # 演示用户模型、UserDetailsService
├── config/       # 配置属性、安全链
├── common/       # 安全随机数、异常处理
└── bootstrap/    # 演示数据初始化
src/test/java/      # AST 规则测试、端到端 MVC 集成测试
examples/            # 多种 AST 策略 JSON
scripts/             # 一键演示
sql/                 # MySQL 建表参考
```

## 八、运行与测试状态

- `src/test/java/.../PolicyEvaluatorTest`：ALL / ANY、AT_LEAST、SEQUENCE、重复因素、缺少认证器。
- `src/test/java/.../LoginFlowIntegrationTest`：策略热更新、旧 Attempt 快照、密码登录、令牌单次消费、浏览器绑定、扫码确认、策略管理权限。
- 源码环境对纯 Java AST 已执行 `javac --release 17` 编译和断言式 Smoke Test 并通过。
- **当前交付环境没有 Maven 且无法联网拉取 Maven Central 依赖，因此没有条件执行完整 `mvn test` 和启动 Spring Boot 服务。**
  请按第二节在你本机运行；如果遇到具体的编译/运行错误，可以根据错误继续修正。

更多设计决策、安全边界见 [docs/architecture.md](docs/architecture.md)。
