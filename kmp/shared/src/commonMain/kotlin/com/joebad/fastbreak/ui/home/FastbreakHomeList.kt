package com.joebad.fastbreak.ui.home

import Question
import QuestionComponent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.ui.CardWithBadge
import com.joebad.fastbreak.ui.TeamCard
import com.joebad.fastbreak.ui.theme.LocalColors


@Composable
fun FastbreakHomeList() {
    val colors = LocalColors.current;

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