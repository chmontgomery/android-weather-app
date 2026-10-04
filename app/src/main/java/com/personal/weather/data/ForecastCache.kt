package com.personal.weather.data

import com.personal.weather.AppJson
import com.personal.weather.forecast.ForecastSnapshot
import java.io.File
import kotlinx.serialization.Serializable

/** The last successful fetch, kept on disk so a flaky API or a cold start still has something to show. */
class ForecastCache(private val file: File) {
    @Serializable
    private data class Envelope(val version: Int, val snapshot: ForecastSnapshot)

    fun save(snapshot: ForecastSnapshot) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(AppJson.encodeToString(Envelope.serializer(), Envelope(SCHEMA_VERSION, snapshot)))
        tmp.renameTo(file) // atomic replace, so a crash mid-write never leaves a half file
    }

    /** Null when missing, unreadable, or written by a different schema version. */
    fun load(): ForecastSnapshot? = try {
        val envelope = AppJson.decodeFromString(Envelope.serializer(), file.readText())
        envelope.snapshot.takeIf { envelope.version == SCHEMA_VERSION }
    } catch (e: Exception) {
        null
    }

    companion object {
        const val SCHEMA_VERSION = 2
    }
}
