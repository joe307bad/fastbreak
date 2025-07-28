@file:OptIn(ExperimentalMaterial3Api::class)

package com.joebad.fastbreak.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.ui.help.HelpContent
import com.joebad.fastbreak.ui.theme.LocalColors
import kotlinx.coroutines.launch

@Composable
fun SimpleBottomSheetExample(
    showBottomSheet: Boolean,
    onDismiss: () -> Unit,
    helpContent: HelpContent
) {
    val bottomSheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val colors = LocalColors.current

    if (showBottomSheet) {
        ModalBottomSheet(
            containerColor = colors.background,
            onDismissRequest = onDismiss,
            sheetState = bottomSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = helpContent.title,
                    color = colors.text,
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    helpContent.description,
                    color = colors.text
                )

                Spacer(modifier = Modifier.height(32.dp))

                PhysicalButton(
                    borderColor = colors.accent,
                    backgroundColor = colors.background,
                    onClick = {
                        scope.launch {
                            bottomSheetState.hide()
                            onDismiss()
                        }
                    }
                ) {
                    Text("CLOSE")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}