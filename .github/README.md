<div align="center">

<img title="Navic" alt="Navic" height="140" src="https://raw.githubusercontent.com/NavicApp/Branding/refs/heads/main/assets/wordmark.png">

# Navic 状态栏歌词版

[下载最新版](https://github.com/Agonie0v0/Navic/releases/latest) · [原项目](https://github.com/ssalggnikool/Navic)

</div>

这是 [Navic](https://github.com/ssalggnikool/Navic) 的 Android 衍生版本，在尽量保持上游代码不变的基础上增加了状态栏歌词支持，并补全了简体中文界面文案。

> 本项目不是 Navic 官方版本。状态栏歌词依赖系统 ROM 支持魅族 / Flyme 状态栏歌词协议，普通 ROM 可能不会显示。

## 下载与安装

前往 [Releases](https://github.com/Agonie0v0/Navic/releases/latest) 下载 `Navic-statusbar.apk`。

本项目使用独立证书签名，无法直接覆盖安装原作者发布的 APK。首次切换到本版本时需要先卸载原版；之后本仓库发布的新版本可以直接覆盖更新。卸载前请自行备份应用数据。

## 新增功能

- 播放歌曲时将当前歌词发送给支持该协议的系统状态栏。
- 暂停、切歌、拖动进度和改变播放速度时自动同步歌词。
- 保留 Navic 原有的媒体通知、封面、控制按钮和 Android Auto 行为。
- 支持 `-2000 ms` 至 `+2000 ms` 的歌词时间校准，步进为 `100 ms`。
- 补齐简体中文设置及相关对话框文案。

歌词校准入口：

`设置 → 正在播放 → 歌词 → 状态栏歌词时间`

- 负数：歌词延后显示。
- 正数：歌词提前显示。

## 自动更新

GitHub Actions 每 6 小时检查一次上游 Release。发现新版本后会自动：

1. 将上游版本合并到本仓库。
2. 构建 Android Release APK（不构建 iOS）。
3. 使用本仓库专用证书签名。
4. 发布为 `${上游版本号}-statusbar`，附件名为 `Navic-statusbar.apk`。同一上游版本已发布时会跳过，不会因为本仓库更新而重复构建。

手动强制重建已发布的上游版本时，会在发布标签后附加本仓库提交短哈希。

普通更新无需人工操作。发布前会检查简体中文资源覆盖情况；如果上游新增了尚未翻译的文案，发布会暂停，直到补齐翻译。如果上游修改了与状态栏歌词相同的代码并产生合并冲突，或大幅重构播放模块，也需要人工适配。

## 本地构建

需要 JDK 21 和 Android SDK。在 Windows PowerShell 中运行：

```powershell
.\gradlew.bat :androidApp:assembleDebug
```

Debug APK 位于 `androidApp/build/outputs/apk/debug/`。

## 上游与许可

- 上游项目：[ssalggnikool/Navic](https://github.com/ssalggnikool/Navic)
- 状态栏歌词版：[Agonie0v0/Navic](https://github.com/Agonie0v0/Navic)
- 许可证：[GNU GPL v3](../LICENSE)

感谢 Navic 原作者及所有贡献者。
