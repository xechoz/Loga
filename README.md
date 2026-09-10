# loga

A Kotlin Multiplatform logging library backed by `mmap`.

Log lines are written into a memory-mapped file, so writes are cheap and survive
a process kill: dirty pages stay in the kernel page cache and are flushed to
disk. A background worker copies snapshots out of the mapping and appends them
to daily log files.

## Platforms

| Platform | Target | mmap implementation |
|---|---|---|
| Android | `androidTarget` | `MappedByteBuffer` |
| JVM (desktop) | `jvm` | `MappedByteBuffer` |
| iOS | `iosArm64` / `iosSimulatorArm64` / `iosX64` | `platform.posix.mmap` |

## Usage

```kotlin
// Android
Loga.init(context, LogConfig(retentionDays = 7))

// JVM / iOS
Loga.init(LogConfig(logDirectory = "/path/to/logs"))

Loga.i("Network", "request finished")
Loga.e("Network", "request failed")
Loga.flush()
```

### Configuration

```kotlin
LogConfig(
    logDirectory = null,          // defaults per platform
    bufferSize = 400 * 1024,      // mmap buffer size
    level = Level.DEBUG,          // minimum level
    formatter = DefaultFormatter, // "L/TAG: msg\n"
    retentionDays = 7,            // delete files older than this
    appenders = null,             // defaults to [FileAppender, ConsoleAppender]
)
```

### Extension points

- `Formatter` — customize the log line format.
- `Appender` — customize output targets (default: mmap file + platform console).

## Design

See [`docs/architecture.md`](docs/architecture.md) for the full design and
[`docs/tech-stack.md`](docs/tech-stack.md) for the technology overview.

Key properties:

- **Linear buffer**: lines append at `dataStart + logLen`; when full the buffer
  is flushed and cleared. A line larger than the whole buffer is truncated with
  a warning.
- **Async flush**: a single worker drains self-contained flush tasks in FIFO
  order, so the logging thread never blocks on disk I/O.
- **Crash recovery**: the buffer file starts with a persistent header
  (`magic | logLen | logPathLen | logPath`). On startup the dirty tail left by a
  previous process is written back before the mapping is rebuilt.
- **Daily rotation**: files are named `yyyy_MM_dd.txt`; rotation is driven by a
  cached day boundary so the hot path only does one comparison.
- **Retention**: files older than `retentionDays` are deleted on init and on
  each rotation.

### Durability

- **Guaranteed**: a process kill (`kill -9`) does not lose logs.
- **Not guaranteed**: power loss, unless `force()` / `msync` is called.

## Building

```bash
./gradlew :loga:jvmTest
./gradlew :loga:compileKotlinIosArm64
```

The Android target requires an installed Android SDK platform (API 36) with
accepted licenses. If the system SDK is not writable, install a local one:

```bash
sdkmanager --sdk_root=.android-sdk "platforms;android-36" "build-tools;36.0.0"
ANDROID_HOME=$PWD/.android-sdk ./gradlew :loga:assemble
```

`.android-sdk/` is git-ignored.

## References

- [Log4a](https://github.com/pqpo/Log4a) — mmap linear buffer, async flush and
  startup recovery; the engine design this project draws from (Apache 2.0).
- [Tencent mars/xlog](https://github.com/Tencent/mars) — the most mature mmap
  logging solution; reference for future compression, encryption and
  multi-process support.
- [Meituan Logan](https://github.com/Meituan-Dianping/Logan) — reference for its
  streaming protocol and single-writer thread model.
