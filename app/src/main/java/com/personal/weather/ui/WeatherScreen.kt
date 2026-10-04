package com.personal.weather.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.weather.forecast.DayForecast
import com.personal.weather.forecast.Forecast
import com.personal.weather.location.Place
import com.personal.weather.location.PlaceSuggestion
import com.personal.weather.ui.charts.ChartMath
import com.personal.weather.ui.charts.DualLineChart
import com.personal.weather.ui.charts.LikelihoodBarChart
import com.personal.weather.ui.charts.LineSeries
import com.personal.weather.ui.theme.LocalChartColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    state: WeatherUiState,
    onRefresh: () -> Unit,
    onClockTick: () -> Unit,
    onSelectDay: (Int) -> Unit,
    onOpenSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onPickPlace: (Place) -> Unit,
    onPickSuggestion: (PlaceSuggestion) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onCloseSearch: () -> Unit,
    onToggleTempUnit: () -> Unit,
) {
    // Ticks once a minute so the now-line and "today" stay current while the screen is open.
    val now by produceState(Instant.now()) {
        while (true) {
            delay(60_000)
            val previous = value
            value = Instant.now()
            // Day 0 must roll over at midnight even if the app stays open.
            if (LocalDate.ofInstant(value, ZoneId.systemDefault()) != LocalDate.ofInstant(previous, ZoneId.systemDefault())) {
                onClockTick()
            }
        }
    }
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            PullToRefreshBox(
                isRefreshing = state.loading && state.forecast != null,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    // Scrollable-but-exactly-screen-height column: lets pull-to-refresh see the drag
                    // while the charts still fill the screen with weights.
                    Column(Modifier.verticalScroll(rememberScrollState()).height(maxHeight).fillMaxWidth()) {
                        val forecast = state.forecast
                        when {
                            forecast != null && forecast.days.isNotEmpty() ->
                                ForecastContent(state, forecast, now, onSelectDay, onOpenSearch, onToggleTempUnit)
                            else -> {
                                Header(
                                    state.headerName, statusText(state, now), state.refreshFailed, state.loading,
                                    null, null, null, state.tempUnit, onOpenSearch, onToggleTempUnit,
                                )
                                CenterMessage(state, onRefresh)
                            }
                        }
                    }
                }
            }
            if (state.search.open) {
                LocationSearchSheet(state.search, onQueryChange, onPickPlace, onPickSuggestion, onUseCurrentLocation, onCloseSearch)
            }
        }
    }
}

@Composable
private fun ColumnScope.ForecastContent(
    state: WeatherUiState,
    forecast: Forecast,
    now: Instant,
    onSelectDay: (Int) -> Unit,
    onOpenSearch: () -> Unit,
    onToggleTempUnit: () -> Unit,
) {
    val days = forecast.days
    val pagerState = rememberPagerState(initialPage = state.selectedDay.coerceIn(days.indices)) { days.size }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { onSelectDay(it) }
    }
    LaunchedEffect(state.selectedDay, days.size) {
        val target = state.selectedDay.coerceIn(days.indices)
        if (pagerState.currentPage != target) pagerState.animateScrollToPage(target)
    }

    val current = pagerState.currentPage.coerceIn(days.indices)
    val day = days[current]
    val today = forecast.isToday(day, now)
    Header(
        placeName = state.headerName,
        status = statusText(state, now),
        statusIsWarning = state.refreshFailed,
        loading = state.loading,
        bigTemp = Formatting.temp(if (today) forecast.currentTempF else day.highF, state.tempUnit) + state.tempUnit.name,
        high = if (today) Formatting.temp(day.highF, state.tempUnit) else null,
        low = Formatting.temp(day.lowF, state.tempUnit),
        tempUnit = state.tempUnit,
        onPlaceClick = onOpenSearch,
        onToggleTempUnit = onToggleTempUnit,
    )
    DayTabs(days, current) { index -> scope.launch { pagerState.animateScrollToPage(index) } }
    HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
        DayPage(days[page], forecast.timeZone, now, state.tempUnit, Modifier.fillMaxSize())
    }
}

private fun statusText(state: WeatherUiState, now: Instant): String? {
    val snapshot = state.snapshot
    val zone = ZoneId.systemDefault()
    return when {
        snapshot != null && state.refreshFailed -> Formatting.refreshFailedLabel(snapshot.fetchedAt, zone) + " · Forecast: " + Formatting.sourceCredit(snapshot)
        snapshot != null -> Formatting.updatedLabel(snapshot.fetchedAt, now, zone) + " · Forecast: " + Formatting.sourceCredit(snapshot)
        else -> null
    }
}

/**
 * One row: big temperature and high/low on the left; on the right an ⓘ (tap for "Updated …" / refresh
 * warning, red while a refresh has failed) with the tappable location name beneath it.
 */
