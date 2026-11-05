# Kite for Android

在手机上写 [Kite](https://github.com/kite-plus/kite) 的文章：离线能写，回到网络里自动同步回电脑。它是 Kite 的**第三个界面**，不是第二个实现——渲染、字数、预览、内容类型都问服务端，客户端只负责把手写下来并存回去。

- 界面上的改动**先落本地数据库**，再由同步引擎推给服务器，断网、电脑关机都不影响写。
- 客户端代码由服务端的 OpenAPI 文档**生成**，不是手写请求（见[架构](#架构)）。
- 只使用 `/api/v1` 现有端点，**不改 Kite 本体**。

## 现在能做什么

| 能力 | 说明 |
|---|---|
| 离线写 | Room 存全部文章，界面只读本地库，不等网络 |
| 双向同步 | 先推后拉；`updated_from` 只取变化，游标翻页；电脑上删掉的条目也会跟着消失 |
| 冲突不丢稿 | 服务器说版本过期（409）时两版都留着，编辑页让你二选一 |
| 配图与封面 | 从相册选图直接传到服务器，正文插图或写成 `meta.cover`，与网页后台一致 |
| 预览 | 走服务端 `/preview`，WebView 里关掉 JS，图片以文章自身地址解析 |
| 字数 | 问服务端 `/wordcount`，未联通时退回本地计数 |
| 分类与字段 | 按 `/content-types` 声明的 schema 渲染：slug、分类 chips、布尔开关、数字、文本 |
| 状态与定时发布 | 草稿 / 已发布 / 定时 / 归档，定时可挑日期 |
| 局域网发现 | 扫手机自己所在的私网网段，失败时报告扫了哪些网段、本机地址是什么 |
| 后台同步 | WorkManager 每 6 小时、有网时跑一次；前台打开、下拉、编辑停手也会同步 |
| 崩溃留痕 | 未捕获异常写进 `logs/crash.txt`，方便取回 |

还没做：富文本编辑（写的是 Markdown 源码）、附件列表、回收站界面、发布到 GitHub、多站点。这些在[路线图](docs/ROADMAP.md)的 M3/M4。

## 快速开始

**1. 电脑上起服务**

```bash
kite run
```

默认只监听 `127.0.0.1`，配好连接方式之后再决定要不要对局域网开放（见下一节）。

**2. 让手机连上电脑**，三种方式，按安全程度排：

| 方式 | 电脑端 | 手机里填的地址 | 代价 |
|---|---|---|---|
| **USB（推荐）** | `kite run` + `adb reverse tcp:1717 tcp:1717` | `http://127.0.0.1:1717` | 拔线就断；重插后要重跑一次 reverse |
| **同一 WiFi** | `kite run --addr <电脑局域网 IP>:1717` | `http://<电脑局域网 IP>:1717` | 明文，只适合自家网络 |
| **Tailscale 等** | `kite run` + 装上并登录 | `http://100.x.x.x:1717` | 需要装第三方客户端 |

**3. 手机 App 里**：设置页填地址 → 点「测试连接」（不用密码，探 `/api/v1/setup`）→ 填用户名密码登录 → 点「立即同步」。

密码不会落盘，会话靠 cookie 维持，过期后应用会让你重新登录。

> 局域网走明文 HTTP（`usesCleartextTraffic`），只在自己信任的网络里用。不要把 1717 直接映射到公网——Kite 目前没有 TLS，账号与会话会裸奔。连接方式的完整讨论记录在 [docs/CONNECTION.md](docs/CONNECTION.md)。

## 构建

工程已跟随 Android Studio 升到 **Gradle 9.5 + AGP 9.3 + Kotlin 2.2 + KSP2**，构建 JDK 用 21 及以上（推荐直接用 Android Studio 自带的 `jbr`），产物按 Java 17 编。

- **用 Android Studio**：`File → Open` 打开本目录即可；仓库里没有 `gradlew` 与 `gradle-wrapper.jar`，Studio 会用自己那份 Gradle。依赖走阿里云镜像、发行版走腾讯镜像，`settings.gradle.kts` 里已配好。
- **命令行**（本机装有 Gradle 时）：

```bash
gradle :app:testDebugUnitTest :api:testDebugUnitTest
gradle :app:assembleDebug        # 产物 app/build/outputs/apk/debug/app-debug.apk
```

**重新生成客户端**（服务端契约变化之后）：

```bash
./tools/generate-api.sh          # 需要 JDK；缓存生成器 jar，并整体替换 api/src/main/java
```

脚本默认从 `http://127.0.0.1:1717/api/v1/openapi.json` 拉文档，可用参数改地址。

## 架构

```
app/                         界面与业务
├── ui/            Compose 三页：列表 / 编辑 / 设置
├── data/          设置、仓库、内容类型、发现、上传、服务端渲染
│   ├── local/     Room：ContentEntity / ContentDao / KiteDatabase
│   └── remote/    KiteGateway（唯一接触生成代码的地方）、cookie 持久化
├── domain/        同步：SyncEngine 编排，Pusher / Puller / ConflictResolver 分工
└── work/          WorkManager 后台同步
api/                         由 OpenAPI 文档生成的客户端（不要手改）
```

几条规则：

- **依赖单向**：`ui → data → domain`；`domain` 不 import Android，所以能在 JVM 上直接测。
- **数据库是唯一真相**：屏幕只读 Room，从不直接读网络；同步引擎是唯一往上说话的东西。
- **同步顺序固定**：先推后拉。反过来会先覆盖掉离线写过的内容。
- **跨模型的转换只有两处**：`KiteGateway`（生成模型 ↔ 网络）与 `ContentMapping`（生成模型 ↔ 数据库行）。别处出现转换就是漏了一层。
- **契约以 spec 为准**：`api/openapi.json` 是服务端按路由表生成的文档，抓下来就能重生成客户端。

### 同步怎么处理这几种情况

| 情况 | 做法 |
|---|---|
| 列表行没有正文 | 列表只更新元数据，正文保留本地；没见过的条目先 `GET /contents/{id}` 读全文再落库 |
| 电脑上删了 | `GET /contents?deleted_only=true` 单独一遍；本地有未上传改动的行不删，并扣住水位 |
| 版本冲突（409） | 本地正文不动，服务器版本存进冲突字段，编辑页给两个选择；下次同步不再重试这一行 |
| 会话过期（401） | 停止这一轮同步并提示重新登录，不再刷请求 |
| 服务端只读（405） | 明确提示，不当成网络问题重试 |

## 测试

```bash
gradle :app:testDebugUnitTest
```

21 个 JVM 单测 + 生成客户端的 2 个：同步（推/拉/冲突/删除/水位）、仓库（无改动不标脏、meta 合并、定时日期）、发现（网段筛选）、上传（multipart 字段名与 415）、服务端渲染（预览与字数）。

没有 instrumented 测试，也没有截图对比——真机交互靠手测；客户端能说“通过”的边界就到这里。

## 排错

**闪退但拿不到堆栈**：`CrashLog` 会写到 `/sdcard/Android/data/com.kiteplus.kite.mobile/files/logs/crash.txt`，用 `adb pull` 取回；USB 调试时 `adb logcat *:E` 更快。

**连不上**：

1. 必须跑 `kite run`，不是 `kite serve`——serve 只有静态站点，`/api/v1/*` 一律 404。
2. 地址填局域网 IP，不要填 `127.0.0.1`（除非走 USB 的 `adb reverse`）；不要带 `/api/v1` 后缀。
3. 手机和电脑要在同一网段；路由器开了客户端隔离（访客网络常见）会挡住。
4. Windows 防火墙首次弹窗时，专用/公用网络都要放行；网络类别是「公用」时，只放行「专用」的规则不生效。
5. 电脑的地址是 DHCP 租的，会变；变了就在 App 里重新填，或在路由器上做地址绑定。

## 文档

- [docs/ROADMAP.md](docs/ROADMAP.md) — 里程碑与工程基线
- [docs/CONNECTION.md](docs/CONNECTION.md) — 连接方式的现状、候选与取舍（只记录，未决定）

## 许可

[Apache-2.0](LICENSE)，与 Kite 本体一致。
