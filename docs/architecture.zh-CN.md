# loga 架构设计

[English](architecture.md) | [中文](architecture.zh-CN.md)

> Kotlin Multiplatform mmap 日志库。对外 API 简单，保持适度扩展，不过度设计。
> 包名 `me.xechoz.loga`，库名 `loga`。
> 目标平台：Android、JVM (desktop)、iOS。

---

## 1. 目标与原则

### 目标
- **API 简单**：一行 `Loga.i("tag", "msg")` 即可用；初始化只需 `Loga.init(config)`。
- **适度扩展**：只保留真正需要的扩展点（`Formatter`、`Appender`），不预设用不到的抽象。
- **不过度设计**：v1 只做核心能力，砍掉参考实现中的冗余抽象。
- **跨平台**：核心引擎全部在 `commonMain`，平台差异收敛为少量 `expect/actual`。

### v1 范围
mmap 缓冲 + 异步刷盘 + 按天切文件 + 平台控制台输出 + 日志保留清理（默认 7 天，可配置）。

### 非目标（v1 明确不做）
压缩、加密、多进程、无锁、对象池、`ByteBuffer` 直传优化、拦截器链、Windows 目标。

---

## 2. 核心概念

| 概念 | 定义 | 为什么这样设计 |
|---|---|---|
| **MMap 缓冲区** | 一个映射到文件的固定大小内存区，日志先写入这里 | 写入即写内存，避免每次 `write` 的系统调用与拷贝 |
| **持久化 Header** | 缓冲区文件头部的一段元数据：`magic \| logLen \| logPathLen \| logPath` | 进程被杀后，下次启动能知道「有多少脏数据、该写往哪个日志文件」 |
| **线性缓冲** | 数据从 `dataStart + logLen` 顺序追加，写满后整体刷盘并清空 | 比环形缓冲简单，无回绕边界问题；参考实现即此模型 |
| **异步刷盘** | 写日志线程只做 memcpy 快照，落盘交给后台单 worker 线程 | 不阻塞调用方 |
| **脏尾部（dirty tail）** | 上次进程被杀时残留在缓冲区、尚未落盘的数据 | 启动时通过 `recoverDirtyTail` 恢复，实现「强杀不丢」 |
| **日志文件轮转** | 日志按天写入 `yyyy_MM_dd.txt`，跨天自动切换 | 便于按日期查找与清理 |

### 持久性边界（重要）
- **保证**：进程被强杀（kill -9）不丢日志——页仍由内核管理，会回写磁盘。
- **不保证**：断电不丢——取决于内核脏页回写时机，除非显式 `msync`/`force()`。

---

## 3. 分层架构

```
┌──────────────────────────────────────────────────┐
│  Kotlin API 层（commonMain）                       │
│  Log（门面）  LogConfig  Formatter  Appender       │
│  ├─ FileAppender ──┐                               │
│  └─ ConsoleAppender│（平台控制台输出）             │
└────────────────────┼──────────────────────────────┘
                     │
┌────────────────────▼──────────────────────────────┐
│  引擎层（commonMain，纯 Kotlin）                    │
│  LogBuffer（线性缓冲 + Header + 切文件）           │
│  AsyncFlush（单 worker + 任务队列）                │
│  Worker（common 协程 worker）                      │
│  LogFileManager（按天轮转 + retention 清理）       │
└────────────────────┬──────────────────────────────┘
                     │ expect/actual
┌────────────────────▼──────────────────────────────┐
│  平台适配层                                        │
│  MappedBuffer  默认目录  控制台输出                │
│  Android/JVM: MappedByteBuffer                     │
│  iOS: platform.posix.mmap                          │
└────────────────────┬──────────────────────────────┘
                     │ mmap / write
┌────────────────────▼──────────────────────────────┐
│  文件系统                                          │
│  .logCache（mmap 缓冲文件）                        │
│  yyyy_MM_dd.txt（日志文件）                        │
└───────────────────────────────────────────────────┘
```

---

## 4. 对外 API 与扩展点

### 4.1 门面 `Loga`（commonMain）

```kotlin
object Loga {
    fun init(config: LogConfig)
    fun v(tag: String, msg: String)
    fun d(tag: String, msg: String)
    fun i(tag: String, msg: String)
    fun w(tag: String, msg: String)
    fun e(tag: String, msg: String)
    fun println(level: Int, tag: String, msg: String)  // 通用入口
    fun flush()      // 立即把缓冲刷入日志文件
    fun release()    // 刷盘并释放资源
}
```

`init` 会先调用 `release()`，因此可安全地重复初始化。

Android 便捷入口（`androidMain`）：

```kotlin
fun Loga.init(context: Context, config: LogConfig = LogConfig())
```

### 4.2 配置 `LogConfig`