@Composable
private fun Header(
    placeName: String,
    status: String?,
    statusIsWarning: Boolean,
    loading: Boolean,
    bigTemp: String?,
    high: String?,
    low: String?,
    tempUnit: TempUnit,
    onPlaceClick: () -> Unit,
    onToggleTempUnit: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp)) {
        if (bigTemp != null) {
            Text(bigTemp, fontSize = 64.sp, lineHeight = 68.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.padding(top = 10.dp)) {
                Text(high ?: "", fontSize = 22.sp)
                Text(low ?: "", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            StatusInfo(status, statusIsWarning, loading, tempUnit, onToggleTempUnit)
            Row(
                Modifier.clickable(onClick = onPlaceClick).padding(start = 8.dp, end = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    placeName,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Change location")
            }
        }
    }
}

/** The ⓘ button and its popup (status line, then the rarely used °F/°C switch); a spinner sits beside it while loading. */
@Composable
private fun StatusInfo(status: String?, isWarning: Boolean, loading: Boolean, tempUnit: TempUnit, onToggleTempUnit: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Box {
            IconButton(onClick = { open = true }, enabled = status != null) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = if (isWarning) "Couldn't refresh" else "Last updated",
                    tint = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = open && status != null, onDismissRequest = { open = false }) {
                Text(
                    status ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                DropdownMenuItem(
                    text = { Text("Show ${tempUnit.other().symbol}") },
                    onClick = {
                        open = false
                        onToggleTempUnit()
                    },
                )
            }
        }
    }
}

@Composable
private fun DayTabs(days: List<DayForecast>, selected: Int, onClick: (Int) -> Unit) {
    val tabColor = LocalChartColors.current.dayTab
    ScrollableTabRow(selectedTabIndex = selected, edgePadding = 8.dp, indicator = {}, divider = {}) {
        days.forEachIndexed { i, day ->
            Tab(
                selected = i == selected,
                onClick = { onClick(i) },
                selectedContentColor = tabColor,
                unselectedContentColor = tabColor,
            ) {
                val outline = if (i == selected) {
                    Modifier.border(1.5.dp, LocalContentColor.current, RoundedCornerShape(50))
                } else {
                    Modifier
                }
                Text(
                    Formatting.tabLabel(day.date),
                    modifier = Modifier.padding(vertical = 4.dp).then(outline).padding(horizontal = 12.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun DayPage(day: DayForecast, zone: ZoneId, now: Instant, unit: TempUnit, modifier: Modifier = Modifier) {
    val colors = LocalChartColors.current
    val nowFraction = if (now >= day.start && now < day.end) ChartMath.xFraction(now, day.start, day.end) else null
    val degrees = { v: Double -> "${v.roundToInt()}°" }
    val percent = { v: Double -> "${v.roundToInt()}%" }
    val mph = { v: Double -> "${v.roundToInt()}" }
    val wind = ChartMath.windAxis(day.hours.flatMap { listOf(it.windMph, it.gustMph) })
    val temps = day.hours.map { h -> h.tempF?.let(unit::fromF) }
    val chills = day.hours.map { h -> h.windChillF?.let(unit::fromF) }
    val tempAxis = ChartMath.temperatureAxis(temps + chills, celsius = unit == TempUnit.C)
    Column(modifier.padding(horizontal = 4.dp)) {
        DualLineChart(
            day = day,
            zone = zone,
            series = listOf(
                LineSeries("Temperature", colors.temperature, temps) { i ->
                    day.hours[i].let { h -> h.tempF != null && Formatting.tempCoveredByWindChill(h.tempF, h.windChillF, unit) }
                },
                LineSeries("Wind chill", colors.windChill, chills),
            ),
            range = tempAxis.range,
            gridStep = tempAxis.step,
            axisLabel = degrees,
            valueLabel = degrees,
            nowFraction = nowFraction,
            modifier = Modifier.weight(0.30f),
        )
        DualLineChart(
            day = day,
            zone = zone,
            series = listOf(
                LineSeries("Wind (mph)", colors.wind, day.hours.map { it.windMph }),
                LineSeries("Gusts (mph)", colors.gust, day.hours.map { it.gustMph }),
            ),
            range = wind.range,
            gridStep = wind.step,
            axisLabel = mph,
            valueLabel = mph,
            nowFraction = nowFraction,
            modifier = Modifier.weight(0.24f),
        )
        DualLineChart(
            day = day,
            zone = zone,
            series = listOf(
                LineSeries("Precip potential", colors.precipPotential, day.hours.map { it.popPct?.toDouble() }),
                LineSeries("Sky cover", colors.skyCover, day.hours.map { it.skyPct?.toDouble() }),
            ),
            range = ChartMath.PERCENT_RANGE,
            gridStep = 20.0,
            axisLabel = percent,
            valueLabel = Formatting::percentLabel,
            nowFraction = nowFraction,
            modifier = Modifier.weight(0.26f),
        )
        LikelihoodBarChart(day = day, zone = zone, nowFraction = nowFraction, modifier = Modifier.weight(0.20f))
    }
}

@Composable
private fun ColumnScope.CenterMessage(state: WeatherUiState, onRetry: () -> Unit) {
    Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        when {
            state.loading -> CircularProgressIndicator()
            state.fatalError != null -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    when (state.fatalError) {
                        ErrorKind.OUTSIDE_COVERAGE -> "Only US and Portugal locations are supported."
                        ErrorKind.NETWORK -> "Couldn't reach the forecast service."
                    },
                    textAlign = TextAlign.Center,
                )
                if (state.fatalError == ErrorKind.NETWORK) Button(onClick = onRetry) { Text("Retry") }
            }
            else -> Text("Tap the location name to choose a place.", textAlign = TextAlign.Center)
        }
    }
}
