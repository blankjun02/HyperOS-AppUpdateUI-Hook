# Miuix 应用更新界面

适配 HyperOS 的 `com.android.wm.shell.packageupdate.PackageUpdateActivity`。
LSPosed Modern API 101，模块作用域为系统界面 `com.android.systemui`。

在原界面的 `onCreate` 完成后换掉进度圈，并按系统深浅色设置窗口背景。
圆环使用原生 Android View 绘制，蓝色圆弧沿灰色轨道旋转。

## 使用

安装 APK，在 LSPosed 管理器启用模块并勾选“系统界面”，然后重启系统界面或手机。
在酷安前台通过安装器更新酷安，或用原先会触发更新过渡页的流程复现。
使用 `adb logcat -s MiuixPackageUpdate` 查看 Hook 与样式应用状态。

## ⚠️：仅限安卓17使用，且仅在小米17ProMax 4.0.6.0官方包测试正常，其他版本谨慎使用
