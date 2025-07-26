package com.joebad.fastbreak.ui.home

import Question
import QuestionComponent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.data.dailyFastbreak.FastbreakViewModel
import com.joebad.fastbreak.model.dtos.DailyFastbreak
import com.joebad.fastbreak.ui.CardWithBadge
import com.joebad.fastbreak.ui.TeamCard
import com.joebad.fastbreak.ui.theme.LocalColors

@Composable
fun FastbreakHomeList(dailyFastbreak: DailyFastbreak?, viewModel: FastbreakViewModel) {
    val colors = LocalColors.current;

//    val viewModel = FastbreakViewModel()
    val state by viewModel.container.stateFlow.collectAsState()


    if (dailyFastbreak?.fastbreakCard == null)
        throw Exception("DailyFastbreak is null");

    for (item in dailyFastbreak.fastbreakCard) {
        when (item.type) {
            "FEATURED-PICK-EM" ->
                CardWithBadge(
                    badgeText = "FEATURED PICK-EM",
                    modifier = Modifier.padding(bottom = 30.dp),
                    content = {
                        TeamCard(
                            item.dateLine1,
                            item.dateLine2,
                            item.dateLine3,
                            item.homeTeam,
                            item.homeTeamSubtitle,
                            item.awayTeam,
                            item.awayTeamSubtitle,
                            selectedAnswer = state.selections.find { it._id == item.id }?.userAnswer,
                            onAnswerSelected = { answer ->
                                val userLoser =
                                    if (answer == item.homeTeam) item.awayTeam else item.homeTeam
                                viewModel.updateSelection(
                                    item.id,
                                    answer,
                                    item.points,
                                    "$answer to win against $userLoser",
                                    "FEATURED PICK-EM"
                                )
                            }
                        )
                    },
                    badgeColor = colors.accent,
                    badgeTextColor = colors.onAccent,
                    points = item.points.toString()
                )

            "PICK-EM" ->
                CardWithBadge(
                    badgeText = "PICK-EM",
                    modifier = Modifier.padding(bottom = 10.dp),
                    points = item.points.toString(),
                    content = {
                        TeamCard(
                            item.dateLine1,
                            item.dateLine2,
                            item.dateLine3,
                            item.homeTeam,
                            item.homeTeamSubtitle,
                            item.awayTeam,
                            item.awayTeamSubtitle,
                            selectedAnswer = state.selections.find { it._id == item.id }?.userAnswer,
                            onAnswerSelected = { answer ->
                                val userLoser =
                                    if (answer == item.homeTeam) item.awayTeam else item.homeTeam
                                viewModel.updateSelection(
                                    item.id,
                                    answer,
                                    item.points,
                                    "$answer to win against $userLoser",
                                    "PICK-EM"
                                )
                            }
                        )
                    }
                )

            "TRIVIA-MULTIPLE-CHOICE" ->
                CardWithBadge(
                    badgeText = "TRIVIA",
                    modifier = Modifier.padding(bottom = 30.dp),
                    content = {
                        Column {
                            QuestionComponent(
                                Question.MultipleChoice(
                                    item.question,
                                    listOf(
                                        item.answer1,
                                        item.answer2,
                                        item.answer3,
                                        item.answer4
                                    )
                                ),
                                selectedAnswer = state.selections.find { it._id == item.id }?.userAnswer,
                                onAnswerSelected = { answer ->
                                    viewModel.updateSelection(
                                        item.id,
                                        answer,
                                        item.points,
                                        "${item.question} - $answer",
                                        "TRIVIA"
                                    )
                                }
                            )
                        }
                    },
                    badgeColor = colors.secondary,
                    badgeTextColor = colors.onSecondary,
                    points = item.points.toString()
                )

            "TRIVIA-TF" ->
                CardWithBadge(
                    badgeText = "TRIVIA",
                    modifier = Modifier.padding(bottom = 30.dp),
                    content = {
                        Column {
                            QuestionComponent(
                                Question.TrueFalse(
                                    item.question
                                ),
                                selectedAnswer = state.selections.find { it._id == item.id }?.userAnswer,
                                onAnswerSelected = { answer ->
                                    viewModel.updateSelection(
                                        item.id,
                                        answer,
                                        item.points,
                                        "${item.question} - $answer",
                                        "TRIVIA"
                                    )
                                }
                            )
                        }
                    },
                    badgeColor = colors.secondary,
                    badgeTextColor = colors.onSecondary,
                    points = item.points.toString()
                )

            else -> println("Unknown Type")
        }
    }
    Spacer(
        modifier = Modifier.height(100.dp)
    )

}