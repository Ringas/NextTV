# NextTV 编译与安装

## 环境要求

- Android Studio Ladybug (2024.2+) 或更新
- JDK 17
- Android SDK 35
- 建议内存 ≥ 8GB（Compose 编译较吃内存）
- 真机或模拟器：Android TV / Google TV（API 24+）

## 步骤

1. 克隆仓库：

```bash
git clone https://github.com/Ringas/NextTV.git
cd NextTV
```

2. 用 Android Studio 打开本目录，等待 Gradle Sync。

3. 若无 `local.properties`，指定 SDK：

```bash
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
```

4. 运行到 TV 设备，或：

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 首次使用

1. 侧边栏进入 **设置**
2. **加载示例源**，或扫码添加自有 M3U
3. 可选加载示例 EPG
4. 首页浏览与全屏播放

## 说明

- Debug 包可直接 sideload。
- 正式分发需自行配置签名。
- 不内置任何直播源；示例列表仅供开发测试。

更多架构与进度见 [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)。
