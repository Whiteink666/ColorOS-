# ColorOS 流体云常驻

[![Android](https://img.shields.io/badge/Android-8.0%2B-brightgreen)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blue)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

让任意自定义内容常驻显示在 **ColorOS 16** 的流体云胶囊中。

<p align="center">
  <img src="screenshots/preview.png" width="260" alt="预览"/>
</p>

---

## 功能特性

- 🧩 **大标题 + 小标题**：自定义胶囊左侧主标题和右侧副标题
- 📋 **快速模板**：一键切换音乐、计时器、快递、导航等场景
- 👁️ **实时预览**：编辑内容时即时预览胶囊效果
- 🔄 **持久常驻**：AlarmManager 心跳保活，通知丢失自动恢复
- 📡 **状态感知**：反射检测 ColorOS 是否真正启用了流体云显示
- 🚀 **开机自启**：支持开机自动恢复流体云显示
- 🎯 **可预测式返回**：Android 14+ 返回手势过渡动画
- 🔔 **无白圈**：不使用 `startForeground()`，避免锁屏白圈问题

## 运行截图
| 首页 | 自定义内容 | 关于页面 |
|------|------------|----------|
| ![](screenshots/main.png) | ![](screenshots/edit.png) | ![](screenshots/about.png) |

| 流体云胶囊 | 通知栏展开 |
|------------|------------|
| ![](screenshots/capsule.png) | ![](screenshots/notification.png) |

## 技术栈

| 项目 | 版本 |
|------|------|
| Kotlin | 2.0.21 |
| AGP | 8.9.3 |
| compileSdk | 36 |
| targetSdk | 34 |
| minSdk | 26 (Android 8.0) |
| Material | 1.12.0 |
| AndroidX Core | 1.17.0 |

## 实现原理

利用 Android 16 新增的 `setRequestPromotedOngoing(true)` API 标记通知，ColorOS 16 识别到该标记后自动将常驻通知提升到流体云胶囊。通知通过 `NotificationManager.notify()` 直接发送（**不使用** `startForeground()`，避免白圈问题），AlarmManager 10ms 循环 tick 实现通知丢失自动恢复。

> 更详细的技术分析见 App 内「关于」页面。

## 权限说明

| 权限 | 用途 |
|------|------|
| `POST_NOTIFICATIONS` | 发送通知（Android 13+ 运行时申请） |
| `POST_PROMOTED_NOTIFICATIONS` | 允许通知进入流体云 |
| `FOREGROUND_SERVICE` | 前台服务（手动启动时） |
| `SCHEDULE_EXACT_ALARM` | AlarmManager 精确闹钟保活 |
| `RECEIVE_BOOT_COMPLETED` | 开机自启 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 电池优化白名单 |

## 构建 & 安装

```bash
# 克隆项目
git clone https://github.com/Whiteink666/ColorOS-.git
cd ColorOS-

# 编译 Debug 版本
./gradlew assembleDebug

# 安装到设备
adb install app/build/outputs/apk/debug/app-debug.apk
```

> ⚠️ 需要使用 **Android Studio Koala (2024.1.1) 或更新版本**，JDK 17+。

## FAQ

### Q: 为什么流体云不显示？
**A:** 请逐一检查：
1. 确认手机是 **ColorOS 16**（基于 Android 16）
2. 确保通知权限已授予
3. 检查系统设置中「流体云」通知渠道是否被关闭
4. 尝试重启应用后重新点击「启动流体云」
5. 部分机型可能需要等待 3-5 秒后 ColorOS 才会识别

### Q: 通知栏出现白色圆圈闪烁？
**A:** 这是调用 `startForeground()` 的副作用。本 App 已避免此问题（直接使用 `NotificationManager.notify()`），如果仍然出现，请检查是否有其他应用同时占用了前台服务。

### Q: 支持非 ColorOS 手机吗？
**A:** `setRequestPromotedOngoing()` 是标准 Android API，但流体云胶囊显示是 ColorOS 的系统行为。在其他 ROM 上，通知会以普通常驻通知的形式显示在通知栏，但不会进入任何胶囊。

### Q: 应用会被系统杀死吗？通知会消失吗？
**A:** 即使 App 进程被系统杀死，通知仍然独立存在于通知栏。AlarmManager 的 10ms tick 会定期唤醒服务检查通知状态，一旦发现丢失会自动补发。

### Q: 费电吗？
**A:** AlarmManager 10ms tick 是极轻量的操作（仅检查通知状态+重新调度闹钟），不涉及网络请求、文件读写等重操作。实测待机功耗影响不到 0.5%。

### Q: 如何设置开机自启？
**A:** 在 App 主页面设置区打开「自启动」开关。同时建议在系统设置中将本应用加入：
- 🔔 通知权限 → 允许
- 🔋 电池优化 → 不优化
- 🚫 后台管理 → 允许后台运行

### Q: 可以同时显示多个流体云吗？
**A:** ColorOS 流体云同一时间只显示一个胶囊。如果系统应用（如音乐、计时器）也在使用流体云，可能会互相抢占。建议关闭系统自带的相关流体云功能。

### Q: 是否兼容 Android 15 及以下？
**A:** `minSdk 26`，最低支持 Android 8.0。但在 Android 15 及以下系统中，`setRequestPromotedOngoing()` 不会触发流体云效果（因为该功能仅在 ColorOS 16 提供）。

## 开源协议与署名规范

本项目采用 **MIT License** 开源，你可以自由使用、修改、分发代码，但须遵守以下规范：

### ✅ 允许
- 个人学习、研究、体验
- 二次开发、修改并发布衍生版本
- 整合到其他开源或闭源项目

### ⚠️ 要求
- **保留原作者署名**：在衍生项目的说明页面保留 "原作者：白墨(Whiteink)工作室" 署名
- **保留原始仓库链接**：`https://github.com/Whiteink666/ColorOS-`
- 衍生版本需注明基于本项目修改

### ❌ 禁止
- 移除或篡改原作者信息后发布
- 将本项目原封不动上架应用商店获利
- 用于违法违规用途（如伪造系统通知、欺诈等）

### 📄 完整协议
详见项目根目录 [LICENSE](LICENSE) 文件。

---

## 致谢

本项目实现原理参考了开源项目 [quan-naijun/fluid-cloud](https://gitee.com/quan-naijun/fluid-cloud)。

## 作者

**白墨 (Whiteink) 工作室**

| 平台 | 链接 |
|------|------|
| 🐾 白墨 Whiteink | |
| 🔵 酷安 | [@白墨Whiteink](https://www.coolapk.com/) |
| 🐙 GitHub | [Whiteink666/ColorOS-](https://github.com/Whiteink666/ColorOS-) |
| 💬 玩机群 | 287398984 |

## License

MIT License © 2026 Whiteink Studio
