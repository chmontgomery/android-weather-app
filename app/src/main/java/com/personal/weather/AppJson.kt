package com.personal.weather

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/** Shared JSON config: tolerant of the many NWS fields we don't model, and of nulls where we have defaults. */
@OptIn(ExperimentalSerializationApi::class)
val AppJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}
