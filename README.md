# Next TV

[![Build signed APK](https://github.com/Ringas/NextTV/actions/workflows/signed-apk.yml/badge.svg)](https://github.com/Ringas/NextTV/actions/workflows/signed-apk.yml)
![Platform](https://img.shields.io/badge/平台-Android%206.0%2B-3DDC84?logo=android\&logoColor=white)
![Language](https://img.shields.io/badge/Kotlin-2.1-A97BFF?logo=kotlin\&logoColor=white)
![Player](https://img.shields.io/badge/播放器-Media3%201.9-4285F4)
![Version](https://img.shields.io/badge/版本-1.4.1-orange)
![License](https://img.shields.io/badge/许可-GPL--3.0--only-blue)

**换台快、出错自愈、对刁钻播放源格外宽容。** Next TV 是一款为 Android TV 与电视盒子深度打磨的 IPTV 播放器。导入你自己的 M3U 订阅，即可用遥控器选台、按分类浏览、收藏频道，并查看节目单与回看。

它把精力花在别人容易忽略的地方：**相邻频道预加载 + 保留上一画面**，换台几乎无黑屏；**线路 → 频道 → 视频软解 → 音频多级降级 + 断网自动重试**，播放自己从故障中恢复；**跨源合并同一频道并汇总节目单**，多个订阅互为备份；**统一导入预算 + 失败保留缓存**，糟糕的订阅源也不会清空你的频道列表。

> **Next TV 不内置任何频道、订阅或直播源，也不提供内容。** 请准备你有权使用的播放源并遵守当地法律。设备需运行 **Android 6.0（API 23）或更高版本**；遥控器为主，也支持键盘、鼠标与触屏。一个 APK，没有账号、没有广告、没有统计 SDK。

---

## 目录

- [为什么选择 Next TV](#why)
- [快速开始](#start)
- [遥控器操作](#remote)
- [功能与设置](#settings)
- [故障排查](#troubleshoot)
- [架构与实现](#architecture)
- [自行构建](#build)
- [已知限制](#limits)
- [隐私与安全](#privacy)
- [参与贡献](#contributing)
- [许可](#license)

---

<a id="why"></a>

## 为什么选择 Next TV

- **为遥控器而生。** 焦点导航、数字选台、长按快捷菜单、返回键层级都按电视交互设计；支持深浅色主题、列表/网格布局与减弱动效。
- **换台快。** 可预加载相邻频道（下一台 / 上一台 / 上下台），换台时保留上一画面直到新画面出现，减少黑屏等待。
- **失败能自愈。** 单条线路出错会按「线路 → 其他频道 → 视频软解 → 音频降级」的顺序尝试；断网后留在播放页等待网络恢复，联网自动重连。
- **节目单与回看。** 读取订阅自带的 XMLTV 地址，也可自定义；节目单与频道智能匹配，支持回看时间轴与拖动定位。
- **多订阅合一。** 可把不同订阅里的同一频道合并成多条线路互备，并汇总各源的节目单，去重后按时间线排列；回看时优先选提供该节目的线路。
- **兼容刁钻的播放源。** 可配置 User-Agent / Referer，识别并降级到软件解码，甚至内置 MP2/L1/L2 音频软解。
- **数据安全有边界。** 订阅与节目单导入有统一的时间与体积预算，更新失败保留已有数据；敏感字段加密存储，不会把频道列表清空。
- **手机扫码添加订阅。** 在电视上打开添加订阅对话框，扫描二维码即可用手机输入冗长的订阅地址，免去遥控器打字之苦。

---

<a id="start"></a>

## 快速开始

### 1. 安装

从 [GitHub Releases](https://github.com/Ringas/NextTV/releases) 下载带 `-signed.apk` 的安装包，传到电视或盒子，用设备文件管理器安装（也可 `adb install -r`）。

CI 会在每次 push 自动产出 Alpha 预发布包，文件名为 `Next-TV-1.4.1-Alpha.N-signed.apk`（dev 分支带 `dev-` 前缀）；正式包需在 Actions 中手动触发。上传前工作流会校验签名证书 SHA-256 与版本号，证书不匹配即构建失败。自行编译见[自行构建](#build)。

> 推荐 Android 9+、2 GB 内存以上。低配盒子建议在设置里关闭「频道预览」与「换台预加载」。

### 2. 添加订阅

三种入口，任选其一：

- **频道首页** → 选择 **添加订阅**；
- **设置 → 订阅与节目单 → 管理订阅 → 添加订阅**；
- **扫码**：在添加订阅对话框中显示二维码，用手机扫描后在浏览器里输入地址（仅限同一局域网，会话 5 分钟内有效且一次性）。

粘贴 **M3U / M3U8 / TXT** 订阅地址并确认。支持 `#EXTINF` 的 `tvg-id`、`tvg-name`、`tvg-logo`、`group-title`、`catchup` 系列属性，以及 `#EXTVLCOPT` 中的 `http-user-agent` / `http-referrer`，也兼容「频道名,地址」的 TXT 列表。频道加载完成后，选中频道按 **确认键** 开始播放。

### 3. 节目单与回看（可选）

若订阅提供节目单地址（`url-tvg` / `x-tvg-url` / `tvg-url`），应用会自动使用。也可在 **设置 → 订阅与节目单 → 节目单来源** 填写自己的 XMLTV 地址，自定义来源优先。开启「频道合并」后，同一频道在各订阅中的节目单会汇总、去重并按时间排列。

回看支持 `append` / `default` / `shift` 三类 catchup 模板，占位符按北京时间展开（如 `${(b)yyyyMMddHHmmss}`、`{utc}`、`{utcend}`、`{lutc}`、`{duration}`、`{start}`、`{end}` 等）。**回看是否可用同时取决于节目单与播放源是否支持。**

---

<a id="remote"></a>

## 遥控器操作

| 操作            | 效果                          |
| ------------- | --------------------------- |
| 首页方向键、确认键     | 浏览频道分类与卡片，确认即播放              |
| 首页长按频道卡片      | 添加 / 取消收藏                    |
| 直播时按上、下或频道键   | 切换上一个 / 下一个频道               |
| 直播时输入频道编号     | 约 1.6 秒后跳到对应全局编号的频道          |
| 播放时短按确认       | 打开频道侧栏，继续浏览频道与节目单           |
| 播放时长按确认，或按菜单键 | 打开快捷菜单：线路、音轨、画面、收藏、解码、详情、主页 |
| 播放时按左、右       | 直播时切换当前频道的线路；回看时向前或向后跳播     |
| 返回键           | 先关弹窗/侧栏；无浮层时再按一次退出播放、回到频道页  |
| 鼠标 / 触屏        | 支持点击、长按、滚轮与滑动；回看时拖拽跳播       |

播放页的 **详情** 会显示当前播放信息，以及本次会话中最近一次失败的频道、线路和中文原因（401/403/404、超时、解码失败、协议不支持等），便于定位问题。

---

<a id="settings"></a>

## 功能与设置

设置分为五类：**订阅与节目单**、**播放**、**频道与外观**、**高级**、**关于**。

### 订阅与节目单

- **管理订阅**：添加、编辑、启停或删除订阅，查看每个源的频道数与最近错误；「重新拉取」只更新订阅列表。
- **节目单来源**：默认「订阅自带」；填写自定义 XMLTV 地址后优先使用。
- **更新订阅**：重新下载所有已启用订阅及其节目单。**失败时保留缓存。**
- **自动更新**：可分别设置「启动时更新订阅」与「自动更新节目单」，均在后台进行，不阻塞进入页面。

### 播放

- **开始页面**：「主页」打开频道首页；「直播」直接播放上次观看的频道，找不到时播放第一个频道（从播放返回即退出应用）。
- **换台预加载**：关闭 / 下一台 / 上一台 / 上下台。「上下台」会同时预热两侧频道，增加网络与内存占用。
- **换台保留画面**：切换频道或线路时保留上一画面，直到新画面出现。
- **时间显示**：播放器右上角时间可选隐藏 / 始终显示 / 每小时显示 / 每半小时显示。

> 性能有限的盒子建议关闭预加载与频道预览，以获得更稳定的播放。

### 频道与外观

- **频道合并**：把不同订阅中的同一频道合成多条播放线路，并汇总节目单。优先匹配频道标识（`tvg-id`），缺少标识时匹配无歧义的频道名，不误并 4K / `+` / 地区版本；合并后旧频道收藏仍有效。默认关闭。
- **频道布局**：列表 / 网格；**频道预览**：停稳后静音预览可见频道（操作时暂停）。
- **界面主题**：深色 / 浅色；**列表台标**：控制播放时频道侧栏的台标，隐藏后侧栏自动收窄。
- **动态效果**：「减弱」会关闭胶囊回弹、焦点渐变与换台渐变，但保留滚轮、触控及其惯性。

### 高级 · 请求参数

- **请求参数**：仅当订阅服务要求时设置 **User-Agent** 与 **Referer**（默认使用内置 TV 端 UA），对订阅、节目单与播放统一生效。单个订阅和单条线路也可携带自定义请求头，优先级为：线路头 > 订阅头 > 全局 UA / Referer。
- **仅 HTTPS**：拒绝 `http://` 的订阅、节目单与播放地址，并拒绝 HTTPS → HTTP 重定向。默认关闭以兼容大量未加密的 IPTV 源。
- **清除缓存**：删除频道、节目单、播放源备份与台标缓存；不会删除订阅、收藏或设置。

### 关于

- **应用版本 / 设备信息**：版本号、设备名称、Android 版本与 ABI，反馈问题时请一并提供。

---

<a id="troubleshoot"></a>

## 故障排查

| 看到的情况               | 可以怎么做                                                                    |
| ------------------- | ------------------------------------------------------------------------ |
| 提示「网络已断开，正在等待网络恢复…」 | 检查电视或盒子的网络。重新联网后，留在播放页，应用会重试当前线路。                                        |
| 正在连接或自动切换频道         | 应用先尝试当前频道的各条线路；尝试用尽后，直播会沿频道列表继续寻找可播放的频道。                                 |
| 某个频道反复失败            | 在快捷菜单切「线路」或打开「详情」查看失败原因。若服务要求特定 UA / Referer，到 **设置 → 高级 → 请求参数** 填写。 |
| 有画面没有声音            | 快捷菜单 →「解码」切到软件解码，或切换音轨；MP2 音轨已内置软解兜底。                                  |
| 有频道，但看不到节目单或回看      | 检查 `tvg-id` 与 XMLTV `channel id` 是否一致；回看需源提供 catchup 模板且服务端支持对应节目。            |
| 换台慢、盒子卡顿           | 关闭「换台预加载」「频道预览」「换台保留画面」，低内存设备上收益最明显。                                    |
| 数字键跳台没反应           | 首页需连续输入后停顿约 1.6 秒；确认频道号在全局编号内。                                            |
| 扫码页面打不开            | 手机与电视需在同一局域网；二维码 5 分钟内有效且只能提交一次；企业网络可能拦截设备互访。                          |
| 订阅很多，更新失败           | 单个源失败会回退到上次缓存；可稍后重试「更新订阅」。                                             |

反馈问题时，请提供**设备型号、Android 版本、应用版本与「最近失败原因」**。分享截图或日志前，请遮住订阅地址中的账号、密码或令牌。最近一次崩溃会写入应用私有目录的 `last-crash.txt`（已脱敏），可用 `adb shell run-as com.nexttv cat files/last-crash.txt` 导出。

---

<a id="architecture"></a>

## 架构与实现

面向希望构建、排查或贡献代码的开发者。以下是对源码结构的客观说明。

### 技术栈

| 类别      | 选型                                                            |
| ------- | ------------------------------------------------------------- |
| 语言 / 构建 | Kotlin 2.1.21 · Gradle 8.11.1 · AGP 8.7.3 · JDK 17               |
| UI      | Jetpack Compose for TV（`tv-material` 1.1.0）                   |
| 播放      | AndroidX Media3 1.9.4（ExoPlayer / HLS / ui-compose）           |
| 音频      | JLayer 1.0.1.4（MP2 / L1 / L2 软件解码，自定义 Media3 Renderer）      |
| 其他      | Coroutines 1.10.2 · ZXing 3.5.3（二维码，本地渲染）· desugar_jdk_libs |
| 存储      | SharedPreferences + `AtomicFile` JSON 缓存 + Android Keystore 加密    |
| SDK     | minSdk 23 · compileSdk / targetSdk 35                         |

### 分层与数据流

```
com.nexttv
├── core/App.kt         应用单例：崩溃落盘、缓存预热、订阅与节目单更新编排
├── core/data/          数据层：M3U / XMLTV 解析、仓库、缓存、导入预算、跨源频道与节目单合并、回看、密钥存储
├── core/io/            统一导入预算（横切所有外部数据）
├── core/tv/            TV 界面与播放会话（PlaybackSession / PlayerScreen / HomeScreen 等）
├── core/ui/            通用 UI：Logo 两级缓存、二维码、时间标签
└── 根包                 Mp2AudioRenderer / StreamResolver / SubscriptionInputServer / NextTvRenderersFactory
```

数据层不依赖 UI 层，UI 单向消费数据层的可观察状态。订阅经 `SubscriptionTransport`（UA/Referer、8 MB 上限、gzip 解码）→ `M3uParser`（流式解析、2 万频道上限、稳定 ID）→ 同源/可选跨源合并 → `ChannelRepository`（收藏、编号迁移、原子缓存），节目单经 `XmltvFeedReader`（±31 天窗口、别名归一化）→ `EpgRepository`（多源缓存、优先级解析）汇入。

### 几处关键设计

1. **世代隔离**：每次调谐独占一个播放器、监听器与任务组，用递增的 `tuneSerial` 丢弃迟到回调，避免旧线路的超时或错误污染新会话。
2. **统一恢复预算**：线路轮换、跨频道尝试与解码降级共享一份时间额度（默认每频道 25 秒）；正常播放与用户主动暂停不消耗额度，稳定播放 30 秒后才清空失败记录。
3. **导入预算**：单个单调截止时间贯穿下载、解压与解析，超时时 watchdog 主动关闭阻塞中的网络读取；对体积、节点数、字段长度分层设限，抵御超大或畸形订阅源。
4. **稳定频道 ID**：由订阅来源与频道身份生成 SHA-256 摘要，订阅刷新、改名或顺序变动后，收藏与上次观看位置不会丢失。
5. **跨源合并与节目单汇总**：`ChannelProgrammes` 按频道标识或名称把多源节目单汇总到一条时间线，去重叠区间、合并同源节目描述并标注来源；回看线路优先选提供该节目的 EPG 源。
6. **解码器历史**：只持久化成功解码器名与失败解码器哈希（不保存原始 URL），失败项 10 分钟降权、成功项保留 7 天，用于下次选路。
7. **敏感信息加密**：订阅 / EPG 地址、Referer 等经 Android Keystore 的 AES-GCM 加密后写入 SharedPreferences，旧版明文在后台首次启动时迁移；崩溃日志亦做脱敏。
8. **局域网输入安全**：扫码表单生命周期 5 分钟、24 字节随机 token、单次提交，校验 Host / Origin / Content-Type 并配 CSP 与 `no-store`，不读取也不展示已有订阅。

### 测试

- **254 个 JVM 单元测试（57 个测试类；Debug 额外运行 25 个设计截图校验）**：覆盖 M3U / XMLTV 解析边界、订阅地址校验、导入预算、回看模板、跨源合并、多源节目单汇总、恢复预算、预加载索引、焦点滚动等纯逻辑。
- **122 个设备测试源文件（4 个 `Instrumentation` 入口）**：覆盖遥控器焦点、返回路径、布局一致性、播放恢复与冷启动性能；CI 只执行 `assembleDebugAndroidTest` 编译，**不会在 CI 中运行真机测试**，设备测试需本地连接电视执行。默认审计型 Runner 可用 Gradle 属性 `perfRunner` 切换。

```sh
bash ./gradlew :app:testReleaseUnitTest
adb shell am instrument -w com.nexttv.test/com.nexttv.AuditFixInstrumentation
```

> Baseline / Startup Profile 已生成入库（`:baselineprofile` 模块），release 开启 R8 与资源压缩；默认构建不重新生成 Profile。

---

<a id="build"></a>

## 自行构建

**环境**：JDK 17、Android SDK Platform 35 / Build-Tools 35.0.0，通过 `ANDROID_HOME` 或 `local.properties` 指定 SDK 路径。

```sh
git clone https://github.com/Ringas/NextTV.git && cd NextTV
bash ./gradlew :app:assembleDebug       # 调试包
bash ./gradlew :app:testReleaseUnitTest # 单元测试
```

**发布**：将 [`signing.properties.example`](signing.properties.example) 复制为 `.signing/signing.properties` 并填入本地 PKCS12 密钥信息（密钥库保存在本地，仓库已忽略）。版本号集中在 [`version.properties`](version.properties)，发布时运行：

```sh
bash ./gradlew :app:packageNextTvRelease
```

版本采用分段规则：每个 `releaseNumber` 独占 10 万个 versionCode 区间。正式版 = `releaseNumber × 100000 + 99999`，Alpha 版 = `releaseNumber × 100000 + run`（run 取 1..99998）。因此同一版本内正式包可覆盖 Alpha，升到下一个 `releaseNumber` 后新 Alpha 又高于上一版正式包。`nextTvChannel` 会把分支前缀同时写进 versionName 与 APK 文件名：

```sh
bash ./gradlew :app:assembleRelease -PnextTvAlphaBuild=123 -PnextTvChannel=dev
```

发布任务会校验 `releaseNumber` 恰好比 `lastPublishedReleaseNumber` 大 1，防止跳号或漏改；同一候选包重建沿用原编号。没有 `.signing/signing.properties` 时 `assembleRelease` 仍能构建，但产物为未签名 APK。

### 自动构建与发布

仓库内置 GitHub Actions 工作流（[`.github/workflows/signed-apk.yml`](.github/workflows/signed-apk.yml)）：

- push 与 `main` / `dev` 的 PR 都会跑 `test` / `lint` 并编译设备测试 APK；二者通过后才进入签名与发布；
- 只有 `main` / `dev` 分支接触签名 Secrets，feature 分支与 PR 不接触；签名 job 绑定 `release-signing` Environment（可配置 required reviewers）；
- push 产出 Alpha 预发布包，正式包只能通过 `workflow_dispatch` 选择 `build_type=formal` 手动触发；发布后工作流自动创建版本推进 PR；
- 构建后校验 APK 证书 SHA-256 与 versionName / versionCode 一致性，不匹配即失败。

Fork 仓库需在 `release-signing` Environment（或仓库 Secrets）配置 `NEXTTV_KEYSTORE_BASE64` 与 `NEXTTV_SIGNING_PROPERTIES_BASE64`，且证书指纹需与工作流声明一致。

---

<a id="limits"></a>

## 已知限制

- 播放引擎只支持 `http` / `https`，不支持 `rtsp`、`rtp`、`udp`、`rtmp`。
- 回看虽支持 `append` / `default` / `shift` 与多种占位符，仍受播放源服务端能力限制；`default` 仅在无自定义模板时用内置占位符。
- 节目单最多 16 个来源；订阅刷新默认 3 路并发，单个慢源有独立预算，但极慢源仍可能拖长整批。
- 首页暂无搜索入口（数据层已具备按名称 / 分类 / 编号筛选能力）；不支持录制、时移、多屏同播与家长锁。
- 界面文案以简体中文硬编码为主，尚未完成全量 `strings.xml` 抽取与英文翻译；关键台标已补无障碍描述。

---

<a id="privacy"></a>

## 隐私与安全

- 显式申请的权限只有 `INTERNET` 与 `ACCESS_NETWORK_STATE`（`WAKE_LOCK` 由 Media3 合并引入）；无统计、无广告、无账号、无远程配置；`allowBackup=false`。
- 订阅、节目单、收藏、解码器历史只存本机应用私有目录；订阅 / EPG 地址与 Referer 使用 Android Keystore AES-256-GCM 加密，崩溃日志会移除 URL userinfo、query 与常见 token 参数。
- 扫码输入是临时局域网页面，5 分钟失效、一次性提交，不读取也不展示已有订阅。
- 因 IPTV 源大量使用明文 HTTP，应用默认允许明文流量；可在「设置 → 高级」开启「仅 HTTPS」，此后订阅、节目单与播放地址只接受 HTTPS，并拒绝 HTTPS → HTTP 重定向。

---

<a id="contributing"></a>

## 参与贡献

欢迎 Issue 与 Pull Request。提交前请：

1. 跑通 `bash ./gradlew :app:assembleDebug :app:testReleaseUnitTest`；
2. 涉及播放 / 输入 / 焦点的改动尽量在真机验证并附设备型号；
3. 保持现有分层：解析与策略放 `core/data`、`core/io`，UI 与播放放 `core/tv`；新增纯逻辑请补单元测试；
4. 新增文件保留 `SPDX-License-Identifier: GPL-3.0-only` 头。

Issue 请包含设备型号、Android 版本、应用版本、复现步骤与「最近失败原因」，相关截图请遮住订阅地址与凭据。

---

<a id="license"></a>

## 许可

Next TV 采用 [GNU GPL v3 only](LICENSE)。第三方组件遵循各自许可：AndroidX / Media3 / ZXing 为 Apache-2.0，JLayer 为 LGPL；分发 APK 时请同时遵守上述许可与 GPL 的源码提供义务。

本项目不提供、不托管、不分发任何频道或订阅内容，仅为播放客户端；使用者需自行确保所用播放源合法，并对自己的使用行为负责。
