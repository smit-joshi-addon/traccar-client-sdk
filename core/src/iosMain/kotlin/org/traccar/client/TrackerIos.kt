package org.traccar.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

fun Tracker(config: Config): Tracker = Tracker(
    provider = IosLocationProvider(),
    uploader = HttpUploader(config, HttpClient(Darwin)),
)
