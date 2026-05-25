package org.traccar.client

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

fun hasPermission(context: Context, permission: String): Boolean =
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

suspend fun requestPermission(activity: ComponentActivity, permission: String): Boolean =
    suspendCancellableCoroutine { continuation ->
        val key = "traccar_permission_${System.nanoTime()}"
        lateinit var launcher: ActivityResultLauncher<String>
        launcher = activity.activityResultRegistry.register(
            key,
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            launcher.unregister()
            continuation.resume(granted)
        }
        continuation.invokeOnCancellation { launcher.unregister() }
        launcher.launch(permission)
    }
