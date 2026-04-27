package com.babytracker.ui.sharing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ConnectPartnerScreen(onNavigateBack: () -> Unit) {
    BackHandler(onBack = onNavigateBack)
    Box(modifier = Modifier.fillMaxSize())
}
