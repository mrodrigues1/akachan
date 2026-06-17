package com.babytracker.ui.partner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.babytracker.sharing.domain.model.DiaperSnapshot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val diaperTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

@Composable
fun PartnerDiaperCard(
    diapers: List<DiaperSnapshot>,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val today = Instant.now().atZone(zone).toLocalDate()
    val todayCount = diapers.count { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() == today }
    val last = diapers.maxByOrNull { it.timestamp }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "🧷 Diapers",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (todayCount == 1) "1 change today" else "$todayCount changes today",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            last?.let {
                val time = diaperTimeFormatter.format(
                    Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalTime(),
                )
                Text(
                    text = "Last: ${it.type.lowercase().replaceFirstChar(Char::uppercase)} at $time",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}
