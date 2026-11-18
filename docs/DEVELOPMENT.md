# Bolt Compose TV 开发说明

> 基于 **Bolt-IPTV** 核心思路，使用 **Jetpack Compose for TV** 重写的现代 Android TV IPTV 播放器。

**版本**：0.3.0-compose（自适应网格 · EPG · 扫码 · 首帧缓存）  
**目标**：类 APTV 的高颜值、流畅换台体验  
**日期**：2026-11

---

## 1. 项目背景与目标

### 为什么做这个项目？

- Bolt-IPTV 已经在传统 View 体系下验证了「双播放器预热换台」的有效性。
- APTV 在 Apple 生态以高颜值 + 实时预览著称。
- 我们希望在 Android TV 上同时获得：
  1. Bolt 的**极致换台速度**
  2. APTV 风格的**现代视觉与焦点体验**
  3. Compose 带来的**可维护性与声明式 UI**

### 本阶段完成内容（v0.2）

| 模块 | 状态 | 说明 |
|------|------|------|
| 数据模型 | ✅ | Channel / PlaylistSource / EpgProgram / DualPlayerState |
| M3U 解析 | ✅ | 支持常见 #EXTINF 属性 |
| DualPlayerManager | ✅ | 核心预热逻辑完整迁移 |
| **PreviewPlayerManager** | ✅ | **聚焦频道静音实时预览（类 APTV）** |
| ChannelRepository | ✅ | 内存版，支持 URL 加载 |
| Compose Theme | ✅ | 深色现代主题（APTV 风格） |
| ChannelCard | ✅ | 圆角 + 焦点缩放 + **实时预览 Surface** |
| HomeScreen | ✅ | 左侧分类 + **TvLazyVerticalGrid 四列网格** |
| PlayerScreen | ✅ | 全屏播放 + 上下键换台 + 信息条 |
| SettingsScreen | ✅ | 简易加载示例源 |
| 导航 | ⚠️ | 简单状态切换，含 EPG 页 |
| **EPG** | ✅ | **XMLTV 解析 + 卡片当前节目 + 节目表页** |
| **扫码加源** | ✅ | **NanoHTTPD + 二维码** |
| **预览首帧缓存** | ✅ | **LRU Bitmap 缓存** |
| **自适应列数** | ✅ | **按屏宽 3–6 列** |
| Room 持久化 | ❌ | 当前纯内存 |
| Xtream Codes | ❌ | 预留枚举 |

---

## 2. 架构说明

```
app/
├── core/                          # 纯逻辑，尽量不依赖 Compose
│   ├── model/Models.kt            # 数据类
│   ├── data/
│   │   ├── M3uParser.kt           # 解析器
│   │   └── ChannelRepository.kt   # 仓库
│   └── player/
│       ├── DualPlayerManager.kt    # ★ 全屏双播放器预热
│       └── PreviewPlayerManager.kt # ★ 列表实时预览
│
└── ui/                            # Compose for TV
    ├── theme/Theme.kt
    ├── components/ChannelCard.kt  # 支持预览 Surface
    ├── screens/
    │   ├── HomeScreen.kt          # TvLazyVerticalGrid
    │   ├── PlayerScreen.kt
    │   └── SettingsScreen.kt
    └── MainActivity.kt
```

**关键设计决策**：

1. **DualPlayerManager 独立于 UI**  
   通过 `StateFlow<DualPlayerState>` 暴露状态，Compose 只负责观察与绑定 Surface。这样未来换成 `PlayerSurface`（Media3 1.6+）或添加 mpv 引擎都很容易。

2. **预热策略（继承自 Bolt）**  
   - 主播放器：正常音量 + 完整缓冲策略  
   - 备用播放器：`volume = 0`，只 prepare 到有关键帧  
   - 换台时 `swapPlayers()`，几乎无黑屏

3. **实时预览策略（类 APTV）**  
   - 全列表共享**一个** `PreviewPlayerManager`（低端盒子友好）  
   - 卡片 `onFocusChanged` → `requestPreview(channel)`  
   - 聚焦卡片内嵌静音 `PlayerView`，未聚焦显示 Logo  
   - 进入全屏播放 / 离开首页时 `clearPreview()`，避免双路解码抢资源  
   - 使用较小 `LoadControl` 缓冲，优先出首帧
   - **Debounce 250ms**：焦点变化立即更新 UI（可显示缓存帧），真正起播延迟执行；快速滑动时取消未执行请求


4. **网格布局**  
   - 使用官方 `TvLazyVerticalGrid` + `TvGridCells.Fixed(4)`  
   - 比手动 `chunked + TvLazyRow` 更利于焦点记忆与滚动性能

