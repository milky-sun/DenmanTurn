# DenmanTurn

这是一个极小的 Android 屏幕强制旋转工具。

## 背景

有时候受手机壳开孔位置等影响，我们希望倒转Android屏幕，但是，多数大厂却在系统底层物理屏蔽了 180° 倒置功能。

## 需求

1. 核心功能，要能倒转屏幕。
2. 占用内存尽可能小，避免长时间使用时被系统结束进程。
    ➡️ 放弃 Android Studio，Kotlin，Gradle，完全使用Java来减少应用体积。
    控制在 Android Zygote 进程 fork 的最低物理限制（~4MB-8MB）。

## 设计点

1. 在屏幕最顶层（Z-axis supremacy）显示一个 0x0 像素 的窗口，通过 screenOrientation 属性强制接管系统底层的屏幕渲染方向。
2. 无 Activity 驻留: BootstrapActivity 仅用于申请悬浮窗权限，授权后即刻销毁。
3. 结合 FLAG_NOT_TOUCHABLE 和 FLAG_NOT_FOCUSABLE 避免干扰下方显示的的其他应用的使用。
4. 使用通知栏提供三个操作：0° (恢复)、180° (倒置) 以及 退出。
5. 纯 Shell 脚本直接调用 Android SDK 的底层命令行工具构筑。
6. 当屏幕方向锁定后，如果没有新的点击事件，进程实际上处于极度休眠的挂起状态（Idle），不会占用 CPU 时间片，对电池的消耗在统计学上可以忽略不计。

## 编译（假设使用Linux）

0. 前置要求
    * 已安装 Android SDK Command-line Tools (包含 build-tools 和 platforms)。
    * 使用 Android 11+ (API 30+) 的 SDK (可以在脚本内修改)。
1. 克隆仓库
git clone [https://github.com/YourName/DenmanTurn.git](https://github.com/YourName/DenmanTurn.git)
cd DenmanTurn

2. 修改 build.sh 中的 SDK_DIR 为你本地的 Android SDK 路径

3. 赋予执行权限并一键编译
chmod +x build.sh
./build.sh

4. 直接通过 ADB 安装生成的超轻量 APK
adb install -r dist/DenmanTurn.apk
