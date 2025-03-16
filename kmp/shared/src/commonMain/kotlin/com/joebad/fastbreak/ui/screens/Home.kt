
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.joebad.fastbreak.ui.CardWithBadge
import com.joebad.fastbreak.ui.TeamCard
import com.joebad.fastbreak.ui.theme.LocalColors

sealed interface Question {
    val text: String

    data class TrueFalse(override val text: String) : Question
    data class MultipleChoice(override val text: String, val choices: List<String>) : Question
}

@Composable
fun QuestionComponent(
    question: Question
) {
    var selectedAnswer by remember { mutableStateOf<String?>(null) }
    val colors = LocalColors.current;

    Column(
//        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = question.text,
            color = colors.text,
            textAlign = TextAlign.Left,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val answers = when (question) {
            is Question.TrueFalse -> listOf("True", "False")
            is Question.MultipleChoice -> question.choices
            else -> emptyList()
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            when (question) {
                is Question.TrueFalse -> answers.forEach { answer ->
                    ElevatedButton(
                        onClick = { selectedAnswer = answer },
                        modifier = Modifier.weight(1f).padding(4.dp),
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = if (selectedAnswer == answer) colors.secondary else colors.primary
                        )
                    ) {
                        Text(
                            answer,
                            color = if (selectedAnswer == answer) colors.onSecondary else colors.onPrimary
                        )
                    }
                }

                is Question.MultipleChoice -> {
                    val buttonShape = RoundedCornerShape(50.dp)   // Define the shape once
                    Column {
                        answers.forEach { answer ->
                            OutlinedButton(
                                onClick = { selectedAnswer = answer },
                                modifier = Modifier.fillMaxWidth(1f).padding(4.dp),
                                shape = buttonShape,
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = colors.secondary
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selectedAnswer == answer) colors.secondary else colors.background
                                )
                            ) {
                                Text(
                                    answer,
                                    color = if (selectedAnswer == answer) colors.onSecondary else colors.onPrimary
                                )
                            }
                        }
                    }
                }

                else -> {}
            }
        }
    }
}


@Composable
fun HomeScreen(listState: LazyListState, animatedAlpha: Float, showModal: MutableState<Boolean>) {
    val colors = LocalColors.current;


    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .zIndex(2f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(
                    modifier = Modifier.height(130.dp)
                )
                CardWithBadge(
                    badgeText = "FEATURED PICK-EM",
                    modifier = Modifier.padding(bottom = 30.dp),
                    content = { TeamCard("Monday", "Sept. 10th", "@ 1pm") },
                    badgeColor = colors.accent,
                    badgeTextColor = colors.onAccent,
                    points = "1,000"
                )
//                CardWithBadge(
//                    badgeText = "TRIVIA",
//                    modifier = Modifier.padding(bottom = 30.dp),
//                    content = {
//                        Column {
//                            QuestionComponent(Question.TrueFalse("Lorem ipsum dolor sit amet, consectetur tempor incididunt ut labore et dolore magna aliqua?"))
//                        }
//                    },
//                    badgeColor = colors.secondary,
//                    badgeTextColor = colors.onSecondary,
//                    points = "6,000"
//                )
                CardWithBadge(
                    badgeText = "TRIVIA",
                    modifier = Modifier.padding(bottom = 30.dp),
                    content = {
                        Column {
                            QuestionComponent(
                                Question.MultipleChoice(
                                    "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed doet dolore magna aliqua?",
                                    listOf(
                                        "Stephen Curry",
                                        "Reggie Miller",
                                        "Ray Allen",
                                        "Paul Pierce"
                                    )
                                )
                            )
                        }
                    },
                    badgeColor = colors.secondary,
                    badgeTextColor = colors.onSecondary,
                    points = "2,000"
                )
                CardWithBadge(
                    badgeText = "PICK-EM",
                    modifier = Modifier.padding(bottom = 10.dp),
                    content = { TeamCard("Monday", "Sept. 10th", "@ 1pm") }
                )
                CardWithBadge(
                    badgeText = "PICK-EM",
                    modifier = Modifier.padding(bottom = 10.dp),
                    content = { TeamCard("Monday", "Sept. 10th", "@ 1pm") }
                )
                CardWithBadge(
                    badgeText = "PICK-EM",
                    modifier = Modifier.padding(bottom = 10.dp),
                    content = { TeamCard("Monday", "Sept. 10th", "@ 1pm") }
                )
                CardWithBadge(
                    badgeText = "PICK-EM",
                    modifier = Modifier.padding(bottom = 10.dp),
                    content = { TeamCard("Monday", "Sept. 10th", "@ 1pm") }
                )
                Spacer(
                    modifier = Modifier.height(100.dp)
                )
            }
        }
        Box(modifier = Modifier.zIndex(1f)) {
            RoundedBottomHeaderBox(
                "FASTBREAK",
                "Daily sports pick-em and trivia",
                "Season 1 • Week 50 • Day 3",
                animatedAlpha = animatedAlpha
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(3f)
        ) {

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            ) {
                FABWithExactShapeBorder(showModal = { showModal.value = true })
            }
        }
    }
}