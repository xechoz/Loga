# loga 技术栈清单

[English](tech-stack.md) | [中文](tech-stack.zh-CN.md)

> 目标：用 Kotlin Multiplatform + mmap 实现一个跨平台日志库。
> 本文列出需要深入了解的技术栈，并标注 **v1 用** / **后续**，说明每项「为什么需要」。
>
> 参考实现：[Log4a](https://github.com/pqpo/Log4a)（Apache 2.0）、[Tencent mars/xlog](https://github.com/Tencent/mars)、[美团 Logan](https://github.com/Meituan-Dianping/Logan)。

---

## 0. 目标平台

| 平台 | Target | v1 |
|---|---|---|
| Android | `androidTarget` | ✅ |
| JVM (desktop) | `jvm` | ✅ |
| iOS | `iosArm64` / `iosSimulatorArm64` / `iosX64` | ✅ |
| Windows | `mingwX64` | 后续 |
| JS / Wasm | — | ❌ 无 mmap，明确排除 |

---

## 1. mmap 核心原理 —— v1 用

| 技术点 | 为什么需要 |
|---|---|
| `mmap` / `FileChannel.map` | 日志写入的核心：把文件映射进进程地址空间，写入即写内存，避免每次 `write` 的系统调用与内核态拷贝 |
| 页缓存（page cache）、缺页中断 | 理解「写内存」为何最终会落盘；首次写入触发缺页分配物理页 |
| 文件预分配（`ftruncate` / `RandomAccessFile.setLength`） | 映射前必须先确定文件大小，否则访问越界触发 `SIGBUS` |
| `msync` / `MappedByteBuffer.force()` | 显式把脏页写回磁盘；不调用则依赖内核回写线程 |
| mmap vs write | mmap 少一次用户态→内核态拷贝，是性能优势的来源 |

**必须写进文档的边界声明**：mmap 保证「进程被强杀（kill -9）不丢日志」（页仍由内核管理，会回写）；**不保证「断电不丢」**（取决于内核脏页回写时机，除非显式 `msync`/`force()`）。这是 mmap 方案的诚实边界。

---

## 2. 线性缓冲与并发控制 —— v1 用

| 技术点 | 为什么需要 |
|---|---|
| 线性缓冲（append 到 `dataStart + logLen`） | 参考实现 Log4a 用的是**线性缓冲而非环形缓冲**：顺序追加，写满后整体刷盘并清空。实现简单、无回绕边界问题 |
| 写满策略 | 行放不下 → 先刷盘再写；行 > 缓冲总容量 → 截断并告警（参考实现此处静默截断，是缺陷） |
| `kotlinx.atomicfu.locks.synchronized` | 保护缓冲的读写指针；v1 用普通锁即可，不引入无锁 |
| 非递归锁拆分 | 参考实现用 `recursive_mutex`（因 `changeLogPath` 持锁内再调 `asyncFlush`）。新库把内部实现拆成「已持锁私有方法」，用普通锁 |
| 内存屏障 / 原子操作 | **后续**：无锁化时才需要 |

---

## 3. 平台 mmap 接入（expect/actual）—— v1 用

KMP 下 mmap 的调用方式按平台分派，收敛为一个 `MappedBuffer` 抽象：

| 平台 | 实现方式 | 说明 |
|---|---|---|
| Android / JVM | `FileChannel.map()` → `MappedByteBuffer` | **纯 Kotlin，无 NDK/JNI**。`put`/`get` 走快速 JNI 路径；`force()` 即 `msync` |
| iOS | `platform.posix.mmap()` | Kotlin/Native 直接调 POSIX，**无需 C++**。需 `@OptIn(ExperimentalForeignApi::class)` 与指针 pinning |

工厂函数 `openMappedBuffer(path, size)` 封装平台侧的打开 + 预分配 + 映射。

**MappedByteBuffer 的边界**：JVM 上无法显式 `unmap`（映射由 GC 管理），`release()` 只能 `force()` + 置空引用。单缓冲、进程级生命周期场景下无实际影响；崩溃持久性不受影响（kill -9 时内核仍回写脏页）。

**Kotlin/Native 指针安全**：mmap 返回 `CPointer<ByteVar>`，跨线程使用需谨慎。策略：写入时在锁内 `memcpy`，刷盘时把数据快照拷贝进 `ByteArray` 再交给后台线程，避免裸指针跨线程。

---

## 4. expect/actual 机制 —— v1 用

平台差异收敛为 3 个点：

| 抽象 | Android | JVM | iOS |
|---|---|---|---|
| `MappedBuffer` | MappedByteBuffer | MappedByteBuffer | posix.mmap |
| 默认日志目录 | `getExternalFilesDir("logs")/logs`（回退 `filesDir`） | `user.home/logs` | `NSDocumentDirectory/logs` |
| 控制台输出 | `android.util.Log`（logcat） | `println` | `NSLog` |
| 崩溃钩子 | `Thread.setDefaultUncaughtExceptionHandler` | `Thread.setDefaultUncaughtExceptionHandler` | no-op |

---

## 5. 协程与异步刷盘 —— v1 用

| 技术点 | 为什么需要 |
|---|---|
| `kotlinx-coroutines-core` | 跨平台线程抽象；worker 运行在 `Dispatchers.Default` |
| 单 worker + 任务队列 | 写日志线程只做 memcpy 快照，落盘交给后台线程，避免阻塞调用方。`Worker` 用 `Channel(UNLIMITED)` 按 FIFO 顺序处理任务 |
| 自包含刷盘任务（FlushBuffer） | 任务持有目标路径 + 数据拷贝，因此缓冲对象被释放后后台任务仍安全 |
| `submitAndWait` / `shutdown` | `flush()` 阻塞到此前提交的任务全部写完；`shutdown()` 关闭队列并 join worker，release 时不丢数据 |
| partial write / 中断处理 | `write` 可能只写一部分或被信号中断，需循环补齐 |

---

## 6. 日期与文件管理 —— v1 用

| 技术点 | 为什么需要 |
|---|---|
| `kotlinx-datetime` + `kotlin.time.Clock` | 跨平台日期计算，用于 `yyyy_MM_dd.txt` 命名与跨天边界 |
| 按天切文件 | 日志文件命名 `yyyy_MM_dd.txt`，跨天自动切换 |
| 日期边界比较 | 缓存 `nextSwitchMillis`，每次写入只做一次 `now >= nextSwitchMillis` 比较，避免每行都做日期格式化（热路径开销） |
| 日志保留清理 | 删除目录中超过 `retentionDays`（默认 7 天）的旧文件；在 init 时与每次切文件时执行 |
| 进程生命周期 | 进程被杀、`onTrimMemory` 时的刷盘与释放策略 |

---

## 7. 序列化 / 压缩 / 加密 —— 后续

| 技术点 | 说明 |
|---|---|
| 二进制序列化（protobuf / flatbuffers） | 结构化日志、更紧凑的存储格式 |
| 压缩（zlib / zstd / snappy） | Log4a 用 zlib raw-deflate；xlog 用 zlib。v1 不做，避免压缩耦合进热路径 |
| 加密（AES） | xlog 支持加密；v1 不做 |

---

## 8. 多进程 / 无锁 —— 后续

| 技术点 | 说明 |
|---|---|
| 多进程共享 mmap | 多个进程写同一日志文件的并发控制 |
| 无锁队列 / CAS / 缓存行对齐 | 高并发下的性能优化，v1 用互斥锁即可 |

---

## 9. 参考方案对比

| 方案 | 语言 | 特点 | 对本项目的价值 |
|---|---|---|---|
| **Log4a** | Kotlin + C++ | 线性 mmap 缓冲、异步刷盘、启动恢复；结构清晰、体量小 | **借鉴引擎设计**（线性缓冲、flushDirty、自包含快照），不照搬其 C++ 实现；修正其缺陷（静默截断、无自动切文件、recursive_mutex、heap 降级无告警） |
| **xlog (mars)** | C++ | 最成熟，mmap + zlib + AES，多进程 | 后续做压缩/加密/多进程时参考 |
| **Logan** | Java/Kotlin | 协议简单、单写线程模型 | 参考其流式协议与线程模型 |

### Log4a 实测性能（README，Google Pixel，写 1w 条日志）

| 方案 | 耗时 |
|---|---|
| 纯内存 | 13 ms |
| **Log4a** | **50 ms** |
| File with Buffer | 61 ms |
| Android Log | 184 ms |
| File no Buffer | 272 ms |

Log4a 断电/强杀不丢日志（靠下次启动恢复）。

---

## v1 技术栈总结

**必须掌握**：mmap 原理与边界、线性缓冲 + 互斥锁、expect/actual 平台接入（MappedByteBuffer / posix.mmap）、启动脏尾部恢复、协程单 worker 异步刷盘、kotlinx-datetime 日期管理与按天切文件。

**明确不做**：压缩、加密、多进程、无锁、对象池、ByteBuffer 直传优化、Windows 目标。
