package com.babytracker.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Shared destructive-action confirmation dialog: an [AlertDialog] with a [title], a body [message],
 * an error-colored confirm [Button], and a plain cancel [TextButton]. Used by every history/list
 * surface so all delete prompts feel identical.
 *
 * The confirm button always carries the error container role. [confirmContentColor] and
 * [dismissContentColor] are optional: when null the Material defaults apply, when set they let a
 * feature pin the confirm label to `onError` or tint the cancel label to its section accent.
 * [confirmEnabled] gates the confirm button while a delete is in flight.
 */
@Composable
fun DeleteConfirmationDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
    confirmContentColor: Color? = null,
    dismissContentColor: Color? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = confirmEnabled,
                colors = if (confirmContentColor != null) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = confirmContentColor,
                    )
                } else {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                },
            ) { Text(confirmText) }
        },
        dismissButton = {
            if (dismissContentColor != null) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = dismissContentColor),
                ) { Text(dismissText) }
            } else {
                TextButton(onClick = onDismiss) { Text(dismissText) }
            }
        },
    )
}