```kotlin
data class LogConfig(
    val logDirectory: String? = null,        // 默认由平台 actual 解析
    val bufferSize: Int = 400 * 1024,        // mmap 缓冲大小
    val level: Int = Level.DEBUG,            // 最低输出级别
    val formatter: Formatter = DefaultFormatter,
    val retentionDays: Int = 7,              // 日志保留天数，可配置
    val flushIntervalMillis: Long = 5_000,   // 后台定时刷盘间隔；0 表示关闭
    val isDebug: Boolean = true,             // true: 文件 + 控制台；false: 仅文件
    val appenders: List<Appender>? = null,   // null 时由 isDebug 决定，追加在文件 appender 之后
    val logUncaughtExceptions: Boolean = true, // 安装崩溃钩子，记录并刷盘
)
```

### 4.3 扩展点（仅两个）

```kotlin
fun interface Formatter {
    fun format(level: Int, tag: String, msg: String): String
}

interface Appender {
    fun append(level: Int, tag: String, line: String)  // line 已格式化
    fun flush()
    fun release()
}
```

- **`Formatter`**：自定义日志行格式，默认输出 `L/TAG: msg\n`。
- **`Appender`**：自定义输出目标。文件落盘（`FileAppender`）始终启用，`appenders` 中的 appender 追加在其后；`appenders = null` 时由 `isDebug` 决定：`true` 追加 `ConsoleAppender`（平台控制台），`false` 仅文件。传 `emptyList()` 可显式关闭控制台输出。

> 设计取舍：参考实现 Log4a 有 `Logger` + `AppenderLogger` + `Interceptor` 链 + `LogData` 对象池，属过度设计。v1 只保留 `Appender` 一层扇出，级别过滤在门面处完成。

---

## 5. 平台适配点（expect/actual）

```kotlin
// commonMain, Platform.kt
expect class MappedBuffer {
    val size: Long
    fun put(position: Long, src: ByteArray, offset: Int, length: Int)
    fun get(position: Long, dst: ByteArray, offset: Int, length: Int)
    fun force()
    fun close()
}

expect fun openMappedBuffer(path: String, size: Long): MappedBuffer
expect fun defaultLogDirectory(): String
expect fun consoleLog(level: Int, tag: String, line: String)
expect fun installUncaughtExceptionHook(onUncaught: (message: String) -> Unit): () -> Unit
```

| 抽象 | Android | JVM | iOS |
|---|---|---|---|
| `MappedBuffer` | MappedByteBuffer | MappedByteBuffer | `platform.posix.mmap` |
| `defaultLogDirectory` | `getExternalFilesDir("logs")/logs`（回退 `filesDir`） | `user.home/logs` | `NSDocumentDirectory/logs` |
| `consoleLog` | `android.util.Log` | `println` | `NSLog` |
| `installUncaughtExceptionHook` | `Thread.setDefaultUncaughtExceptionHandler` | `Thread.setDefaultUncaughtExceptionHandler` | no-op |

**Kotlin/Native 指针安全**：mmap 返回 `CPointer<ByteVar>`，写入在锁内 `memcpy`；刷盘时把数据快照拷贝进 `ByteArray` 再交给后台线程，避免裸指针跨线程。

---

## 6. 核心流程

### 6.1 写入流程

```
Loga.i(tag, msg)
  → 级别过滤（level < config.level 直接返回）
  → formatter.format(level, tag, msg)
  → 遍历 appenders
      ├─ ConsoleAppender → consoleLog(...)
      └─ FileAppender → LogBuffer.append
            → 检查日期边界：now >= nextSwitchMillis ? 切文件 : 继续
            → 若缓冲剩余空间不足 → 先 asyncFlush
            → 若单行 > 缓冲总容量 → 截断 + 告警
            → memcpy 到 dataStart + logLen，logLen += len
```

### 6.2 刷盘流程（异步）

```
flushLocked()
  → logLen == 0 ? 直接返回
  → 把缓冲数据区拷贝进自包含的 FlushBuffer（path + ByteArray）
  → 重置 logLen = 0 并重写 Header
  → 提交任务到 AsyncFlush 队列
  → worker 协程取出任务 → append 到文件

flush()
  → flushLocked() 后 asyncFlush.await()   // 阻塞到全部写完
```

**关键点**：`FlushBuffer` 自包含（持有目标路径 + 数据拷贝），因此 `LogBuffer` 被释放后后台任务仍安全。

### 6.3 启动恢复流程

```
LogBuffer.init()
  → 若 buffer 文件已存在，必须在重建映射之前恢复脏尾部
        recoverDirtyTail(existingSize)
          → 打开已有映射，读取 Header
          → 若 logLen > 0，拷贝脏数据并提交 FlushBuffer 到 asyncFlush
  → 预分配文件大小 + mmap（总大小 = capacity + headerSize(logPathLen)）
  → 写入新 Header
```

**关键点**：恢复必须在重建映射之前，否则脏数据被抹掉。

### 6.4 按天切文件 + 保留清理

