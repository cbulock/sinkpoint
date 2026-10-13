package com.cbulock.sinkpoint

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cbulock.sinkpoint.ui.SinkpointApp
import com.cbulock.sinkpoint.ui.SinkpointTheme

class MainActivity : ComponentActivity() {
    private lateinit var model: GameViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = ViewModelProvider(this)[GameViewModel::class.java]
        setContent {
            val ui = model.ui.collectAsStateWithLifecycle().value
            SinkpointTheme(ui.preferences.theme) { SinkpointApp(ui, model) }
        }
    }
    override fun onStart() { super.onStart(); if (::model.isInitialized) model.foreground(true) }
    override fun onStop() { if (::model.isInitialized) model.foreground(false); super.onStop() }
}
