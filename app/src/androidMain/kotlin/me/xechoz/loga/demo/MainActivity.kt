package me.xechoz.loga.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import me.xechoz.loga.Loga
import me.xechoz.loga.init

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setDemoContext(this)
        Loga.init(this)
        setContent { App() }
    }
}
