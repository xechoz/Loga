package me.xechoz.loga.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.xechoz.loga.Level
import me.xechoz.loga.Loga
import me.xechoz.loga.LogConfig
import me.xechoz.loga.appender.ConsoleAppender
import me.xechoz.loga.formatter.Formatter

private data class Preset(
    val name: String,
    val description: String,
    val build: (InMemoryAppender) -> LogConfig,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    MaterialTheme {
        val memoryAppender = remember { InMemoryAppender() }
        var selectedPreset by remember { mutableStateOf(0) }
        var tag by remember { mutableStateOf("Demo") }
        var message by remember { mutableStateOf("hello loga") }
        var activeConfig by remember { mutableStateOf("Default") }

        val presets = remember {
            listOf(
                Preset("Default", "LogConfig() 默认配置") { LogConfig() },
                Preset("WARN only", "level = Level.WARN") { LogConfig(level = Level.WARN) },
                Preset("Disable", "level = Level.DISABLE 全静默") { LogConfig(level = Level.DISABLE) },
                Preset("Formatter", "自定义 Formatter，无级别前缀") {
                    LogConfig(formatter = Formatter { _, t, m -> "[$t] $m\n" })
                },
                Preset("Appender", "文件 + InMemoryAppender（追加）") {
                    LogConfig(appenders = listOf(memoryAppender))
                },
                Preset("Console+Mem", "ConsoleAppender + InMemoryAppender") {
                    LogConfig(appenders = listOf(ConsoleAppender(), memoryAppender))
                },
                Preset("Small buffer", "bufferSize = 4KB，频繁刷盘") {
                    LogConfig(bufferSize = 4 * 1024)
                },
                Preset("Custom dir", "logDirectory = demoLogDirectory()") {
                    LogConfig(logDirectory = demoLogDirectory())
                },
            )
        }

        fun applyPreset(index: Int) {
            selectedPreset = index
            val preset = presets[index]
            Loga.init(preset.build(memoryAppender))
            activeConfig = preset.name
        }

        Scaffold(
            topBar = { TopAppBar(title = { Text("loga demo") }) },
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Text("配置预设（点击即 Loga.init 重配）", style = MaterialTheme.typography.titleMedium) }
                presets.forEachIndexed { index, preset ->
                    item(key = "preset-$index") {
                        FilterChip(
                            selected = selectedPreset == index,
                            onClick = { applyPreset(index) },
                            label = { Text("${preset.name} — ${preset.description}") },
                        )
                    }
                }

                item {
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Text("当前配置: $activeConfig", style = MaterialTheme.typography.bodyMedium)
                            Text("日志目录: ${demoLogDirectory()}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = tag,
                        onValueChange = { tag = it },
                        label = { Text("tag") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("message") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { Loga.v(tag, message) }) { Text("V") }
                        Button(onClick = { Loga.d(tag, message) }) { Text("D") }
                        Button(onClick = { Loga.i(tag, message) }) { Text("I") }
                        Button(onClick = { Loga.w(tag, message) }) { Text("W") }
                        Button(onClick = { Loga.e(tag, message) }) { Text("E") }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { repeat(10_000) { Loga.i(tag, "benchmark #$it") } }) {
                            Text("写 10000 条")
                        }
                        Button(onClick = { Loga.flush() }) { Text("Flush") }
                    }
                }

                if (selectedPreset == 4 || selectedPreset == 5) {
                    item {
                        Text(
                            "InMemoryAppender 收集（最近 ${memoryAppender.lines.size} 行）",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    items(memoryAppender.lines) { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}


