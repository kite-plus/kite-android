<h1 align="center">Kite for Android</h1>

<p align="center">
  <b>在手机上写。没网也写得下去。有网就同步回去。</b>
</p>

<p align="center">
  <a href="https://github.com/kite-plus/kite">Kite</a> 的第三个界面：它不重新实现渲染、字数、预览或内容类型，<br>
  那些都问服务端；它只负责把作者的手机输入落进本地数据库，再送回电脑上的那个文件夹。
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/github/license/Amazon2025/kite-android?style=flat-square&color=4a77d6&label=license" alt="License"></a>
  <a href="gradle/libs.versions.toml"><img src="https://img.shields.io/badge/kotlin-2.2-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin"></a>
  <a href="app/build.gradle.kts"><img src="https://img.shields.io/badge/android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android 8.0+"></a>
  <a href="docs/ROADMAP.md"><img src="https://img.shields.io/badge/status-M1%20in%20progress-4a77d6?style=flat-square" alt="Status"></a>
</p>

<p align="center">
  <a href="docs/ROADMAP.md"><b>路线图</b></a> ·
  <a href="docs/CONNECTION.md"><b>连接方式</b></a> ·
  <a href="https://github.com/kite-plus/kite"><b>Kite 本体</b></a> ·
  <a href="README.md">English</a> · 简体中文
</p>

## 为什么用它

<table>
  <tr>
    <td width="50%" valign="top">
      <h3>先写下来，再说网络</h3>
      <p>文章存在手机本地的 Room 数据库里，界面只读数据库。地铁里、电脑关机、路由器断了，都能照常写；没有“保存”按钮，停手就落库。</p>
    </td>
    <td width="50%" valign="top">
      <h3>冲突不替你选，也不丢一边</h3>
      <p>服务器说“你看到的版本已经过期”时，两个版本都留着：本地正文不动，对面那份存进冲突字段，编辑页给两个选项。默认不覆盖，也不悄悄合并。</p>
    </td>
  </tr>
  <tr>
    <td valign="top">
      <h3>写法和网页后台一样</h3>
      <p>分类、slug、状态、定时发布，以及站点内容类型声明的字段，都从 <code>/content-types</code> 读来再渲染。配图直接传到服务器，封面写进 <code>meta.cover</code>。</p>
    </td>
    <td valign="top">
      <h3>预览和字数是服务端算的</h3>
      <p>预览走 <code>POST /preview</code>，拿到的是站点真正渲染出的整页 HTML；字数问 <code>POST /wordcount</code>。客户端不自己跑 Markdown 库，就不会和线上页面分叉。</p>
    </td>
  </tr>
  <tr>
    <td valign="top">
      <h3>契约由文档生成</h3>
      <p>客户端代码来自服务端 <code>/api/v1/openapi.json</code>（路由表与文档同源）。契约变了就重跑 <code>tools/generate-api.sh</code>，字段对不上是编译期报错，不是运行期的空白。</p>
    </td>
    <td valign="top">
      <h3>只动客户端</h3>
      <p>只用 <code>/api/v1</code> 现有端点，不改 Kite 本体；需要服务端配合的想法先记进文档，等你决定。后台每 6 小时在有网时兜底同步一次。</p>
    </td>
  </tr>
</table>

## 快速开始

电脑上起服务，绑到它的局域网地址（默认只听回环，手机进不来）：

```bash
kite run --addr 192.168.1.20:1717        # 换成电脑自己的局域网 IP
```

手机上：设置页填同一个地址（**不要带 `/api/v1`**，应用自己补）→ 点「测试连接」→ 用 Kite 后台那套账号登录 → 点「立即同步」。

电脑的地址由 DHCP 分配，会变；变了就在应用里重填。局域网走明文 HTTP，只在自己信任的网络里用，也不要把 1717 映射到公网——Kite 目前没有 TLS。

## 构建

