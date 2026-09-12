# loga Architecture

[English](architecture.md) | [中文](architecture.zh-CN.md)

> A Kotlin Multiplatform mmap logging library. The public API stays small, with
> just enough extension points and no over-engineering.
> Package `me.xechoz.loga`, library name `loga`.
> Targets: Android, JVM (desktop), iOS.

---

## 1. Goals and Principles

### Goals
- **Simple API**: one line `Loga.i("tag", "msg")` is enough; initialization is just `Loga.init(config)`.
- **Moderate extensibility**: keep only the extension points that are actually needed (`Formatter`, `Appender`); do not predefine abstractions nobody uses.
- **No over-engineering**: v1 ships only the core capabilities and drops the redundant abstractions found in the reference implementations.
- **Cross-platform**: the whole engine lives in `commonMain`; platform differences collapse into a handful of `expect/actual` declarations.

### v1 Scope
mmap buffer + asynchronous flush + daily file rotation + platform console output + log retention cleanup (7 days by default, configurable).

### Non-goals (explicitly out of scope for v1)
Compression, encryption, multi-process, lock-free, object pools, direct `ByteBuffer` optimization, interceptor chains, Windows target.

---

## 2. Core Concepts

| Concept | Definition | Why it is designed this way |
|---|---|---|
| **MMap buffer** | A fixed-size memory region mapped to a file; log lines are written here first | Writing is a memory write, avoiding a `write` syscall and copy per line |
| **Persistent header** | A metadata block at the start of the buffer file: `magic \| logLen \| logPathLen \| logPath` | After the process is killed, the next start knows "how many dirty bytes exist and which log file they belong to" |
| **Linear buffer** | Data is appended sequentially at `dataStart + logLen`; when full it is flushed and cleared | Simpler than a ring buffer, no wrap-around edge cases; the reference implementation uses this model |
| **Async flush** | The logging thread only takes a memcpy snapshot; writing to disk is delegated to a single background worker | Never blocks the caller |
| **Dirty tail** | Data left in the buffer by a previous process that was killed before it was flushed | Recovered on startup via `recoverDirtyTail`, which is what makes "kill -9 does not lose logs" work |
| **Log file rotation** | Logs are written to `yyyy_MM_dd.txt` and switch automatically across days | Easy to find and clean up by date |

### Durability boundary (important)
- **Guaranteed**: a process kill (`kill -9`) does not lose logs — the pages are still managed by the kernel and will be written back to disk.
- **Not guaranteed**: power loss, unless `msync`/`force()` is called explicitly.

---

## 3. Layered Architecture

```
┌──────────────────────────────────────────────────┐
│  Kotlin API layer (commonMain)                    │
│  Loga (facade)  LogConfig  Formatter  Appender    │
│  ├─ FileAppender ──┐                              │
│  └─ ConsoleAppender│ (platform console output)    │
└────────────────────┼──────────────────────────────┘
                     │
┌────────────────────▼──────────────────────────────┐
│  Engine layer (commonMain, pure Kotlin)           │
│  LogBuffer (linear buffer + header + rotation)    │
│  AsyncFlush (single worker + task queue)          │
│  Worker (common coroutine worker)                 │
│  LogFileManager (daily rotation + retention)      │
└────────────────────┬──────────────────────────────┘
                     │ expect/actual
┌────────────────────▼──────────────────────────────┐
│  Platform adaptation layer                        │
│  MappedBuffer  default directory  console output  │
│  Android/JVM: MappedByteBuffer                    │
│  iOS: platform.posix.mmap                         │
└────────────────────┬──────────────────────────────┘
                     │ mmap / write
┌────────────────────▼──────────────────────────────┐
│  File system                                      │
│  .logCache (mmap buffer file)                     │
│  yyyy_MM_dd.txt (log files)                       │
└───────────────────────────────────────────────────┘
```

---

## 4. Public API and Extension Points

### 4.1 Facade `Loga` (commonMain)

```kotlin
object Loga {
    fun init(config: LogConfig)
    fun v(tag: String, msg: String)
    fun d(tag: String, msg: String)
    fun i(tag: String, msg: String)
    fun w(tag: String, msg: String)
    fun e(tag: String, msg: String)
    fun println(level: Int, tag: String, msg: String)  // generic entry point
    fun flush()      // flush the buffer into the log file immediately
    fun release()    // flush and release resources
}
```

