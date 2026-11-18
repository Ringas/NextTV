# NextTV 开发说明

> 项目名：**NextTV**  
> 定位：Android TV 端、类 APTV 体验的现代化 IPTV 播放器  
> 上游思路：[Bolt-IPTV](https://github.com/AaronHvang/Bolt-IPTV)

**当前版本：v0.5.0-ui**  
**更新日期：2026-11**

---

## 1. 背景与目标

### 为什么做 NextTV？

- [Bolt-IPTV](https://github.com/AaronHvang/Bolt-IPTV) 在传统 View 体系下验证了「双播放器预热换台」等能力。
- APTV 在 Apple 生态以高颜值与实时预览著称。
- NextTV 希望在 **Android TV** 上同时获得：
  1. Bolt 级的**换台速度与预热策略**
  2. 接近 APTV 的**预览与视觉体验**
  3. Compose for TV 带来的**可维护 UI**

### 与 Bolt-IPTV 的关系

| 项目 | 说明 |
|------|------|
| 上游 | https://github.com/AaronHvang/Bolt-IPTV |
| 关系 | 思路与部分能力对照实现，**独立代码库**，非官方 fork |
| 差异 | UI 全面 Compose for TV；增加预览 debounce、首帧缓存、扫码加源、自适应网格等 |

---

## 2. 开发进度

### 已完成（v0.3）

| 模块 | 状态 | 说明 |
|------|------|------|
| 数据模型 | ✅ | Channel / PlaylistSource / EpgProgram / DualPlayerState |
| M3U 解析 | ✅ | 常见 `#EXTINF` 属性 |
| DualPlayerManager | ✅ | 全屏双播放器预热换台 |
| PreviewPlayerManager | ✅ | 列表静音实时预览 |
| 预览 Debounce | ✅ | 默认 250ms，可配置 |
| 首帧缓存 | ✅ | LRU Bitmap，弱网友好 |
| ChannelRepository | ✅ | 内存版 URL 加载 |
| EPG (XMLTV) | ✅ | 解析 + 卡片当前节目 + 节目表页 |
| 扫码加源 | ✅ | NanoHTTPD + 二维码 |
| 自适应网格 | ✅ | 按屏宽 3–6 列（LazyVerticalGrid） |
| Home / Player / Settings / EPG 页 | ✅ | 基础可操作流程 |
| 深色主题 | ✅ | TV 向 Material |
| 工程可编译配置 | ✅ | Kotlin 2.0 Compose 插件、图标、Wrapper |

### 未完成 / 已知限制

| 项 | 状态 | 说明 |
|----|------|------|
| Navigation Compose | ❌ | 目前为简单 Screen 枚举切换 |
| DataStore 持久化 | ✅ | 源列表、当前源、收藏、上次频道 |
| 数字键换台 | ✅ | 全屏数字键 + 频道号/序号 |
| EPG 时间轴网格 | ✅ | 频道×时间块 + 现在红线 + 日切换 |
| Xtream Codes | ❌ | 枚举预留，未实现 |
| 多源管理 UI | ✅ | 添加/切换/删除/刷新 |
| 正式签名与应用名全面重命名 | ⚠️ | applicationId 仍为 `com.bolttv.compose`，显示名可再统一为 NextTV |
| 单元测试 / UI 测试 | ❌ | 无 |
| 低端盒子专项优化 | ⚠️ | 已有单预览实例 + debounce，未做机型分级 |

---

## 3. 架构

```
app/src/main/java/com/bolttv/compose/
├── core/                          # 尽量不依赖 Compose
│   ├── model/Models.kt
│   ├── data/
│   │   ├── M3uParser.kt
│   │   ├── ChannelRepository.kt
│   │   ├── EpgRepository.kt
│   │   └── SourceWebServer.kt     # 扫码加源
│   └── player/
│       ├── DualPlayerManager.kt   # 全屏播放
│       └── PreviewPlayerManager.kt# 列表预览 + 缓存 + debounce
└── ui/
    ├── theme/Theme.kt
    ├── components/
    │   ├── ChannelCard.kt
    │   └── QrCodeImage.kt
    ├── screens/
    │   ├── HomeScreen.kt
    │   ├── PlayerScreen.kt
    │   ├── SettingsScreen.kt
    │   └── EpgScreen.kt
    └── MainActivity.kt
```

### 关键设计

1. **播放逻辑与 UI 分离**  
   Dual / Preview 通过 `StateFlow` 暴露状态，界面只绑定 Surface。

2. **双播放器换台（Bolt 思路）**  
   备用静音预加载 → 换台时 swap，减少黑屏。

3. **列表预览**  
   - 全局仅一个预览播放器  
   - Debounce 后再真正 `prepare`  
   - 就绪后截帧入 LRU；未就绪时优先显示缓存帧  

4. **网格**  
   `LazyVerticalGrid` + 按 `LocalConfiguration` 计算列数。

---

## 4. 开发计划（Roadmap）

### v0.4 — 体验打磨（近期待办）

- [ ] 应用显示名 / 包名策略明确为 NextTV（若需上架再定 applicationId）
- [ ] Navigation Compose 正式导航与返回栈
- [ ] DataStore 持久化播放源与收藏
- [ ] 全屏数字键换台、信息条手势优化
- [ ] 预览失败占位与重试

### v0.5 — EPG 与源管理

- [ ] EPG 时间轴（频道 × 时间网格）
- [ ] 多播放源列表、切换与刷新
- [ ] 源与 EPG 地址配对配置
- [ ] 收藏分组

### v0.6 — 播放增强

- [ ] 音轨 / 画面比例 / 播放信息面板
- [ ] 可选外挂解码路线（调研 mpv 等，参考社区方案）
- [ ] 错误码与常见流格式兼容说明

### 更长期

- [ ] Xtream Codes 登录与分类
- [ ] 多语言
- [ ] 机型分级（低端关闭实时预览等）
- [ ] 基础自动化测试

---

## 5. 本地开发

见根目录 [BUILD.md](../BUILD.md)。

常用命令：

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

预览 debounce 可调：

```kotlin
previewManager.debounceMs = 300L
```

---

## 6. 贡献与许可

- 欢迎 Issue / PR。
- 请合法使用播放源；不要在仓库中提交任何付费或未授权列表。
- 许可证建议与 [Bolt-IPTV](https://github.com/AaronHvang/Bolt-IPTV) 保持 **GPL-3.0** 兼容；二次分发请保留对 Bolt-IPTV 的致谢。

---

## 7. 版本历史（摘要）

| 版本 | 内容 |
|------|------|
| 0.1 | 骨架：双播放器、M3U、Home/Player |
| 0.2 | 实时预览、Tv/Lazy 网格 |
| 0.3 | 自适应列数、EPG、扫码加源、首帧缓存、Debounce；工程可编译配置；品牌定为 **NextTV** |
| 0.4 | DataStore 持久化、多源管理、播放自动重试、数字键换台、启动恢复上次源/频道 |
| 0.5-ui | APTV 向三栏首页（列表+大预览）、全屏左侧频道浮层；交互参照 APTV，不足处对齐 Bolt |
| 0.5-epg | APTV 风格 EPG 时间轴：频道×时间块、当前时间线、日期切换 |
