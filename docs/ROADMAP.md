# Kite 移动端路线图

这个文档只规划**移动端自己**的演进。一条硬规则贯穿全程：**不改 Kite 本体**——客户端只能使用 `/api/v1` 现有的端点与行为，凡需要服务端配合的想法，一律记在最后一节「与 Kite 本体的整合对比」，等移动端自己走通再单独评估、单独批准。

## 一、定位

Kite 有三个界面：web admin、CLI、以及这里说的移动端。移动端不是第三个实现，而是第三个界面——渲染、字数、预览、发布流程全部沿用服务端的实现（`POST /preview`、`POST /wordcount`、发布三步），客户端只负责「把作者的手写下来并同步回去」。

因此移动端**不做**：自己解析 Markdown、自己做发布预检、自己判断内容类型的字段含义。

## 二、工程基线

这些要求在每个里程碑里都有效，不单列为某一阶段。

| 方面 | 要求 |
|---|---|
| 契约 | 由 `/api/v1/openapi.json` 生成客户端代码（架构文档 §19）；契约快照 `api/openapi.json` 入库，生成物不入库 |
| 手写请求 | CI 脚本禁止 Retrofit 注解出现在 generated 目录之外 |
| 分层 | `ui → domain → data` 单向；`domain` 不 import Android；等价于 Kite 的 `scripts/check-imports.sh` |
| 提交 | Conventional Commits，小写、带 scope、结尾不加句号 |
| 门禁 | `make mobile-check` = 生成契约 → ktlint → detekt → 单测 → assembleDebug |
| 测试 | 单测覆盖契约映射、同步、仓储、发现；不做 instrumented 与截图测试，改写真机手测清单 |
| 凭据 | 服务端密码不落盘；keystore 与签名配置不进仓库 |

## 三、里程碑

### M0 · 契约合规 spike

- **目的**：先证明「由 spec 生成 Kotlin 客户端」这条路在本机能跑通，再决定要不要全量替换。
- **做法**：`kite openapi` 导出 `api/openapi.json` → openapi-generator（`kotlin` 模板 / `jvm-retrofit2` / gson）生成 models 与接口 → 只拿一个端点（`GET /contents`）编译并跑一个 MockWebServer 测试。
- **验收**：生成物能编译、能发一次真实请求、DTO 字段与服务端一致；记录模板坑（日期类型、nullable、security 生成的冗余参数）。
- **风险**：生成器对 `security` 的处理方式可能逼着我们改认证注入点。若不可行，此里程碑以一份结论收尾，不硬推。
- **结论（2026-11-05，已实测）：路子通，M1 按生成式推进。**
  - 契约：`/api/v1/openapi.json` → `api/openapi.json`，90,659 字节，OpenAPI 3.1.0，**44 条路径 / 74 个 schema**。
  - 生成：openapi-generator **7.10.0**，`-g kotlin --library jvm-retrofit2`，`serializationLibrary=gson`、`dateLibrary=string`、`useCoroutines=true`，产出 **84 个 .kt**（models + 单个 `DefaultApi` 接口 + `infrastructure/`）。
  - 编译：放进独立 `:api` 模块（android-library），`:api:compileDebugKotlin` 通过。
  - 行为：`api/src/test/java/.../GeneratedClientTest.kt` 2/2 绿——`GET /api/v1/contents` 的路径与 `limit`/`updated_from` 查询名正确；`PUT` 带 `If-Match`、body 只含 Draft 的 10 个字段。
  - 版本与命令固化在 `tools/generate-api.sh`（`OPENAPI_GENERATOR_VERSION` 可覆盖）。
  - 已知模板怪相（M1 要处理）：`deleted_only` 被生成成 `String?`（spec 里是布尔查询参数）；`DefaultApi` 把 44 条路径塞进一个千行接口；`security` 生成 `ApiKeyAuth`，会话 cookie 仍需我们在 OkHttp 层接管。

### M1 · 分层重构 + 生成式客户端

- **范围**：
  - `EditorScreen.kt`（848 行）拆成 `EditorViewModel` + 无状态组件；状态与副作用离开 Composable。
  - `SyncEngine.kt`（434 行）拆成 `Pusher` / `Puller` / `ConflictResolver` / `SyncOrchestrator`。
  - 手写 `KiteApi`/DTO 全量换成生成物，中间加一层 `KiteGateway` 做「生成模型 ↔ 数据库实体」的唯一转换点。
  - CI 与 `make mobile-check` 就位。
- **验收**：现有单测（19 个）全绿且不改断言；`check-no-handwritten-api.sh` 在故意加一个 `@POST` 时失败；APK 行为与重构前一致（编辑、保存、冲突、预览、配图、发现）。
- **理由**：后面要加的都是页面与端点，先用正确的骨架承接，比事后再拆便宜。

