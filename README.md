# 影视同声传译 (MovieSubtitle)

一个 Android 端「看电影同声翻译」工具：**抓取手机正在播放的声音 → 本地离线识别成文字 → 大模型翻译成中文 → 系统级悬浮字幕**。无需外挂字幕文件，适合观看没有字幕的国外电影、剧集、直播等场景。

> 本项目为移动端 App（Android 9+，ARM 架构），仓库内包含完整源码与 CI，clone 后可直接构建 APK。

## 功能特性

- 🎙️ **系统声音捕获**：使用 `MediaProjection` + `AudioPlaybackCaptureConfiguration` 只抓取正在播放的媒体声音，不占用麦克风、不影响音量。
- 🧠 **本地离线识别**：内置 [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) + Whisper 语音模型，识别全程本机执行，声音不上传。
- 🌐 **大模型翻译**：接入 OpenAI 兼容接口（默认 DeepSeek，可换任意兼容服务）；也可切换 `dummy` 模式只显示原文。
- 🎬 **系统级悬浮字幕**：`TYPE_APPLICATION_OVERLAY` 悬浮窗，黑底白字译文 + 灰字原文，可按住拖动位置。
- 📲 **移动端优先**：minSdk 29 / targetSdk 34，适配 arm64-v8a / armeabi-v7a。

## 工作原理

```
正在播放的声音 → AudioRecord(16k mono) → 静音分段(Segmenter) → Whisper 离线识别
                                                              ↓
系统悬浮字幕条 ← 大模型翻译（可选） ←────────────── 识别文本
```

## 仓库结构

```
AndroidApp/
├── app/
│   ├── src/main/java/com/doubao/mobilesub/   # 应用源码（Kotlin）
│   │   ├── MainActivity.kt                   # 主界面：配置/下载模型/权限流程
│   │   ├── CaptureService.kt                 # 前台服务：音频捕获流水线
│   │   ├── WhisperEngine.kt                  # sherpa-onnx Whisper 识别引擎
│   │   ├── Segmenter.kt                      # 静音分段器
│   │   ├── Translator.kt                     # 大模型翻译（OpenAI 兼容）
│   │   └── SubtitleOverlay.kt                # 悬浮字幕窗
│   ├── src/main/java/com/k2fsa/sherpa/onnx/  # sherpa-onnx 官方 Java API（内嵌源码，Apache-2.0）
│   ├── src/main/jniLibs/                     # sherpa-onnx 原生库（onnxruntime + sherpa-onnx-jni）
│   ├── src/test/java/...                     # 单元测试
│   └── build.gradle.kts
├── gradle/wrapper/                           # Gradle Wrapper
├── gradlew / gradlew.bat
├── third_party/sherpa-onnx/LICENSE           # 第三方组件许可证
├── settings.gradle.kts / build.gradle.kts
└── .github/workflows/android-build.yml       # GitHub Actions CI
```

## 环境要求

- JDK 17
- Android SDK（platform 34、build-tools 34.0.0），通过 `ANDROID_HOME` 或 `local.properties` 指定
- 无需安装 sherpa-onnx：Java API 源码与 `.so` 原生库已内嵌仓库

## 构建

```bash
# 命令行（Linux/macOS）
./gradlew assembleDebug

# Windows
gradlew.bat assembleDebug

# 运行单元测试
./gradlew testDebugUnitTest

# 产物
app/build/outputs/apk/debug/app-debug.apk
```

也可直接用 Android Studio 打开本目录构建。

## 使用

1. 安装 APK（Android 9+）。
2. 打开应用 → 选择模型大小（tiny/base/small）→ 点「下载模型」（模型自 hf-mirror.com 下载，仅首次需要）。
3. 填写大模型 API Key（可选；`dummy` 模式仅显示原文，无需 Key）。
4. 点「开始翻译」，依次授权悬浮窗 / 通知 / 投屏（用于抓声音）。
5. 回到播放器放电影，屏幕下方出现悬浮字幕条。

已知限制：DRM 加密内容（部分正版在线影片）系统禁止捕获声音；识别+翻译有数秒延迟属正常。

## 测试

- `SegmenterTest`：静音分段器的分段时机、最长时长、尾部保留逻辑（纯 JVM 单测）。
- CI（GitHub Actions）每次 push 自动执行 `assembleDebug` + 单测并上传 APK 产物。

## 许可证与致谢

- 应用源码：Apache-2.0（见根目录 `LICENSE`）。
- 语音识别内核：[sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)（Apache-2.0），其 Java API 源码已内嵌于 `app/src/main/java/com/k2fsa/sherpa/onnx/`，许可证见 `third_party/sherpa-onnx/LICENSE`。
- 语音模型：[csukuangfj/sherpa-onnx-whisper-*](https://huggingface.co/csukuangfj)（Whisper 系列，MIT 许可模型权重）。

## 免责声明

本工具仅用于个人观影辅助。请遵守内容版权与所在地区法律，勿将翻译结果用于商业用途。
