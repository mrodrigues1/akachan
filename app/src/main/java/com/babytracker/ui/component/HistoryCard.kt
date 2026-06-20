package com.babytracker.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.babytracker.ui.theme.LocalDarkTheme

@Composable
fun HistoryCard(
    title: String,
    subtitle: String,
    trailing: String,
    badgeColor: Color,
    modifier: Modifier = Modifier,
    badgeEmoji: String = "",
    trailingColor: Color = MaterialTheme.colorScheme.primary,
    trailingIcon: ImageVector? = null,
    trailingIconDescription: String? = null,
    badgeContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val isDark = LocalDarkTheme.current
    val rowModifier = Modifier
        .fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
        .padding(horizontal = 14.dp, vertical = 12.dp)
        .semantics(mergeDescendants = true) {
            if (onClick != null) role = Role.Button
        }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
    ) {
        Row(
            modifier = rowModifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = badgeColor,
                        shape = MaterialTheme.shapes.small,
                ),
                contentAlignment = Alignment.Center,
            ) {
                if (badgeContent != null) {
                    badgeContent()
                } else {
                    Text(text = badgeEmoji, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp))
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = trailing,
                style = MaterialTheme.typography.bodyMedium,
                color = trailingColor,
            )

            when {
                trailingContent != null -> trailingContent()
                trailingIcon != null -> {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = trailingIcon,
                        contentDescription = trailingIconDescription,
                        tint = trailingColor,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