`init` calls `release()` first, so re-initializing is safe.

Android convenience entry point (`androidMain`):

```kotlin
fun Loga.init(context: Context, config: LogConfig = LogConfig())
```

### 4.2 Configuration `LogConfig`

```kotlin
data class LogConfig(
    val logDirectory: String? = null,        // resolved by the platform actual when null
    val bufferSize: Int = 400 * 1024,        // mmap buffer size
    val level: Int = Level.DEBUG,            // minimum level
    val formatter: Formatter = DefaultFormatter,
    val retentionDays: Int = 7,              // log retention in days
    val flushIntervalMillis: Long = 5_000,   // background flush interval; 0 disables
    val isDebug: Boolean = true,             // true: file + console; false: file only
    val appenders: List<Appender>? = null,   // null = derived from isDebug, appended after the file appender
    val logUncaughtExceptions: Boolean = true, // install a crash hook that logs and flushes
)
```

### 4.3 Extension Points (only two)

```kotlin
fun interface Formatter {
    fun format(level: Int, tag: String, msg: String): String
}

interface Appender {
    fun append(level: Int, tag: String, line: String)  // line is already formatted
    fun flush()
    fun release()
}
```

- **`Formatter`**: customizes the log line format; the default emits `L/TAG: msg\n`.
- **`Appender`**: customizes output targets. File logging (`FileAppender`) is always enabled, and appenders in `appenders` are appended after it. When `appenders = null`, `isDebug` decides: `true` appends `ConsoleAppender` (platform console), `false` keeps file only. Passing `emptyList()` explicitly disables console output.

> Design trade-off: the reference implementation Log4a has a `Logger` + `AppenderLogger` + `Interceptor` chain + `LogData` object pool, which is over-engineered. v1 keeps a single `Appender` fan-out layer, and level filtering happens in the facade.

---

## 5. Platform Adaptation Points (expect/actual)

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

| Abstraction | Android | JVM | iOS |
|---|---|---|---|
| `MappedBuffer` | MappedByteBuffer | MappedByteBuffer | `platform.posix.mmap` |
| `defaultLogDirectory` | `getExternalFilesDir("logs")/logs` (falls back to `filesDir`) | `user.home/logs` | `NSDocumentDirectory/logs` |
| `consoleLog` | `android.util.Log` | `println` | `NSLog` |
| `installUncaughtExceptionHook` | `Thread.setDefaultUncaughtExceptionHandler` | `Thread.setDefaultUncaughtExceptionHandler` | no-op |

**Kotlin/Native pointer safety**: mmap returns a `CPointer<ByteVar>`. Writes `memcpy` inside the lock; when flushing, the data snapshot is copied into a `ByteArray` before being handed to the background thread, so raw pointers never cross threads.

---

## 6. Core Flows

### 6.1 Write flow

```
Loga.i(tag, msg)
  → level filter (level < config.level returns immediately)
  → formatter.format(level, tag, msg)
  → iterate appenders
      ├─ ConsoleAppender → consoleLog(...)
      └─ FileAppender → LogBuffer.append
            → check the day boundary: now >= nextSwitchMillis ? rotate : continue
            → if the buffer cannot fit the line → flush first
            → if a single line > total capacity → truncate + warn
            → memcpy to dataStart + logLen, logLen += len
```

### 6.2 Flush flow (asynchronous)

```
flushLocked()
  → logLen == 0 ? return
  → copy the valid region into a self-contained FlushBuffer (path + ByteArray)
  → reset logLen = 0 and rewrite the header
  → submit the task to the AsyncFlush queue
  → the worker coroutine takes the task → append to the file

flush()
  → flushLocked() then asyncFlush.await()   // blocks until everything is written
```

**Key point**: `FlushBuffer` is self-contained (it owns the target path and a data copy), so background tasks remain safe even after `LogBuffer` is released.

### 6.3 Startup recovery flow

```
LogBuffer.init()
  → if the buffer file exists, recover the dirty tail BEFORE rebuilding the mapping
        recoverDirtyTail(existingSize)
          → open the existing mapping, read the header
          → if logLen > 0, copy the dirty bytes and submit a FlushBuffer to asyncFlush
  → preallocate the file and mmap (total size = capacity + headerSize(logPathLen))
  → write the new header
```

**Key point**: recovery must happen before the mapping is rebuilt, otherwise the dirty data is wiped out.

