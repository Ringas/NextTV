# Bolt Compose TV

**基于 Bolt-IPTV 核心思路，使用 Jetpack Compose for TV 重写的现代 Android TV IPTV 播放器。**

> 闪电流转，秒速畅播 · 类 APTV 体验

## 特性（v0.3）

- **双播放器预热换台**（继承自 Bolt-IPTV）
- **实时预览** + **首帧缓存**（聚焦静音播放，离开后缓存画面）
- **自适应网格**：按屏幕宽度 3–6 列
- **EPG**：XMLTV 解析，卡片显示当前节目，独立节目表页
- **扫码加源**：局域网 Web 服务 + 二维码
- Jetpack Compose for TV 现代 UI
- M3U 解析与示例源加载
- 遥控器上下键换台、信息条自动隐藏
- 深色高颜值主题

## 快速开始

1. 使用 Android Studio 打开本项目
2. 连接 Android TV / Google TV 设备或模拟器
3. Run
4. 进入设置页，点击「加载示例源」进行测试

详细开发说明请查看 → [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)

## 技术栈

- Kotlin
- Jetpack Compose for TV (`androidx.tv:tv-material`)
- Media3 (ExoPlayer)
- Coil
- Coroutines + StateFlow

## 声明

本项目是播放器工具，**不提供、不内置任何频道源**。请自行准备合法授权的播放列表。

界面与交互参考了 APTV 与 Bolt-IPTV，但代码为独立实现。

## License

建议沿用 Bolt-IPTV 的 GPL-3.0 或根据实际需要调整。
