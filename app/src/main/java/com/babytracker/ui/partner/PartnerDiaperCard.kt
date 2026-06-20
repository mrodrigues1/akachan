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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.babytracker.R
import com.babytracker.domain.model.toDiaperTypeSafe
import com.babytracker.sharing.domain.model.DiaperSnapshot
import com.babytracker.ui.component.labelRes
import com.babytracker.ui.theme.diaperColors
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
    val (todayCount, last) = remember(diapers) {
        val today = Instant.now().atZone(zone).toLocalDate()
        val count = diapers.count { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() == today }
        count to diapers.maxByOrNull { it.timestamp }
    }
    val diaper = diaperColors()
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = diaper.container),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.partner_diaper_heading),
                style = MaterialTheme.typography.titleMedium,
                color = diaper.onContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = pluralStringResource(
                    R.plurals.partner_diaper_count_today,
                    todayCount,
                    todayCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = diaper.onContainer,
            )
            last?.let {
                val time = diaperTimeFormatter.format(
                    Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalTime(),
                )
                val typeLabel = stringResource(it.type.toDiaperTypeSafe().labelRes())
                Text(
                    text = stringResource(R.string.partner_diaper_last, typeLabel, time),
                    style = MaterialTheme.typography.bodySmall,
                    color = diaper.onContainer,
                )
            }
        }
    }
}