工程用 Gradle 9.5 + AGP 9.3 + Kotlin 2.2 + KSP2，JDK 21 及以上（推荐 Android Studio 自带的 `jbr`），产物按 Java 17 编。仓库里没有 `gradlew` 与 `gradle-wrapper.jar`：用 Android Studio 打开本目录即可；本机装过 Gradle 时也可以直接用命令行。

```bash
gradle :app:testDebugUnitTest :api:testDebugUnitTest
gradle :app:assembleDebug        # 产物 app/build/outputs/apk/debug/app-debug.apk
```

依赖走阿里云镜像、发行版走腾讯镜像，`settings.gradle.kts` 里已配好。服务端契约变化后重生成客户端：

```bash
./tools/generate-api.sh          # 需要 JDK；缓存生成器 jar，并整体替换 api/src/main/java
```

## 它怎么工作

**手机上有自己的一份数据。** 所有文章在 Room 里，界面只读本地库，不等网络。同步引擎是唯一往上说话的东西。

**同步先推后拉。** 顺序反过来会先覆盖掉你离线写的东西。

| 情况 | 做法 |
|---|---|
| 推 | 新建走 `POST`，id 换成服务器给的；改动走 `PUT`，带上当初看到的 `If-Match` |
| 拉 | `updated_from` 只取变化，游标翻页；列表行里没有正文，所以已有行保留本地正文，新条目先读全文再落库 |
| 删除 | 单独走 `deleted_only=true`；本地有未上传改动的行不删，并扣住水位 |
| 冲突 | 409 时两版都留，等你选；下次同步不再重试这一行 |
| 会话过期 / 只读 | 401 与 405 分开处理，不当成网络故障重试 |

## 结构

```
app/                         界面与业务
├── ui/            Compose 三页：列表 / 编辑 / 设置
├── data/          设置、仓库、内容类型、发现、上传、服务端渲染
│   ├── local/     Room：ContentEntity / ContentDao / KiteDatabase
│   └── remote/    KiteGateway（唯一接触生成代码的地方）、cookie 持久化
├── domain/        同步：SyncEngine 编排，Pusher / Puller / ConflictResolver 分工
└── work/          WorkManager 后台同步
api/                         由 OpenAPI 文档生成的客户端，不要手改
```

几条规则：依赖单向（`ui → data → domain`，`domain` 不 import Android）；数据库是唯一真相；跨模型的转换只允许出现在 `KiteGateway` 与 `ContentMapping` 两处；契约以 spec 为准。

## 测试

```bash
gradle :app:testDebugUnitTest
```

21 个 JVM 单测，加上生成客户端的 2 个：同步（推/拉/冲突/删除/水位）、仓库（无改动不标脏、meta 合并、定时日期）、发现（网段筛选）、上传（multipart 字段名与 415）、服务端渲染（预览与字数）。没有 instrumented 测试，也没有截图对比——真机交互靠手测，能说“通过”的边界就到这里。

## 排错

<details>
<summary><b>闪退但拿不到堆栈</b></summary>

`CrashLog` 会把未捕获异常写到 `/sdcard/Android/data/com.kiteplus.kite.mobile/files/logs/crash.txt`：

```bash
adb pull /sdcard/Android/data/com.kiteplus.kite.mobile/files/logs/crash.txt
```

有 USB 调试时 `adb logcat -c && adb logcat *:E` 更直接。

</details>

<details>
<summary><b>连不上电脑上的 Kite</b></summary>

1. 跑 `kite run`，不是 `kite serve`——serve 只有静态站点，`/api/v1/*` 一律 404。
2. 地址别带 `/api/v1` 后缀，应用自己拼。
3. 手机与电脑要在同一网段；访客网络常开客户端隔离，会挡住。
4. Windows 防火墙首次弹窗时专用/公用网络都要放行；网络类别是「公用」时，只放行「专用」的规则不生效。
5. 电脑地址是 DHCP 租的，会变；变了重填，或在路由器上做地址绑定。

</details>

## 文档

[路线图](docs/ROADMAP.md)：里程碑与工程基线 · [连接方式](docs/CONNECTION.md)：现状、候选与取舍

## 许可

[Apache-2.0](LICENSE)，与 Kite 本体一致。
