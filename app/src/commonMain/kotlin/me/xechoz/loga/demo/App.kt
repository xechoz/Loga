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
import org.jetbrains.compose.ui.tooling.preview.Preview

private const val TAG = "demo"

@Composable
fun App() {
    MaterialTheme {
        val memoryAppender = remember { InMemoryAppender() }
        val logDirectory = remember { demoLogDirectory() }

        remember {
            LogConfig(
                logDirectory = logDirectory,
                bufferSize = 400 * 1024,
                level = Level.DEBUG,
                formatter = Formatter { level, tag, message -> "$level/$tag: $message\n" },
                retentionDays = 7,
                isDebug = true,
                appenders = listOf(ConsoleAppender(), memoryAppender),
                logUncaughtExceptions = true,
            ).also { config ->
                Loga.init(config)
                Loga.i(TAG, "$config")
            }
        }

        AppContent(memoryAppender = memoryAppender, logDirectory = logDirectory)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppContent(memoryAppender: InMemoryAppender, logDirectory: String) {
    var tag by remember { mutableStateOf("Demo") }
    var message by remember { mutableStateOf("hello loga") }

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
            item {
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Text("LogConfig example (all parameters)", style = MaterialTheme.typography.titleMedium)
                        Text("logDirectory = $logDirectory", style = MaterialTheme.typography.bodySmall)
                        Text("bufferSize = 400 * 1024", style = MaterialTheme.typography.bodySmall)
                        Text("level = Level.DEBUG", style = MaterialTheme.typography.bodySmall)
                        Text("formatter = custom \"\$level/\$tag: \$message\"", style = MaterialTheme.typography.bodySmall)
                        Text("retentionDays = 7", style = MaterialTheme.typography.bodySmall)
                        Text("isDebug = true", style = MaterialTheme.typography.bodySmall)
                        Text("appenders = [ConsoleAppender, InMemoryAppender]", style = MaterialTheme.typography.bodySmall)
                        Text("logUncaughtExceptions = true", style = MaterialTheme.typography.bodySmall)
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { crashInBackgroundThread() }) { Text("Crash (bg)") }
                        Button(onClick = { crashInMainThread() }) { Text("Crash (main)") }
                    }
                    Text(
                        "A background thread crash does not exit the process; a main thread crash " +
                            "terminates it (i.e. a crash on Android). After restart, the crash log " +
                            "can be found in the log directory.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            item {
                Text(
                    "InMemoryAppender collected (last ${memoryAppender.lines.size} lines)",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(memoryAppender.lines) { line ->
                Text(line, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun AppPreview() {
    MaterialTheme {
        val memoryAppender = remember {
            InMemoryAppender().apply {
                append(Level.INFO, "Demo", "I/Demo: hello loga")
                append(Level.WARN, "Demo", "W/Demo: something looks off")
                append(Level.ERROR, "Demo", "E/Demo: boom")
            }
        }
        AppContent(memoryAppender = memoryAppender, logDirectory = "/tmp/loga-demo")
    }
}
