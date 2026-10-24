package com.herbertindustries.alex311

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.herbertindustries.alex311.ui.*

class MainActivity: ComponentActivity() {
    private lateinit var state: AppState
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        state = ViewModelProvider(this)[AppState::class.java]
        if (savedInstanceState == null) intent.dataString?.let(state::handleLink)
        setContent { Alex311Theme { Alex311Root(state) } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); intent.dataString?.let(state::handleLink) }
}
