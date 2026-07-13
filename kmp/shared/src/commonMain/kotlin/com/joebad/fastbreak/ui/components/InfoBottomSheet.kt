package com.joebad.fastbreak.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * Shared bottom sheet layout used for all info/detail sheets.
 * Matches the TeamSelectorBottomSheet visual style.
 *
 * @param fullScreen When true, expands to the full screen height (e.g. team pickers).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoBottomSheet(
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    fullScreen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = fullScreen)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = modifier.then(if (fullScreen) Modifier.fillMaxHeight() else Modifier)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fullScreen) Modifier.fillMaxHeight() else Modifier)
                .padding(horizontal = 24.dp)
        ) {
            // Header
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Content area (scrollable)
            val contentModifier = if (fullScreen) {
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            } else {
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            }
            Column(modifier = contentModifier) {
                content()
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
