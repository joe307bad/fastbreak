import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.ui.theme.LocalColors


sealed interface Question {
    val text: String?

    data class TrueFalse(override val text: String?) : Question
    data class MultipleChoice(override val text: String?, val choices: List<String?>) : Question
}

@Composable
fun QuestionComponent(
    question: Question
) {
    var selectedAnswer by remember { mutableStateOf<String?>(null) }
    val colors = LocalColors.current;

    Column {
        Text(
            text = question.text ?: "",
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
                is Question.TrueFalse -> answers?.forEach { answer ->
                    ElevatedButton(
                        onClick = { selectedAnswer = answer },
                        modifier = Modifier.weight(1f).padding(4.dp),
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = if (selectedAnswer == answer) colors.secondary else colors.primary
                        )
                    ) {
                        if (answer != null) {
                            Text(
                                answer,
                                color = if (selectedAnswer == answer) colors.onSecondary else colors.onPrimary
                            )
                        }
                    }
                }

                is Question.MultipleChoice -> {
                    val buttonShape = RoundedCornerShape(50.dp)   // Define the shape once
                    Column {
                        answers?.forEach { answer ->
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
                                if (answer != null) {
                                    Text(
                                        answer,
                                        color = if (selectedAnswer == answer) colors.onSecondary else colors.onPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {}
            }
        }
    }
}
