package com.personal.weather.ui

/** How temperatures are shown. Forecasts stay in °F internally; this converts only for display. */
enum class TempUnit {
    F,
    C;

    fun fromF(f: Double): Double = if (this == F) f else (f - 32.0) * 5.0 / 9.0

    val symbol: String get() = "°$name"

    fun other(): TempUnit = if (this == F) C else F
}