```
切文件触发：
  每次写入时比较 now >= nextSwitchMillis
    → 是：asyncFlush 当前缓冲 → 打开新日期文件 → 重算 nextSwitchMillis
    → 否：继续写入

保留清理触发：
  init 时 + 每次切文件时
    → 扫描日志目录
    → 删除文件名日期早于 (今天 - retentionDays) 的 yyyy_MM_dd.txt
```

**关键点**：用 `nextSwitchMillis` 边界比较，避免每行都做日期格式化。

---

## 7. 数据结构

### 7.1 持久化 Header（v1 简化版）

```
偏移  字段        类型      说明
0     magic       byte      固定 0x11，用于判断 Header 是否有效
1     logLen      Long      缓冲区中有效数据长度（脏数据量）
      logPathLen  Int       日志文件路径长度
      logPath     bytes     日志文件路径（UTF-8）
```

- `headerSize(logPathLen) = 1 + 8 + 4 + logPathLen`
- `isAvailable() = data[0] == magic`
- `dataStart() = data + headerSize(logPathLen)`

> 相比参考实现去掉了 `isCompress` 字段（v1 不做压缩）。

### 7.2 缓冲文件布局

```
┌──────────────┬──────────────────────────────┐
│  Header      │  数据区（capacity 字节）       │
│  (动态大小)   │  ← logLen 有效 →              │
└──────────────┴──────────────────────────────┘
文件总大小 = capacity + headerSize(logPathLen)
```

---

## 8. 设计取舍

| 取舍点 | 选择 | 理由 |
|---|---|---|
| 纯 Kotlin vs JNI/C++ | **纯 Kotlin** | KMP 下 Native 可直接调 POSIX，JVM 用 MappedByteBuffer；砍掉 NDK/CMake/JNI 全部复杂度 |
| 线性 vs 环形缓冲 | **线性** | 实现简单、无回绕边界；写满整体刷盘，符合日志「顺序追加」语义 |
| 互斥锁 vs 无锁 | **互斥锁** | v1 优先正确性与简单；无锁列入后续 |
| 递归锁 vs 普通锁 | **普通锁 + 已持锁私有方法** | 参考实现用 `recursive_mutex` 是设计耦合的结果，可拆解 |
| 写满时行为 | **先刷盘再写；行 > 容量则截断 + 告警** | 参考实现静默截断是缺陷，必须让使用者知道数据被丢弃 |
| mmap 失败 | **直接抛错** | 库不静默降级到堆缓冲；映射失败是硬错误 |
| MappedByteBuffer 不可显式 unmap | **接受** | 单缓冲、进程级生命周期下无影响；`release()` = `force()` + 置空引用。iOS 会调用 `munmap` |
| 扩展点数量 | **仅 Formatter + Appender** | 砍掉 Interceptor 链、LogData 对象池、Logger/AppenderLogger 双抽象 |
| 日期切换 | **nextSwitchMillis 边界比较** | 避免热路径上的日期格式化开销 |
| 持久性声明 | **强杀不丢，断电不保证** | 如实说明 mmap 边界，不夸大 |

---

## 9. 模块文件结构

```
loga/
├── build.gradle.kts
├── src/
│   ├── commonMain/kotlin/me/xechoz/loga/
│   │   ├── Loga.kt                # 门面
│   │   ├── LogConfig.kt           # 配置
│   │   ├── Level.kt               # 级别常量
│   │   ├── LogBuffer.kt           # 线性缓冲 + Header + 切文件
│   │   ├── LogBufferHeader.kt     # 持久化 Header 读写
│   │   ├── AsyncFlush.kt          # 单 worker 异步刷盘
│   │   ├── Worker.kt              # common 协程 worker（FIFO 队列）
│   │   ├── FlushBuffer.kt         # 自包含刷盘任务
│   │   ├── LogFileManager.kt      # 按天轮转 + retention 清理
│   │   ├── Platform.kt            # expect：MappedBuffer / 目录 / 控制台 / 崩溃钩子
│   │   ├── FileSystem.kt          # expect 文件操作
│   │   ├── formatter/
│   │   │   ├── Formatter.kt
│   │   │   └── DefaultFormatter.kt
│   │   └── appender/
│   │       ├── Appender.kt
│   │       ├── FileAppender.kt
│   │       └── ConsoleAppender.kt
│   ├── androidMain/kotlin/me/xechoz/loga/
│   │   ├── MappedBuffer.android.kt
│   │   ├── FileSystem.android.kt
│   │   └── Platform.android.kt    # 含 Loga.init(context, config)
│   ├── jvmMain/kotlin/me/xechoz/loga/
│   │   ├── MappedBuffer.jvm.kt
│   │   ├── FileSystem.jvm.kt
│   │   └── Platform.jvm.kt
│   └── iosMain/kotlin/me/xechoz/loga/
│       ├── MappedBuffer.ios.kt
│       ├── FileSystem.ios.kt
│       └── Platform.ios.kt
```
