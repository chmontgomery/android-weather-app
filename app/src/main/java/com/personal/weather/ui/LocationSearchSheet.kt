package com.personal.weather.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.personal.weather.location.Place
import com.personal.weather.location.PlaceSuggestion

/** Full-screen place picker: current location, recent places (filtered by the query), then geocoder suggestions. */
@Composable
fun LocationSearchSheet(
    search: SearchState,
    onQueryChange: (String) -> Unit,
    onPick: (Place) -> Unit,
    onPickSuggestion: (PlaceSuggestion) -> Unit,
    onCurrentLocation: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val query = search.query.trim()
    val recents = if (query.isEmpty()) search.recents else search.recents.filter { it.name.contains(query, ignoreCase = true) }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = search.query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("City or ZIP") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                TextButton(onClick = onClose) { Text("Cancel") }
            }
            search.note?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            if (search.resolving) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            LazyColumn(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                item { PlaceRow("📍 Current location", onCurrentLocation) }
                if (recents.isNotEmpty()) {
                    item { SectionLabel("Recent") }
                    items(recents, key = { "recent-" + it.name }) { place -> PlaceRow(place.name) { onPick(place) } }
                }
                if (search.results.isNotEmpty()) {
                    item { SectionLabel("Results") }
                    items(search.results, key = { "result-" + it.label }) { suggestion -> PlaceRow(suggestion.label) { onPickSuggestion(suggestion) } }
                }
                search.message?.let { message ->
                    item { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
    )
    HorizontalDivider()
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}