### M2 · 设计系统对齐 admin

- **范围**：从 `web/src/styles/theme.css` 取 tokens 建 `designsystem/`——canvas `#EDF2F9`、surface `#FFFFFF`、ink `#172036`、primary `#4A77D6`、次要文字 `#56627A`、描边 `#E6EBF2`、输入框 `#D6DDE8`、圆角 10dp；站点 accent（`theme.settings.accent`）作为强调色。组件：`KiteTopBar`、`KiteSheet`、`KiteChip`、`KiteField`、`StatusChip`。
- **版式**：编辑页顶部改为「标题 + 摘要 + 封面」，正文居中，属性面板收地址/状态/分类/字段；列表页改为卡片行（标题、摘要、状态、时间）。
- **验收**：与 admin 编辑页并排截图对照，颜色/圆角/间距差异逐项记录；编辑页可完成「改标题 + 摘要 + 封面 + 正文 + 分类 + 状态」全流程。
- **非目标**：深色模式（v1.1）。

### M3 · admin 对等功能

- **发布到 GitHub**：`GET /publish` 状态 → `POST /publish/preflight` → `POST /publish`（必要时 `/publish/push`）；失败时按服务端错误原样呈现。
- **回收站与恢复**：列表加「回收站」入口，走 `?deleted_only=true` 与 `POST /contents/{id}/restore`。
- **附件管理**：`GET /contents/{id}/media` 列出、`DELETE .../{name}` 删除、`PUT .../{name}` 替换；不做裁剪。
- **摘要与封面**：`description` 与 `cover` 两个字段提到标题下方（M2 的版式已预留位置）。
- **验收**：每一项都能在真机上对一台真实 Kite 完成一次往返，且电脑端 admin 能看到结果。

### M4 · 多站点

- **范围**：站点配置表（名称、地址、认证状态、同步水位、上次同步时间）；Room 主键改 `(siteId, id)`；每站点一个 WorkManager 唯一任务；顶栏可切换站点。
- **验收**：两个站点交替编辑不串数据；同一站点在后台同步时不阻塞当前站点的编辑。

### M5 · 远程访问

- **范围**：只接受 `https://` 远程地址；证书两条明路——导入系统 CA，或应用内 TOFU 指纹固定（仅对该 host 生效，弹窗写明风险）；二维码导入地址；局域网沿用现有发现。
- **非目标**：明文 http 远程（局域网 http 保留）、自建中转。
- **验收**：在手机移动网络下连一台经隧道的 Kite，完成一次拉取、编辑、推送、预览；指纹变更时给出明确警告而不是静默连接。

### M6 · 分发

- **范围**：签名配置（secrets/local，不入库）、版本策略（`versionName` 语义版本从 1.0.0 起、`versionCode` 单调递增）、GitHub Releases 产出 APK + sha256、设置页「有新版本」提示（只提示不静默下载）、真机手测清单。
- **验收**：从 Releases 下载安装、覆盖升级不丢数据；手测清单逐项通过。

## 四、与 Kite 本体的整合对比（收尾动作）

移动端各里程碑走完后，做一次对照评估。评估方法：以 `api/openapi.json` 为基准逐条比对「客户端想做但服务端没有的能力」，产出候选清单交你决定；**任何一条落地都要单独走设计文档与批准**。

已识别的候选：

| 候选 | 为什么移动端会想要 | 影响面 |
|---|---|---|
| TLS（`--tls-cert/--tls-key`） | 让远程访问不必人人架隧道 | 服务端启动、证书管理、文档 |
| API Token（V3 规划） | 移动端不必长期持有 cookie 会话 | 认证、OpenAPI security、Scope |
| 发布状态机的中间态 | 移动端网络中断后无法知道发布进行到哪一步 | 发布流程、幂等性 |
| 站点多实例注册 | 一个客户端管理多台 Kite 时的「添加站点」体验 | 无（客户端自解决） |

连接与发现的完整候选（二维码自述、mDNS、TLS、API Token、中继）与取舍记录在 [CONNECTION.md](CONNECTION.md)，目前**只记录、未决定**。

## 五、已确认的决策（2026-11-05）

| 决策 | 结论 |
|---|---|
| 界面语言 | **中英双语，v1 就做**。所有可见文案走 `strings.xml` + `values-en/strings.xml`，代码里不出现中文字面量；新增文案两处一起改 |
| 里程碑顺序 | 按 **M0 → M6** 顺序推进，不插队 |
| 地址二维码 | **v1 不做**，记进第四节候选（要改 admin 才能生成，属 Kite 本体） |
| 生成式客户端 | 先跑 **M0 spike**，用结果决定全量替换还是保留手写并记录差距 |

尚未决定、留到对应里程碑再谈：深色模式（v1.1）、附件缩略图（v1.1）。
