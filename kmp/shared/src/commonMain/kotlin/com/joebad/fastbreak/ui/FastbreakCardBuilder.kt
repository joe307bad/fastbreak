
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.data.dailyFastbreak.*

@Composable
fun FastbreakCardScreen(viewModel: FastbreakCardViewModel) {
    val state by viewModel.container.stateFlow.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadGameStates()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colors.background
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Fastbreak Cards",
                style = MaterialTheme.typography.h5,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (state.cards.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No cards available")
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.cards) { card ->
                        CardItem(card = card) { newSelection ->
                            viewModel.updateSelection(card.id, newSelection)
                        }
                    }
                }
            }

            Button(
                onClick = { viewModel.loadGameStates() },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Refresh Cards")
            }
        }
    }
}

@Composable
fun CardItem(
    card: FastbreakCardItem,
    onSelectionUpdated: (FastbreakCardSelection) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Card #${card.id}",
                style = MaterialTheme.typography.h6
            )

            Spacer(modifier = Modifier.height(8.dp))

            when (val selection = card.selection) {
                is PickEm -> {
                    Text("Type: Pick'Em")
                    Text("Selection: ${selection.solution}")

                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onSelectionUpdated(PickEm("Team A")) },
                            enabled = selection.solution != "Team A"
                        ) {
                            Text("Team A")
                        }

                        Button(
                            onClick = { onSelectionUpdated(PickEm("Team B")) },
                            enabled = selection.solution != "Team B"
                        ) {
                            Text("Team B")
                        }
                    }
                }
                is Trivia -> {
                    Text("Type: Trivia")
                    Text("Answer: ${selection.solution}")

                    var text by remember { mutableStateOf(selection.solution) }
                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        label = { Text("Your Answer") }
                    )

                    Button(
                        onClick = {
                            if (text.isNotEmpty()) {
                                onSelectionUpdated(Trivia(text))
                            }
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Submit Answer")
                    }
                }

                else -> throw IllegalArgumentException("Unknown selection type")
            }
        }
    }
}