# loga Tech Stack

[English](tech-stack.md) | [中文](tech-stack.zh-CN.md)

> Goal: build a cross-platform logging library with Kotlin Multiplatform + mmap.
> This document lists the technologies worth understanding in depth, marks each
> as **used in v1** / **later**, and explains "why it is needed".
>
> Reference implementations: [Log4a](https://github.com/pqpo/Log4a) (Apache 2.0),
> [Tencent mars/xlog](https://github.com/Tencent/mars),
> [Meituan Logan](https://github.com/Meituan-Dianping/Logan).

---

## 0. Target Platforms

| Platform | Target | v1 |
|---|---|---|
| Android | `androidTarget` | ✅ |
| JVM (desktop) | `jvm` | ✅ |
| iOS | `iosArm64` / `iosSimulatorArm64` / `iosX64` | ✅ |
| Windows | `mingwX64` | later |
| JS / Wasm | — | ❌ no mmap, explicitly excluded |

---

## 1. mmap Fundamentals — used in v1

| Topic | Why it is needed |
|---|---|
| `mmap` / `FileChannel.map` | The core of log writing: map a file into the process address space so writes go to memory, avoiding a `write` syscall and a kernel copy per line |
| Page cache, page faults | Understand why "writing to memory" eventually reaches disk; the first write triggers a fault that allocates a physical page |
| File preallocation (`ftruncate` / `RandomAccessFile.setLength`) | The file size must be fixed before mapping, otherwise an out-of-bounds access raises `SIGBUS` |
| `msync` / `MappedByteBuffer.force()` | Explicitly write dirty pages back to disk; without it you rely on the kernel writeback thread |
| mmap vs write | mmap saves one user→kernel copy, which is the source of its performance advantage |

**Boundary statement that must be documented**: mmap guarantees "a process kill (`kill -9`) does not lose logs" (pages are still managed by the kernel and will be written back); it does **not** guarantee "no loss on power failure" (that depends on the kernel's dirty-page writeback timing, unless `msync`/`force()` is called). This is the honest boundary of the mmap approach.

---

## 2. Linear Buffer and Concurrency Control — used in v1

| Topic | Why it is needed |
|---|---|
| Linear buffer (append at `dataStart + logLen`) | The reference implementation Log4a uses a **linear buffer rather than a ring buffer**: sequential append, flush and clear when full. Simple to implement, no wrap-around edge cases |
| Full-buffer policy | A line that does not fit → flush first, then write; a line larger than the whole buffer → truncate and warn (the reference implementation truncates silently, which is a defect) |
| `kotlinx.atomicfu.locks.synchronized` | Protects the buffer's read/write pointers; v1 uses a plain lock and does not introduce lock-free code |
| Non-recursive lock decomposition | The reference implementation uses `recursive_mutex` (because `changeLogPath` calls `asyncFlush` while holding the lock). The new library splits the internals into "private methods that assume the lock is held" and uses a plain lock |
| Memory barriers / atomics | **Later**: only needed when going lock-free |

---

## 3. Platform mmap Access (expect/actual) — used in v1

Under KMP, mmap is dispatched per platform and collapsed into a single `MappedBuffer` abstraction:

| Platform | Implementation | Notes |
|---|---|---|
| Android / JVM | `FileChannel.map()` → `MappedByteBuffer` | **Pure Kotlin, no NDK/JNI**. `put`/`get` take the fast JNI path; `force()` is `msync` |
| iOS | `platform.posix.mmap()` | Kotlin/Native calls POSIX directly, **no C++ needed**. Requires `@OptIn(ExperimentalForeignApi::class)` and pointer pinning |

A factory `openMappedBuffer(path, size)` hides the platform construction (open + preallocate + map).

**MappedByteBuffer boundary**: on the JVM the mapping cannot be explicitly `unmap`ped (it is managed by the GC), so `release()` can only `force()` and drop the reference. With a single buffer and a process-level lifetime there is no practical impact; crash durability is unaffected (the kernel still writes back dirty pages on `kill -9`). iOS does call `munmap`.

**Kotlin/Native pointer safety**: mmap returns a `CPointer<ByteVar>`, which must be used carefully across threads. Strategy: `memcpy` inside the lock when writing, and copy the data snapshot into a `ByteArray` before handing it to the background thread when flushing, so raw pointers never cross threads.

---

## 4. expect/actual Mechanism — used in v1

Platform differences collapse into a few points:

| Abstraction | Android | JVM | iOS |
|---|---|---|---|
| `MappedBuffer` | MappedByteBuffer | MappedByteBuffer | posix.mmap |
| Default log directory | `getExternalFilesDir("logs")/logs` (falls back to `filesDir`) | `user.home/logs` | `NSDocumentDirectory/logs` |
| Console output | `android.util.Log` (logcat) | `println` | `NSLog` |
| Uncaught exception hook | `Thread.setDefaultUncaughtExceptionHandler` | `Thread.setDefaultUncaughtExceptionHandler` | no-op |

---

## 5. Coroutines and Async Flush — used in v1

| Topic | Why it is needed |
|---|---|
| `kotlinx-coroutines-core` | Cross-platform threading abstraction; the worker runs on `Dispatchers.Default` |
| Single worker + task queue | The logging thread only takes a memcpy snapshot; disk I/O is delegated to a background thread so the caller is never blocked. `Worker` uses a `Channel(UNLIMITED)` and processes tasks in FIFO order |
| Self-contained flush task (`FlushBuffer`) | The task owns the target path and a data copy, so it stays valid after the buffer object is released |
| `submitAndWait` / `shutdown` | `flush()` blocks until everything submitted so far is written; `shutdown()` closes the queue and joins the worker, so no data is lost on release |
| partial write / interruption handling | `write` may write only part of the data or be interrupted by a signal, so it must be looped to completion |

---

## 6. Dates and File Management — used in v1

| Topic | Why it is needed |
|---|---|
| `kotlinx-datetime` + `kotlin.time.Clock` | Cross-platform date computation for `yyyy_MM_dd.txt` naming and day boundaries |
| Daily rotation | Log files are named `yyyy_MM_dd.txt` and switch automatically across days |
| Date boundary comparison | Cache `nextSwitchMillis`; each write only does one `now >= nextSwitchMillis` comparison, avoiding date formatting per line (hot-path cost) |
| Log retention cleanup | Delete files older than `retentionDays` (7 by default); runs on init and on every rotation |
| Process lifecycle | Flush and release strategy when the process is killed or `onTrimMemory` fires |

---

## 7. Serialization / Compression / Encryption — later

| Topic | Notes |
|---|---|
| Binary serialization (protobuf / flatbuffers) | Structured logs, a more compact storage format |
| Compression (zlib / zstd / snappy) | Log4a uses zlib raw-deflate; xlog uses zlib. v1 does not, to avoid coupling compression into the hot path |
| Encryption (AES) | xlog supports encryption; v1 does not |

---

## 8. Multi-process / Lock-free — later

| Topic | Notes |
|---|---|
| Multi-process shared mmap | Concurrency control when several processes write the same log file |
| Lock-free queue / CAS / cache-line alignment | Performance optimization under high concurrency; v1 uses a plain lock |

---

## 9. Reference Comparison

| Solution | Language | Characteristics | Value to this project |
|---|---|---|---|
| **Log4a** | Kotlin + C++ | Linear mmap buffer, async flush, startup recovery; clear structure, small footprint | **Borrows the engine design** (linear buffer, dirty-tail recovery, self-contained snapshot) without copying its C++ implementation; fixes its defects (silent truncation, no automatic rotation, `recursive_mutex`, no warning on heap fallback) |
| **xlog (mars)** | C++ | The most mature; mmap + zlib + AES, multi-process | Reference for compression/encryption/multi-process later |
| **Logan** | Java/Kotlin | Simple protocol, single-writer thread model | Reference for its streaming protocol and thread model |

### Log4a measured performance (README, Google Pixel, 10k log lines)

| Solution | Time |
|---|---|
| Pure memory | 13 ms |
| **Log4a** | **50 ms** |
| File with Buffer | 61 ms |
| Android Log | 184 ms |
| File no Buffer | 272 ms |

Log4a does not lose logs on power loss / kill (recovered on the next startup).

---

## v1 Tech Stack Summary

**Must understand**: mmap fundamentals and boundaries, linear buffer + plain lock, expect/actual platform access (MappedByteBuffer / posix.mmap), startup dirty-tail recovery, coroutine single-worker async flush, kotlinx-datetime date management and daily rotation.

**Explicitly out of scope**: compression, encryption, multi-process, lock-free, object pools, direct `ByteBuffer` optimization, Windows target.