5. **Compose for TV 组件**  
   使用 `androidx.tv:tv-material` 与 `tv-foundation`，获得原生焦点指示、缩放、边框效果。

---

## 3. 如何运行

### 环境要求

- Android Studio Ladybug (2024.2+) 或更新
- JDK 17
- Android SDK 35
- 真机/模拟器：Android TV 或 Google TV（API 24+）

### 步骤

1. 用 Android Studio 打开 `BoltComposeTV` 目录
2. Sync Gradle
3. 选择 Android TV 设备 / 模拟器
4. Run

> 当前 Settings 页提供「加载示例源」按钮，会拉取 iptv-org 的中国列表（仅供测试，请替换为合法授权源）。

### 命令行构建（可选）

```bash
cd BoltComposeTV
./gradlew :app:assembleDebug
# APK 输出在 app/build/outputs/apk/debug/
```

---

## 4. 核心代码导读

### DualPlayerManager 换台流程

```kotlin
fun play(channel: Channel) {
    if (备用已预加载该频道) {
        swapPlayers()          // 热切换
    } else {
        primary.setMediaItem(...)
        primary.prepare()
        primary.play()         // 冷启动
    }
}
```

在 `PlayerScreen` 中：
- 上下键调用 `dualPlayer.play(相邻频道)`
- `LaunchedEffect` 自动对「下一个频道」调用 `preload()`

### 播放器绑定（当前方案）

使用 `AndroidView` + `PlayerView`，兼容性最好：

```kotlin
AndroidView(
    factory = { PlayerView(it).apply { useController = false } },
    update = { it.player = dualPlayer.activePlayer }
)
```

后续可升级到 Media3 的 `PlayerSurface`。

---

## 5. 后续开发计划（Roadmap）

### 短期（v0.2）—— 已完成

- [x] 实现频道实时预览（聚焦静音副路）
- [x] 使用 `TvLazyVerticalGrid` 真正网格布局

### 下一步（v0.3）

- [ ] 接入 Navigation Compose
- [ ] DataStore 持久化播放源与收藏
- [ ] 完善遥控器数字键换台
- [ ] EPG 时间轴（复用/移植 Bolt 的 EpgRepository）
- [ ] 扫码添加源（局域网 WebServer）
- [ ] 多播放源管理与切换
- [ ] 播放控制面板（音轨、画面比例、信息）
- [ ] 预览首帧缓存（弱网占位优化）

### 长期

- [ ] 可选 mpv / libmpv 引擎（参考 OwnTV）提升兼容性
- [ ] 多语言
- [ ] 性能专项优化（低端盒子内存与带宽控制）
- [ ] 主题切换

---

## 6. 与原 Bolt-IPTV 的对应关系

| Bolt-IPTV | 本项目 | 说明 |
|-----------|--------|------|
| PlayerActivity + 双 ExoPlayer | DualPlayerManager + PlayerScreen | 逻辑迁移，UI 重写 |
| ChannelListView / Adapter | HomeScreen + ChannelCard | Compose 声明式 |
| M3uParser | M3uParser（几乎原样） | 复用 |
| PreviewCapturer | 待实现 | 下一步重点 |
| SourceWebServer（扫码） | 待迁移 | |
| 传统 View 圆角/动画 | tv-material Card + scale | 系统级焦点效果 |

---

## 7. 注意事项

1. **合法使用**  
   本项目仅为播放器工具，不提供任何频道源。请使用合法授权的 M3U / Xtream。

2. **清晰文本流量**  
   Manifest 中临时开启了 `usesCleartextTraffic`，方便测试 HTTP 源。生产环境请尽量使用 HTTPS 或按需配置网络安全策略。

3. **焦点调试**  
   Compose for TV 的焦点问题建议使用 Layout Inspector + 遥控器真机调试。

4. **依赖版本**  
   当前锁定 Media3 1.5.x 以获得较广兼容。若目标设备 API 较高，可升级到 1.8+ 并改用 `PlayerSurface`。

---

## 8. 贡献与反馈

当前为个人开发骨架版本。欢迎基于此继续完善。

原始灵感来自：
- [Bolt-IPTV](https://github.com/AaronHvang/Bolt-IPTV)（双播放器预热与整体方向）
- APTV（视觉与实时预览体验）
- OwnTV / JetStream（Compose for TV 最佳实践）

---

**下一步建议**：优先把「频道实时预览」和「真正的网格 + 焦点记忆」做扎实，这两点最能体现「类 APTV」的感觉。
