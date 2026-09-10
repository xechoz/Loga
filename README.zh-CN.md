# loga

[English](README.md) | [中文](README.zh-CN.md)

一个基于 `mmap` 的 Kotlin Multiplatform 日志库。

日志行写入内存映射文件，因此写入开销很低，并且能在进程被杀后存活：脏页保留在内核页缓存中并最终刷入磁盘。后台 worker 从映射中拷贝快照，追加到按天滚动的日志文件。

## 平台

| 平台 | Target | mmap 实现 |
|---|---|---|
| Android | `androidTarget` | `MappedByteBuffer` |
| JVM (desktop) | `jvm` | `MappedByteBuffer` |
| iOS | `iosArm64` / `iosSimulatorArm64` / `iosX64` | `platform.posix.mmap` |

## 用法

```kotlin
// Android
Loga.init(context, LogConfig(retentionDays = 7))

// JVM / iOS
Loga.init(LogConfig(logDirectory = "/path/to/logs"))

Loga.i("Network", "request finished")
Loga.e("Network", "request failed")
Loga.flush()
```

### 配置

```kotlin
LogConfig(
    logDirectory = null,          // 各平台默认值
    bufferSize = 400 * 1024,      // mmap 缓冲区大小
    level = Level.DEBUG,          // 最低日志级别
    formatter = DefaultFormatter, // "L/TAG: msg\n"
    retentionDays = 7,            // 删除早于该天数的文件
    isDebug = true,               // true: 文件 + 控制台；false: 仅文件
    appenders = null,             // null 时由 isDebug 决定；传列表可覆盖
)
```

### 扩展点

- `Formatter` — 自定义日志行格式。
- `Appender` — 自定义输出目标（默认：mmap 文件 + 平台控制台）。

## 设计

完整设计见 [`docs/architecture.md`](docs/architecture.md)，技术概览见
[`docs/tech-stack.md`](docs/tech-stack.md)。

关键特性：

- **线性缓冲区**：日志行追加到 `dataStart + logLen`；缓冲区满时刷出并清空。大于整个缓冲区的日志行会被截断并给出警告。
- **异步刷出**：单个 worker 按 FIFO 顺序消费自包含的刷出任务，因此日志线程不会阻塞在磁盘 I/O 上。
- **崩溃恢复**：缓冲区文件以持久化 header 开头
  （`magic | logLen | logPathLen | logPath`）。启动时，上一个进程遗留的脏尾部会在重建映射前写回。
- **按天滚动**：文件命名为 `yyyy_MM_dd.txt`；滚动由缓存的日期边界驱动，因此热路径只需一次比较。
- **保留策略**：早于 `retentionDays` 的文件会在初始化和每次滚动时删除。

### 持久性

- **保证**：进程被杀（`kill -9`）不会丢失日志。
- **不保证**：断电，除非调用 `force()` / `msync`。

## 构建

```bash
./gradlew :loga:jvmTest
./gradlew :loga:compileKotlinIosArm64
```

Android target 需要已安装并接受许可的 Android SDK platform（API 36）。如果系统 SDK 不可写，可安装一个本地 SDK：

```bash
sdkmanager --sdk_root=.android-sdk "platforms;android-36" "build-tools;36.0.0"
ANDROID_HOME=$PWD/.android-sdk ./gradlew :loga:assemble
```

`.android-sdk/` 已被 git 忽略。

## 参考

- [Log4a](https://github.com/pqpo/Log4a) — mmap 线性缓冲区、异步刷出与启动恢复；本项目引擎设计参考来源（Apache 2.0）。
- [Tencent mars/xlog](https://github.com/Tencent/mars) — 最成熟的 mmap 日志方案；未来压缩、加密与多进程支持的参考。
- [Meituan Logan](https://github.com/Meituan-Dianping/Logan) — 其流式协议与单写线程模型的参考。
