# Weather — a single-screen NWS forecast for Android

A small, sideloaded Android app that puts the hourly graphs from
[forecast.weather.gov](https://forecast.weather.gov) on one phone screen. It shows the
current temperature and the day's high/low, then four hourly charts for the selected day.
Swipe to see each day the National Weather Service forecasts, usually 7–8 days.

This is a personal project, built for one phone and shared as-is. It isn't on the Play
Store and has no accounts or analytics.

## What it shows

- **Header:** current temperature (or the day's high on future days) with high/low, the
  location, and an ⓘ that shows when the data was last updated and where the forecast came
  from. The ⓘ turns red if a refresh failed. The ⓘ popup also has a **Show °C / Show °F**
  switch, which the app remembers.
- **Temperature & wind chill** (°F, or °C if switched)
- **Surface wind & gusts** (mph)
- **Precipitation potential & sky cover** (%)
- **Rain / snow / freezing rain / sleet likelihood** as bars (slight chance → occasional),
  with forecast amounts (e.g. `0.13"`, `1.5" snow`)
- A dashed "now" line on today's page. Value labels are placed so they never cover a line.

**Location:**
- It defaults to the phone's approximate location.
- Tap the name to search US cities and ZIP codes or Portuguese towns and postcodes.
- Places picked in the last 30 days are listed as recents.
- The last successful forecast is saved, so the app opens instantly and still shows data
  when weather.gov is flaky.

## Specs and constraints

- **Android 13+** (`minSdk 33`, `targetSdk 36`). Tested on a Pixel 9.
- Kotlin, Jetpack Compose (Material 3), OkHttp, kotlinx.serialization, DataStore. No Google
  Play Services. Charts are drawn by hand on Compose `Canvas`.
- **US and Portugal only** (mainland, Madeira and the Azores). Temperatures are °F by default, with a
  °C switch in the ⓘ popup. Wind is always mph and rain/snow always inches. There's no settings screen.
- Permissions: `INTERNET`, `ACCESS_COARSE_LOCATION`.
- Light and dark mode follow the system setting.
- **Debug build, sideload only.** There's no release signing config and it uses a stock icon.

## Data sources

- **US forecast:** the National Weather Service API, [api.weather.gov](https://www.weather.gov/documentation/services-web-api).
  The app uses gridpoint forecast data plus the nearest station's latest observation for
  the current temperature. It's free and keyless; requests send a `User-Agent` as NWS asks.
- **Portugal forecast:** [Open-Meteo](https://open-meteo.com) (free, keyless, CC BY 4.0, credited in the
  app's ⓘ popup). Its hourly rain probability is mapped onto the same Slight Chance / Chance / Likely /
  Occasional levels, and wind chill is computed with the NWS formula, so the charts read the same as for
  US places.
- **Place search:** Esri's [ArcGIS World Geocoder](https://developers.arcgis.com/rest/geocode/),
  the same service behind forecast.weather.gov's search box, limited to the US and Portugal. It also
  names your current location in Portugal. It's used keylessly, and the
  terms for that cover searching; storing results (recents, cached coordinates) is
  normally for authenticated use. That's fine for personal use, but swap in your own key
  or geocoder if you build on this.

## Build and install

You need the Android SDK (platform 36, build-tools, `platform-tools`) and JDK 17+ (21
recommended). The Gradle wrapper is committed.

```bash
export ANDROID_HOME=~/Library/Android/sdk          # or create local.properties with sdk.dir=...
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS; any JDK 17+ works
./gradlew assembleDebug                            # → app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest                        # optional: JVM + Robolectric unit tests
```

Turn on USB debugging on the phone (Settings → About phone → tap Build number 7 times, then
Developer options → USB debugging), connect it, and install:

```bash
$ANDROID_HOME/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The app appears as **Weather**. On first launch it asks for approximate location; if you
deny it, you can still search for a place.

## License

[MIT](LICENSE)
