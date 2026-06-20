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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.babytracker.R
import com.babytracker.sharing.domain.model.DoctorVisitSnapshot
import com.babytracker.ui.theme.doctorVisitColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val visitDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

/** Read-only partner view of shared doctor visits (questions are private and never synced). */
@Composable
fun PartnerDoctorVisitCard(
    visits: List<DoctorVisitSnapshot>,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val colors = doctorVisitColors()
    // Soonest upcoming first, then most recent past — capped so the card stays compact.
    val ordered = visits.sortedBy { it.date }
    val now = Instant.now().toEpochMilli()
    val upcoming = ordered.filter { it.date > now }
    val past = ordered.filter { it.date <= now }.sortedByDescending { it.date }
    val shown = (upcoming + past).take(MAX_ROWS)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.container),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.partner_doctor_visits_heading),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onContainer,
            )
            Spacer(Modifier.height(4.dp))
            if (shown.isEmpty()) {
                Text(
                    text = stringResource(R.string.partner_doctor_visits_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onContainer,
                )
            } else {
                shown.forEach { visit ->
                    val dateLabel = visitDateFormatter.format(
                        Instant.ofEpochMilli(visit.date).atZone(zone).toLocalDate(),
                    )
                    val line = visit.providerName?.takeIf { it.isNotBlank() }
                        ?.let { "$dateLabel · $it" } ?: dateLabel
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onContainer,
                    )
                }
            }
        }
    }
}

private const val MAX_ROWS = 3
