# Next TV

[![Build signed APK](https://github.com/Ringas/NextTV/actions/workflows/signed-apk.yml/badge.svg)](https://github.com/Ringas/NextTV/actions/workflows/signed-apk.yml)
[![License: GPL-3.0-only](https://img.shields.io/badge/license-GPL--3.0--only-blue.svg)](LICENSE)
[![minSdk 23](https://img.shields.io/badge/minSdk-23-brightgreen.svg)](#系统要求)
[![Kotlin 2.1](https://img.shields.io/badge/Kotlin-2.1-7F52FF.svg)](https://kotlinlang.org)

为 **Android TV 与电视盒子** 设计的 IPTV 播放器。添加自己的 M3U 订阅，就能用遥控器选台、按分类浏览、收藏常看频道、查看节目单，并在支持的情况下回看已播节目。

Next TV **不内置任何频道、订阅或直播源**，也不提供内容。请准备你有权使用的播放源，并遵守当地法律与服务条款。

- 一个 APK，没有账号、没有广告、没有统计 SDK
- 面向电视设计：焦点、长按、数字键、侧栏、快捷菜单
- 三条容错线：线路回退、解码降级、断网等待
- release 包 **2.6 MB**，冷启动有 Baseline Profile

---

## 目录

- [特性](#特性)
- [系统要求](#系统要求)
- [下载与安装](#下载与安装)
- [快速开始](#快速开始)
- [遥控器速查](#遥控器速查)
- [设置说明](#设置说明)
- [常见问题](#常见问题)
- [技术架构](#技术架构)
- [自行构建](#自行构建)
- [版本与发布](#版本与发布)
- [已知限制](#已知限制)
- [参与贡献](#参与贡献)
- [隐私](#隐私)
- [许可](#许可)

---

## 特性

### 订阅与频道

- **M3U / M3U8 / TXT 订阅**：支持 `#EXTINF` 的 `tvg-id`、`tvg-name`、`tvg-logo`、`group-title`、`catchup`、`catchup-source`、`catchup-days`，支持 `#EXTVLCOPT` 设置 User-Agent / Referer，也兼容 `频道名,地址` 的 TXT 列表。
- **多订阅源管理**：每个源可单独启用/停用、编辑、删除；可配置独立的 UA、Referer 与自定义请求头；拉取失败时**保留上一次缓存**，列表不会突然清空。
- **频道合并（可选）**：把不同订阅中的同一个频道合成一条，播放时自动使用多条线路，并汇总各自的节目单。优先按频道标识（`tvg-id`）合并，缺少标识时只匹配无歧义的频道名，不会把 4K、`+`、地区版本误合并。
- **收藏与分类**：分类浏览、收藏频道、全局频道编号；长按频道卡片可添加或取消收藏。
- **扫码输入**：电视上打开「添加订阅」，用手机扫二维码，在同一 Wi-Fi 下粘贴订阅地址回传，免去用遥控器敲长链接。

### 节目单与回看

- **XMLTV 节目单**：自动使用订阅内置的 `url-tvg` / `x-tvg-url`，也可以单独填写自定义节目单地址；支持按 `tvg-id` 与频道显示名（含常见分辨率后缀归一化）匹配。
- **多源节目单汇总**：频道合并后，来自不同订阅的节目单会按时间轴合并，填充彼此的空档并去重；冲突时段以自定义节目单/更高优先级源为准。
- **回看**：支持 `catchup="append"` 型回看地址模板（`${(b)yyyyMMddHHmmss}`、`${(e)yyyyMMddHHmmss}`、`{utc}`、`{utcend}`、`{duration}`），按北京时间生成；回看时可用左右键跳播、快捷菜单返回直播。

### 播放与容错

- **HTTP/HTTPS 直播播放**：基于 AndroidX Media3（ExoPlayer），支持 HLS 与常见 TS/MP4 流。
- **线路自动回退**：同一频道有多条线路时逐条尝试，单线路会重试一次；全部失败后，直播会沿频道列表继续找可播放的频道。
- **解码器降级**：硬件解码失败会自动尝试软件解码；音频输出异常会尝试 PCM 或软解；内置 **JLayer MP2 解码器**，解决部分盒子“有画面没声音”的问题。
- **断网等待而非报错退出**：网络断开时留在播放页等待恢复，联网后自动重连当前线路。
- **恢复预算**：每个频道每次播放有总时间预算，避免无限重试与反复黑屏；稳定播放 30 秒后才清空失败记录。
- **中文失败原因**：401/403/404/超时/解码失败/协议不支持等都会给出可读原因，详情页可查看本次会话最近一次失败的频道、线路与原因。

### 电视交互与外观

- 方向键/确认键浏览，长按确认或菜单键打开快捷菜单，播放时短按确认打开频道侧栏。
- 输入频道编号跳台；支持数字键、键盘、鼠标滚轮与触屏滑动。
- 网格 / 列表两种频道布局，深色 / 浅色主题，可减弱动效。
- 可选台标、频道静音预览、换台预加载与“保留上一帧画面”。

### 性能

- 频道表与节目单落盘缓存，冷启动直接读缓存进入首页；设置页可查看缓存占用并一键清除，频道源备份、EPG 与台标缓存均有独立配额和孤儿清理。
    - 播放信息采用浮动卡片：片名/节目与规格徽章同排，3 dp 细进度条，右侧上方为大号时间、下方为星期与日期，速率不带外框；快捷菜单改为与信息卡同一材质的描边文字按钮；右上角时间默认隐藏，可在「设置 → 播放 → 时间显示」选择“隐藏 / 始终显示 / 每小时显示 / 每半小时显示”。
- 台标自研两级缓存（16 MB 内存 LRU + 64 MB 磁盘，3 路并发、按目标尺寸采样解码），未引入第三方图片库。
- 换台预热池与相邻频道预加载（可关），配合解码器历史记忆选择更可能成功的线路与解码器。
- Baseline Profile + Startup Profile 提交入库，release 开启 R8 与资源压缩。

---

## 系统要求

| 项 | 要求 |
| --- | --- |
| 系统 | Android 6.0（API 23）及以上 |
| 设备 | Android TV、电视盒子，或支持横屏的 Android 设备 |
| 推荐 | Android 9+、2 GB 内存以上；低配盒子建议关闭「频道预览」与「换台预加载」 |
| 网络 | 能访问你的订阅源与节目单地址（不少源为明文 HTTP） |

> 应用没有内置播放源，首次打开是空列表，需要先添加订阅。

---

## 下载与安装

1. 打开 [GitHub Releases](https://github.com/Ringas/NextTV/releases)。
2. 下载最新带 `-signed.apk` 的安装包。push 自动产出 Alpha 包；正式包需要在 Actions 中手动触发。`main` 的文件名形如 `Next-TV-1.4.1-Alpha.N-signed.apk`，其他分支带前缀，例如 `Next-TV-dev-1.4.1-Alpha.N-signed.apk`。
3. 把 APK 拷到电视/盒子（U 盘、局域网推送、`adb install` 均可），用设备上的文件管理器安装。
4. 首次安装需在系统设置中允许“安装未知来源应用”。

命令行安装：

```bash
adb install -r Next-TV-dev-1.4.1-Alpha.123-signed.apk
```

CI 产出的 APK 使用项目发布证书签名，并在上传前校验证书 SHA-256 与版本号；如果证书不匹配，构建会直接失败。

---

## 快速开始

1. **添加订阅**
   - 首页空状态点「添加订阅」；或进入 **设置 → 订阅与节目单 → 管理订阅 → 添加订阅**；
   - 粘贴 M3U 地址并确认；也可以用手机扫码，在同一局域网内输入地址。
2. **选择节目单**
   订阅里带 `url-tvg` 时会自动使用；需要单独指定时进入 **设置 → 订阅与节目单 → 节目单来源**。
3. **开始播放**
   频道加载完成后，选中频道按确认键。播放时短按确认打开频道侧栏，长按确认或按菜单键打开快捷菜单。
4. **整理频道**
   按分类切换、长按卡片收藏；不需要的订阅可以停用而不是删除，停用后仍保留缓存与收藏。
5. **更新**
   设置页「更新订阅」会同时更新所有启用的订阅与节目单；更新失败会保留原有数据。

---

## 遥控器速查

| 操作 | 效果 |
| --- | --- |
| 首页方向键 / 确认键 | 浏览频道分类与卡片，确认即播放 |
| 首页长按频道卡片 | 打开收藏操作（添加收藏 / 取消收藏） |
| 直播时上、下或频道键 | 切换上一个 / 下一个频道 |
| 直播时输入频道编号 | 约 1.6 秒后跳到对应全局编号的频道 |
| 播放时短按确认 | 打开频道侧栏，可继续浏览频道与节目单 |
| 播放时长按确认，或按菜单键 | 打开快捷菜单：线路、音轨、画面适配、收藏、解码、详情、主页（回看时另有播放暂停 / 返回直播） |
| 播放时按左 / 右 | 直播：切换当前频道的线路；回看：向前 / 向后跳播 |
| 播放时按返回 | 先关闭快捷菜单、侧栏、弹窗；没有浮层时提示“再按一次退出播放”，第二次返回回到频道页 |
| 鼠标 / 触屏 | 支持点击、长按、滚轮与滑动；回看时拖拽可跳播 |

---

## 设置说明

设置分为五类：**订阅与节目单、播放、频道与外观、高级、关于**。

| 分类 | 设置项 | 说明 |
| --- | --- | --- |
| 订阅与节目单 | 管理订阅 | 添加、编辑、启停、删除订阅，查看每个源的频道数与最近错误 |
| 订阅与节目单 | 节目单来源 | 默认“订阅自带”；填写自定义 XMLTV 地址后优先使用 |
| 订阅与节目单 | 更新订阅 | 立即更新全部启用的订阅与节目单，失败保留缓存 |
| 订阅与节目单 | 自动更新 | 分别控制“启动时更新订阅”“自动更新节目单” |
| 播放 | 开始页面 | 「主页」进入频道页；「直播」直接播放上次观看的频道 |
| 播放 | 换台预加载 | 关闭 / 下一台 / 上一台 / 上下台；会增加网络与内存占用 |
| 播放 | 换台保留画面 | 切换频道或线路时保留上一帧，直到新画面渲染 |
| 频道与外观 | 频道合并 | 跨订阅合并同一频道并汇总节目单（默认关闭） |
| 频道与外观 | 频道布局 | 网格 / 列表 |
| 频道与外观 | 频道预览 | 首页停稳后静音预览可见频道，操作时暂停；会增加流量与解码开销 |
| 频道与外观 | 界面主题 | 深色 / 浅色，作用于首页、设置与播放浮层 |
| 频道与外观 | 列表台标 | 播放页侧栏是否显示台标，隐藏后侧栏自动收窄 |
| 频道与外观 | 动态效果 | 标准 / 减弱；减弱会关闭回弹、焦点渐变与浮层过渡 |
| 高级 | 请求参数 | 为订阅、节目单与播放统一设置 User-Agent / Referer / 自定义请求头 |
| 关于 | 应用版本 / 设备信息 | 版本号、设备名称、Android 版本与 ABI，反馈问题时请一并提供 |

---

## 常见问题

| 现象 | 排查方向 |
| --- | --- |
| 添加订阅后一个频道都没有 | 地址是否可直接下载；是否被源站要求特定 UA/Referer（到 **高级 → 请求参数** 设置）；应用会保留旧列表，可查看管理订阅页的最近错误 |
| 所有频道都播放失败 | 用浏览器/播放器验证源是否仍可用；确认协议是 `http(s)`（暂不支持 `rtsp`、`rtp`、`udp`、`rtmp`）；查看播放详情里的失败原因 |
| 某个频道反复失败 | 在快捷菜单打开「线路」换一条线路，或打开「详情」查看是 HTTP 状态还是解码问题 |
| 有画面没有声音 | 打开快捷菜单 →「解码」，尝试切到软件解码或切换音轨；MP2 音轨已内置软解兜底 |
| 换台慢、盒子卡顿 | 关闭「换台预加载」「频道预览」「换台保留画面」；低内存设备上收益最明显 |
| 有频道但没有节目单 | 检查频道 `tvg-id` 与 XMLTV `channel id` 是否一致；没有标识时按频道名匹配，名称差异过大可能匹配不到 |
| 有节目单但没有回看 | 回看需要源提供 `catchup="append"` 且 `catchup-source` 以 `?` 开头；`default` / `shift` 型暂不支持 |
| 数字键跳台没反应 | 首页需要连续输入后停顿约 1.6 秒；播放页也可直接用数字键；确认频道号在全局编号内 |
| 更新后频道变少 | 源可能下架或解析失败；更新失败不会清空缓存，可在管理订阅页查看错误 |
| 扫码页面打不开 | 手机与电视需在同一局域网；二维码 5 分钟内有效且只能提交一次；企业网络可能拦截设备互访 |

反馈问题时请提供：**设备型号、Android 版本、应用版本、最近的失败原因**（播放页「详情」可见）。分享截图或日志前，请遮住订阅地址中的账号、密码、Token。

应用会把最近一次崩溃写入应用私有目录的 `last-crash.txt`，可通过 `adb` 导出：

```bash
adb shell run-as com.nexttv cat files/last-crash.txt
```

---

## 技术架构

### 技术栈

| 层 | 选型 |
| --- | --- |
| 语言 / 构建 | Kotlin 2.1.21、Gradle 8.11.1、AGP 8.7.3、JDK 17 |
| UI | Jetpack Compose、Compose for TV（`androidx.tv:tv-material`） |
| 播放 | AndroidX Media3 1.9.4（ExoPlayer + HLS + Compose UI） |
| 音频兜底 | JLayer（MP2/MP1 软件解码，自定义 Media3 Renderer） |
| 二维码 | ZXing core（本地渲染，不联网） |
| 异步 | Kotlin Coroutines |
| 存储 | SharedPreferences + `AtomicFile` JSON 缓存 |

### 模块地图

```
app/src/main/java/com/nexttv
├── core/
│   ├── data/      订阅、M3U/XMLTV 解析、频道仓库、节目单、缓存、偏好、回看
│   ├── tv/        首页、播放器、设置、焦点与输入、预加载、播放恢复
│   ├── ui/        台标加载、二维码、时间标签
│   └── io/        导入预算与上限策略（ImportBudget / ImportPolicy）
├── Mp2AudioRenderer.kt       JLayer MP2 解码 Renderer
├── StreamResolver.kt         流地址探测与 MIME 推断
├── SubscriptionInputServer.kt 局域网扫码表单（临时 HTTP 服务）
└── NextTvRenderersFactory.kt 渲染器装配（含 MP2 兜底）
```

### 数据流

```
订阅 URL / 本地文件
   └─ SubscriptionTransport       // UA/Referer/自定义头，8 MB 上限，gzip 解码
        └─ M3uParser              // 流式解析，2 万频道上限，稳定频道 ID
             └─ mergeChannelRoutes // 同源按 tvg-id 合并；可选跨源合并
                  └─ ChannelRepository
                       ├─ 收藏 / 续播 / 编号迁移
                       ├─ AtomicFile 频道缓存（失败保留旧数据）
                       └─ 频道列表 + 分类 + 全局编号
```

```
XMLTV 地址（自定义或订阅内置）
   └─ XmltvFeedReader             // Pull 解析、±31 天窗口、别名归一化
        └─ EpgRepository         // 多源缓存、按源优先级解析、跨线路节目单合并
             └─ now / next、当日节目、回看条目
```

### 播放状态机（简化）

```
tune(channel)
  └─ preferredRoute：预热命中 → 上次成功线路 → 解码器历史 → 第 1 条
       └─ attempt：
            ├─ 复用预热池 MediaSource 或新建
            ├─ 首帧渲染 → 30 s 稳定期 → 清空失败记录
            ├─ 视频解码失败 → 所有线路失败后尝试软解
            ├─ 音频失败 → PCM / MP2 软解 / 音频软解
            ├─ 线路失败 → RouteRecovery 轮换（单线路重试一次）
            ├─ 全部耗尽 → 沿频道列表找下一台
            └─ 断网 → 等待网络恢复后重连
```

所有重试都受 `RecoveryBudget` 约束（默认每频道 25 秒），不会无限循环。

### 几个关键设计

| 设计 | 作用 |
| --- | --- |
| **稳定频道 ID** | `SHA-256(sourceId + tvg-id 或规范化名称)`；订阅改名、换线路时收藏与“上次频道”不丢 |
| **导入预算** | 下载、解压、解析、落盘共享一个 deadline；watchdog 关闭阻塞 IO；解析前做节点数/字符串长度上限，防损坏或恶意订阅 |
| **保守合并** | 默认只在同一订阅内合并；跨源合并要求标识一致，或名称无歧义，且冲突标识会阻止名称回退 |
| **失败保留缓存** | 订阅与节目单均先写临时文件、再原子替换；刷新失败时旧列表与旧节目单继续可用 |
| **解码器历史** | 只保存成功解码器名与失败解码器哈希，不保存原始 URL；7 天 / 10 分钟 TTL |
| **局域网输入** | 5 分钟生命周期、24 字节随机 token、单次提交、校验 Host/Origin/Content-Type、CSP + `no-store`，且不提供任何已有订阅数据 |

### 测试

```bash
bash ./gradlew :app:testReleaseUnitTest
```

当前 **196 个单元测试**（45 个测试类），覆盖节目窗口语义、跨源合并、回看模板、恢复预算、预加载索引、焦点滚动、数字键归一等纯逻辑。

`app/src/androidTest` 另有一套自研仪器化巡检（约 120 个文件、4 个 `Instrumentation` 入口），用于真机上的播放、冷启动与兼容性检查，需要设备或模拟器手动执行：

```bash
adb shell am instrument -w com.nexttv.test/com.nexttv.AuditFixInstrumentation
```

---

## 自行构建

### 前置条件

- JDK 17
- Android SDK Platform 35、Build-Tools 35.0.0
- 通过 `ANDROID_HOME` 或 `local.properties` 指定 SDK

```bash
git clone https://github.com/Ringas/NextTV.git
cd NextTV
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk

bash ./gradlew :app:assembleDebug
bash ./gradlew :app:testReleaseUnitTest
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。

### 签名与正式包

复制示例并填入本地密钥（PKCS12）：

```bash
cp signing.properties.example .signing/signing.properties
```

```properties
storeFile=.signing/nexttv-release.p12
storePassword=******
keyAlias=nexttv
keyPassword=******
```

```bash
bash ./gradlew :app:packageNextTvRelease
```

该任务会先校验 `releaseNumber` 恰好比 `lastPublishedReleaseNumber` 大 1，再执行 `assembleRelease`。密钥与密码只应保存在本地，不要提交；没有 `.signing/signing.properties` 时，`assembleRelease` 仍可构建，但产物是**未签名** APK。

### 版本号规则

版本信息集中在 [`version.properties`](version.properties)：

| 构建 | versionName | versionCode |
| --- | --- | --- |
| 正式版（main） | `1.4.1` | `releaseNumber × 100000 + 99999` |
| 正式版（dev） | `dev-1.4.1` | `releaseNumber × 100000 + 99999` |
| Alpha（main） | `1.4.1-Alpha.<run>` | `releaseNumber × 100000 + run` |
| Alpha（dev） | `dev-1.4.1-Alpha.<run>` | `releaseNumber × 100000 + run` |

正式版和 Alpha 共用同一个 `releaseNumber`。正式版升版时同步修改 `versionName` 和 `releaseNumber`，Alpha 的名称与 versionCode 会自动进入新版本区间，无需单独维护 Alpha 的版本基数。同一版本内正式版占用 `+99999`，因此正式包可以覆盖该版本的 Alpha；升到下一个 `releaseNumber` 后，新 Alpha 又高于上一版正式包。Alpha 的 run 取值范围为 `1..99998`。

`nextTvChannel` 会把分支前缀同时写进 versionName（设置页会显示）和 APK 文件名：

```bash
bash ./gradlew :app:assembleRelease -PnextTvAlphaBuild=123 -PnextTvChannel=dev
```

### 持续集成

[`.github/workflows/signed-apk.yml`](.github/workflows/signed-apk.yml) 的行为：

- push 与 `main` / `dev` 的 Pull Request 都会运行 `test` / `lint`，并编译 instrumentation 测试 APK；
- 只有 `main` / `dev` 分支会进入签名构建；feature 分支与 PR 不接触签名 Secrets；
- 正式包只能通过 `workflow_dispatch` 手动触发，选择 `build_type=formal`，且只允许在 `main` / `dev` 上构建；
- 签名 job 绑定 `release-signing` Environment，可在仓库设置中为该环境配置 required reviewers；
- `test` 与 `lint` job 必须先通过，否则不会进入签名构建和发布；
- 正式 Release 发布后，工作流会自动创建 `chore/bump-<next_version>` 分支并提交版本推进 PR。

构建流程：

1. 用 JDK 17 + SDK 35 构建签名 APK；
2. 校验 APK 证书 SHA-256 与预期发布证书一致；
3. 校验 `versionName` 与分支前缀、Alpha 构建号一致，并校验对应的 `versionCode`；
4. 上传构建产物，并创建/更新 `build-<sha>-<run>` Release（Alpha 为预发布，正式包为普通 Release）。

需要在 `release-signing` Environment Secrets（或仓库 Secrets）中配置：

| Secret | 内容 |
| --- | --- |
| `NEXTTV_KEYSTORE_BASE64` | 发布密钥库文件的 Base64 |
| `NEXTTV_SIGNING_PROPERTIES_BASE64` | `signing.properties` 的 Base64 |

### 性能 Profile

`:baselineprofile` 模块用于生成 Baseline Profile，生成结果已提交在 `app/src/release/generated/baselineProfiles/`。默认构建不自动生成，需要时按 Baseline Profile 插件流程手动运行。

---

## 版本与发布

- 版本遵循 `主版本.次版本.修订号`，`releaseNumber` 单调递增。
- 每次 push 产出 Alpha 预发布包，用于真机验证；正式包从 Actions 手动触发 `build_type=formal`，本地等价命令是 `packageNextTvRelease`。
- 非 `main` 分支的包名与 versionName 会带分支前缀，例如 `dev-1.4.1-Alpha.123`。
- 正式发布后，工作流运行 `tools/bump_version.py`，自动把 `lastPublishedReleaseNumber` 更新为本次 `releaseNumber`、修订号 +1，并提交 `chore/bump-<next_version>` PR；自动化失败时仍可按同样规则手动更新。
- versionCode 以 `releaseNumber` 为基数单调增长；Alpha 与正式包共用该基数，正式版升版后 Alpha 自动同步。

---

## 已知限制

- 播放引擎只支持 `http` / `https`，不支持 `rtsp`、`rtp`、`udp`、`rtmp`。
- 回看支持 `append` / `default` / `shift`，以及 `{utc}`、`{utcend}`、`{lutc}`、`{duration}`、`{start}`、`{end}`、日期占位符与 `${(b)...}` / `${(e)...}` 模板；仅仍受播放源服务端能力限制。
- 节目单最多 16 个来源；订阅刷新默认 3 路并发，单个慢源受独立预算限制，但极慢源仍可能拖长整批时间。
- 首页暂无搜索入口（数据层已具备按名称/分类/编号筛选能力）。
- 不支持录制、时移、多屏同播、家长锁。
- 界面文案仍以简体中文硬编码为主，尚未完成全量 `strings.xml` 抽取与英文翻译；关键台标已补充无障碍描述。
- 订阅地址、EPG 地址、Referer 等敏感字段使用 Android Keystore AES-GCM 加密后写入 SharedPreferences，旧版明文会在后台首次启动时迁移；崩溃日志已脱敏，但仍请勿分享含账号/Token 的截图。

---

## 参与贡献

欢迎 Issue 与 Pull Request。提交前请：

1. 跑通 `bash ./gradlew :app:assembleDebug :app:testReleaseUnitTest`；
2. 涉及播放/输入/焦点的改动，尽量在真机上验证并附设备型号；
3. 保持现有分层：解析与策略放 `core/data`、`core/io`，UI 与播放放 `core/tv`；
4. 新增纯逻辑请补单元测试；
5. 新增文件保留 `SPDX-License-Identifier: GPL-3.0-only` 头。

Issue 请包含：

- 设备型号、Android 版本、应用版本；
- 复现步骤与「最近失败原因」；
- 相关截图（**遮住订阅地址与凭据**）。

---

## 隐私

- 显式申请的权限只有 `INTERNET` 与 `ACCESS_NETWORK_STATE`（`WAKE_LOCK` 由 Media3 合并引入，另有系统自动生成的签名级动态接收器权限）。
- 无统计、无广告、无账号、无远程配置；应用不会向你之外的服务上报数据。
- 订阅、节目单、收藏、解码器历史都只存在本机应用私有目录；系统备份已关闭（`allowBackup=false`）。
- 扫码输入是临时局域网页面，5 分钟后失效，只接收你填写的一条地址，不读取也不展示已有订阅。
- 因 IPTV 源大量使用明文 HTTP，应用默认允许明文流量；可在「设置 → 高级」开启「仅 HTTPS」，此时订阅、节目单与播放地址只接受 HTTPS，并拒绝 HTTPS→HTTP 重定向。
- SharedPreferences 中的订阅地址、EPG 地址与 Referer 使用 Android Keystore AES-256-GCM 加密；崩溃日志会移除 URL userinfo / query 与常见 token 参数。

---

## 许可

Next TV 采用 [GNU General Public License v3.0 only](LICENSE)。

第三方组件遵循各自许可：AndroidX / Media3 / ZXing 为 Apache-2.0，JLayer 为 LGPL。分发 APK 时请同时遵守上述许可与 GPL 的源码提供义务。

---

## 声明

本项目不提供、不托管、不分发任何频道或订阅内容，仅为播放客户端。使用者需自行确保所用播放源合法，并对自己的使用行为负责。
