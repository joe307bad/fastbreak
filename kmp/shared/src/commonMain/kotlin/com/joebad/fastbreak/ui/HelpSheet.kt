@file:OptIn(ExperimentalMaterial3Api::class)

package com.joebad.fastbreak.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.ui.help.HelpItem
import com.joebad.fastbreak.ui.theme.LocalColors
import kotlinx.coroutines.launch

@Composable
fun HelpSheet(
    showBottomSheet: Boolean,
    onDismiss: () -> Unit,
    helpItems: List<HelpItem>,
    helpTitle: String
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
                Title(helpTitle.replace("_", " "))

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(helpItems) { helpItem ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = helpItem.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.accent,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            
                            Text(
                                text = helpItem.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.text,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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