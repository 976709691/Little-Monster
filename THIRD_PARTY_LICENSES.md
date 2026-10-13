# 第三方组件许可证说明

## sherpa-onnx（语音识别内核）

- 项目：https://github.com/k2-fsa/sherpa-onnx
- 许可证：Apache License 2.0
- 使用方式：
  - 其 Java API 源码已内嵌于本仓库 `app/src/main/java/com/k2fsa/sherpa/onnx/`（102 个类，未做修改）。
  - 其预编译原生库（`libonnxruntime.so`、`libsherpa-onnx-jni.so`）位于 `app/src/main/jniLibs/`，来自官方 v1.13.8 Android 发布包。
- 完整许可证文本见：`third_party/sherpa-onnx/LICENSE`。

## Whisper 语音模型（运行时下载，不入库）

- 模型仓库：https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny|base|small
- 模型权重复制自 OpenAI Whisper（MIT 许可），由 sherpa-onnx 社区导出为 onnx 格式。
- App 首次使用时从 hf-mirror.com 下载，下载即视为同意模型许可。
