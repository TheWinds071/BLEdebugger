# BLEdebugger

一个基于 Jetpack Compose 和 Material 3 的 Android 蓝牙调试助手，面向 BLE/GATT 调试场景。

## 功能

- 设备页
  - 混合扫描 BLE 和经典蓝牙设备
  - 显示已配对设备、扫描结果、RSSI、设备类型
  - 点击设备后弹窗确认，再自动连接
- 调试页
  - 自动连接后发现服务
  - 自动匹配 Nordic UART Service 的 `RX/TX` 特征
  - 支持 GATT 连接、断开、服务发现、读取、写入、通知订阅
  - 写入支持 `Hex` 和 `UTF-8`
  - 自动按 MTU 分包发送，减少长包写入失败
- 日志页
  - 记录扫描、连接、服务发现、读写、通知等关键日志

## UI 特性

- Jetpack Compose 单 Activity 架构
- Material 3 主题
- Android 12+ 使用动态取色，基于系统壁纸莫奈色系
- 标准底栏，滚动时自动隐藏/显示

## 技术栈

- Kotlin
- Jetpack Compose
- Material 3
- Android Bluetooth / Bluetooth LE / GATT API

## 运行要求

- Android Studio 最新稳定版
- JDK 11
- Android 设备需支持蓝牙，BLE 调试建议使用支持 GATT 的设备
- `minSdk = 35`

## 权限

应用会申请以下权限：

- `BLUETOOTH_SCAN`
- `BLUETOOTH_CONNECT`
- `ACCESS_FINE_LOCATION`

部分 Android 设备即使已经有蓝牙权限，仍然要求系统定位服务开启，否则扫描可能为空。

## 构建

在项目根目录执行：

```bash
./gradlew :app:assembleDebug
```

APK 构建成功后可在默认输出目录中找到调试包。

## 调试流程

1. 打开应用，进入设备页
2. 授权蓝牙相关权限，并确认蓝牙/定位已开启
3. 点击“开始扫描”
4. 选择目标设备并确认连接
5. 进入调试页查看连接状态、服务和特征
6. 根据需要读取、写入、订阅通知

## Nordic UART Service 兼容

当前逻辑会优先匹配 Nordic UART Service：

- Service: `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`
- RX: `6E400002-B5A3-F393-E0A9-E50E24DCCA9E`
- TX: `6E400003-B5A3-F393-E0A9-E50E24DCCA9E`

如果对端设备实现的是 NUS，应用会优先选择对应的写入和通知特征。

## 项目结构

```text
app/src/main/java/com/winds/bledebugger/
├── MainActivity.kt
├── BluetoothDebuggerApp.kt
├── DeviceScreen.kt
├── DebugLogScreens.kt
├── UiComponents.kt
├── BleController.kt
└── ui/theme/
```

## 当前限制

- GATT 服务发现仍依赖不同厂商 ROM 的兼容性
- 目前没有做服务/特征筛选搜索
- 目前没有做历史会话持久化

## 后续可扩展方向

- MTU 手动协商
- 服务/特征别名显示
- 发送历史和快捷指令
- 日志导出
- 自动重连
