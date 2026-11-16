# Auth Policy Engine - 前端控制台 (Vue 3 + TypeScript)

本项目为 **Spring Boot 动态认证策略引擎** 专门配套的现代化前端交互验证控制台。

## 双重使用模式

### 模式 A：零安装直接运行（默认推荐）
前端页面已被直接编译并内嵌在 Spring Boot 的静态资源中 (`src/main/resources/static/index.html`)。

无需安装 Node.js 或运行额外的前端开发服务器，只要启动 Spring Boot：

```bash
mvn spring-boot:run
```

启动完成后，直接在浏览器中打开：
👉 **`http://localhost:8080/`** 即可立即体验全功能前端！

---

### 模式 B：独立前端工程开发（Vite + Vue 3 + TS）
如果你需要对前端页面进行组件定制、二次开发或扩展样式，可以使用本目录下的源码工程：

```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务器（已配置反向代理至 http://localhost:8080）
npm run dev

# 编译打包并同步至 Spring Boot 静态资源目录
npm run build
```

开发服务器将启动在 `http://localhost:5173`。

---

## 核心功能模块

1. **🔑 登录流程动态验证台 (Login Flow Wizard)**
   - 动态读取并解析服务端 AST 策略树。
   - 智能识别当前可用因子（Available Nodes）：密码、短信验证码、APP 扫码。
   - 递归策略树实时状态高亮（PENDING、CHALLENGE_ISSUED、VERIFIED、EXHAUSTED）。
   - 实时倒计时、重试次数限制、CAS 状态机流转展示。
   - 完成条件满足时，一键换取 Bearer 会话令牌，并调用受保护的 `/api/me` 接口。

2. **⚙️ 策略编排与热更控制台 (Policy Studio & Hot-Reload)**
   - 预置 8 种典型认证策略模板（默认组合、单因子密码、强双因子、二选一、三选二投票、顺序流转、非法Passkey拦截、重复因子拦截）。
   - AST 策略 JSON 可视化编辑、草稿保存、一键即时发布。
   - 历史版本查看与一键版本回退（Rollback）。
   - 演示进行中流程快照隔离特性（已开始流程不受新策略发布影响）。

3. **📱 移动端 APP 扫码模拟端 (Mobile Scanner Simulator)**
   - 独立模拟已认证的移动端 APP 客户端。
   - 支持切换 APP 登录身份（alice / admin）。
   - 一键获取当前登录 Attempt 的 QR Ticket 并调用 `/api/app/qr/approve`。
   - 支持跨账号扫码拦截测试（例如 alice 的登录被 admin 扫码，预期拦截返回 403）。

4. **🛡️ 安全防御机制实测实验室 (Security Edge Cases Lab)**
   - **防重放攻击实测**：再次调用 `/complete` 接口消费同一 Attempt，验证原子 CAS 防御（返回 403 Forbidden）。
   - **Cookie 绑定防御实测**：伪造或去除 `AUTH_BIND` HttpOnly Cookie，验证防跨浏览器劫持（返回 403 Forbidden）。

5. **📡 实时 API 与 CAS 状态审计日志 (Audit Inspector)**
   - 全程抓取前端发出的每一笔 HTTP 请求、耗时、状态码、请求体与服务端响应 JSON。
