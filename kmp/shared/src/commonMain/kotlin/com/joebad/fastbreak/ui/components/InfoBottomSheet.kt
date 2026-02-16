package com.joebad.fastbreak.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * Shared bottom sheet layout used for all info/detail sheets.
 * Matches the TeamSelectorBottomSheet visual style.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoBottomSheet(
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            // Header
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Content area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                content()
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
