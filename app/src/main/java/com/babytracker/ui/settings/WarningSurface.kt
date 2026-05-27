package com.babytracker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.babytracker.ui.theme.LocalDarkTheme
import com.babytracker.ui.theme.OnWarningContainerAmber
import com.babytracker.ui.theme.OnWarningContainerAmberDark
import com.babytracker.ui.theme.WarningAmber
import com.babytracker.ui.theme.WarningAmberDark
import com.babytracker.ui.theme.WarningContainerAmber
import com.babytracker.ui.theme.WarningContainerAmberDark

@Composable
internal fun WarningSurface(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val isDark = LocalDarkTheme.current
    val bg = if (isDark) WarningContainerAmberDark else WarningContainerAmber
    val contentColor = if (isDark) OnWarningContainerAmberDark else OnWarningContainerAmber
    val iconTint = if (isDark) WarningAmberDark else WarningAmber

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(bg, MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = if (subtitle != null) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            TextButton(
                onClick = onAction,
                colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
            ) {
                Text(actionLabel)
            }
        }
    }
}
