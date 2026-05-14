package com.joebad.fastbreak.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Reusable share floating action button with consistent styling across the app.
 * Uses theme colors: primaryContainer background with primary (first color) icon.
 */
@Composable
fun ShareFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = CircleShape,
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Filled.Share,
            contentDescription = "Share",
            modifier = Modifier.size(24.dp)
        )
    }
}
