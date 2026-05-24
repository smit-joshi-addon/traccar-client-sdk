package org.traccar.client

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

fun hasLocationPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

suspend fun requestLocationPermission(activity: ComponentActivity): Boolean =
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
        launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
