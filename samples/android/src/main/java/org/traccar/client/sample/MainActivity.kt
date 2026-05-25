package org.traccar.client.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.traccar.client.Config
import org.traccar.client.Tracker
import org.traccar.client.createTracker

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                TrackerScreen()
            }
        }
    }
}

@Composable
private fun TrackerScreen() {
    val activity = LocalActivity.current as ComponentActivity
    val scope = rememberCoroutineScope()
    var serverUrl by remember { mutableStateOf("https://demo.traccar.org/") }
    var deviceId by remember { mutableStateOf("123456") }
    var tracker: Tracker? by remember { mutableStateOf(null) }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Server URL") },
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = deviceId,
                onValueChange = { deviceId = it },
                label = { Text("Device ID") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val current = tracker
                    if (current != null) {
                        current.stop()
                        tracker = null
                    } else {
                        scope.launch {
                            val newTracker = createTracker(activity, Config(serverUrl, deviceId))
                            if (newTracker.start()) {
                                tracker = newTracker
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (tracker != null) "Stop" else "Start")
            }
        }
    }
}