### 6.4 Daily rotation + retention cleanup

```
Rotation trigger:
  on every write, compare now >= nextSwitchMillis
    → yes: flush the current buffer → switch to the new date file → recompute nextSwitchMillis
    → no: keep writing

Retention cleanup trigger:
  on init + on every rotation
    → scan the log directory
    → delete yyyy_MM_dd.txt files whose date is older than (today - retentionDays)
```

**Key point**: the cached `nextSwitchMillis` boundary avoids formatting a date on every line.

---

## 7. Data Structures

### 7.1 Persistent header (v1 simplified)

```
offset  field       type    notes
0       magic       byte    fixed 0x11, used to tell whether the header is valid
1       logLen      Long    valid data length in the buffer (dirty byte count)
9       logPathLen  Int     log file path length
13      logPath     bytes   log file path (UTF-8)
```

- `headerSize(logPathLen) = 1 + 8 + 4 + logPathLen`
- `isAvailable() = data[0] == magic`
- `dataStart() = data + headerSize(logPathLen)`

> Compared with the reference implementation, the `isCompress` field is dropped (v1 does not compress).

### 7.2 Buffer file layout

```
┌──────────────┬──────────────────────────────┐
│  Header      │  Data region (capacity bytes) │
│  (dynamic)   │  ← logLen valid →             │
└──────────────┴──────────────────────────────┘
total file size = capacity + headerSize(logPathLen)
```

---

## 8. Design Trade-offs

| Trade-off | Choice | Rationale |
|---|---|---|
| Pure Kotlin vs JNI/C++ | **Pure Kotlin** | Under KMP, Native can call POSIX directly and JVM uses MappedByteBuffer; this removes all NDK/CMake/JNI complexity |
| Linear vs ring buffer | **Linear** | Simple to implement, no wrap-around edge cases; flushing the whole buffer matches the "sequential append" semantics of logs |
| Mutex vs lock-free | **Mutex** | v1 prioritizes correctness and simplicity; lock-free is deferred |
| Recursive vs plain lock | **Plain lock + private locked methods** | The reference implementation's `recursive_mutex` is a result of design coupling and can be decomposed |
| Behavior when full | **Flush first, then write; truncate + warn if a line > capacity** | The reference implementation truncates silently, which is a defect; the user must know data was dropped |
| mmap failure | **Throw** | The library does not silently degrade to a heap buffer; a failed mapping is a hard error |
| MappedByteBuffer cannot be explicitly unmapped | **Accepted** | With a single buffer and a process-level lifetime there is no practical impact; `release()` = `force()` + drop the reference. iOS does call `munmap` |
| Number of extension points | **Only Formatter + Appender** | Drops the interceptor chain, `LogData` object pool, and the `Logger`/`AppenderLogger` double abstraction |
| Date switching | **`nextSwitchMillis` boundary comparison** | Avoids date formatting on the hot path |
| Durability statement | **Survives kill, not power loss** | States the mmap boundary honestly, without exaggeration |

---

## 9. Module File Structure

```
loga/
├── build.gradle.kts
├── src/
│   ├── commonMain/kotlin/me/xechoz/loga/
│   │   ├── Loga.kt                # facade
│   │   ├── LogConfig.kt           # configuration
│   │   ├── Level.kt               # level constants
│   │   ├── LogBuffer.kt           # linear buffer + header + rotation
│   │   ├── LogBufferHeader.kt     # persistent header read/write
│   │   ├── AsyncFlush.kt          # single-worker async flush
│   │   ├── Worker.kt              # common coroutine worker (FIFO queue)
│   │   ├── FlushBuffer.kt         # self-contained flush task
│   │   ├── LogFileManager.kt      # daily rotation + retention cleanup
│   │   ├── Platform.kt            # expect: MappedBuffer / directory / console / crash hook
│   │   ├── FileSystem.kt          # expect file operations
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
│   │   └── Platform.android.kt    # includes Loga.init(context, config)
│   ├── jvmMain/kotlin/me/xechoz/loga/
│   │   ├── MappedBuffer.jvm.kt
│   │   ├── FileSystem.jvm.kt
│   │   └── Platform.jvm.kt
│   └── iosMain/kotlin/me/xechoz/loga/
│       ├── MappedBuffer.ios.kt
│       ├── FileSystem.ios.kt
│       └── Platform.ios.kt
```
