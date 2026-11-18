# 如何编译安装 Bolt Compose TV

## 环境要求

- Android Studio Ladybug (2024.2+) 或更新
- JDK 17
- Android SDK 35
- 建议机器内存 ≥ 8GB（Compose 编译较吃内存）

## 步骤

1. 用 Android Studio 打开目录 `BoltComposeTV`
2. 等待 Gradle Sync 完成  
   - 若提示缺少 Wrapper，选择 “Gradle wrapper” 自动生成即可
3. 连接 Android TV / Google TV 设备，或创建 TV 模拟器
4. 点击 Run，或菜单 **Build → Build Bundle(s) / APK(s) → Build APK(s)**
5. Debug APK 路径：  
   `app/build/outputs/apk/debug/app-debug.apk`

## 命令行（可选）

```bash
cd BoltComposeTV
# 配置 SDK 路径
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

./gradlew :app:assembleDebug
# 安装到已连接设备
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 首次使用

1. 打开应用 → 侧边栏「设置」
2. 点击「加载示例源」或用手机扫码添加 M3U
3. 可选：加载示例 EPG
4. 返回首页浏览频道，确认键全屏播放

## 说明

- 当前为 Debug 包，可直接 sideload 安装
- 正式分发需自行配置签名（signingConfigs）
- 本项目不内置任何直播源，请使用合法授权列表
