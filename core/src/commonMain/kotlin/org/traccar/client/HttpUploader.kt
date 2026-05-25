package org.traccar.client

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.round

class HttpUploader(
    private val config: Config,
    private val httpClient: HttpClient,
) : Uploader {

    override suspend fun upload(position: Position): Boolean = try {
        val response: HttpResponse = httpClient.get(config.serverUrl) {
            parameter("id", config.deviceId)
            parameter("lat", position.latitude)
            parameter("lon", position.longitude)
            parameter("timestamp", position.time / 1000)
            parameter("accuracy", position.accuracy)
            position.altitude?.let { parameter("altitude", it) }
            position.speed?.let { parameter("speed", it.toKnots()) }
            position.bearing?.let { parameter("bearing", it) }
            position.battery?.let { parameter("batt", it) }
        }
        response.status.isSuccess()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        false
    }

    private fun Double.toKnots(): Double = round(this * 1.94384 * 100) / 100
}
