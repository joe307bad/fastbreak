
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.joebad.fastbreak.ui.CardWithBadge
import com.joebad.fastbreak.ui.home.FastbreakHomeList
import com.joebad.fastbreak.ui.theme.LocalColors
import com.joebad.fastbreak.ui.theme.lighten

@Composable
fun FastbreakHome(
    locked: Boolean,
    listState: LazyListState,
    animatedAlpha: Float,
    showModal: MutableState<Boolean>
) {
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
                    badgeText = "LOADING THE DAILY FASTBREAK",
                    modifier = Modifier.padding(bottom = 30.dp),
                    content = {
                        Box(
                            modifier = Modifier
                                .height(16.dp)
                                .fillMaxWidth()
                                .background(colors.primary)
                                .shimmerEffect(
                                    baseColor = colors.primary,
                                    highlightColor = lighten(colors.primary, 0.6f),
                                    durationMillis = 2000
                                )
                        )
//                        Box(
//                            modifier = Modifier
//                                .height(16.dp)
//                                .fillMaxWidth()
////                                .padding(top = 120.dp)
//                                .placeholder(
//                                    visible = true,
//                                    highlight = PlaceholderHighlight.shimmer(
//                                        highlightColor = lighten(
//                                            colors.accent,
//                                            0.4f
//                                        )
//                                    ),
//                                    color = colors.accent
//                                )
//                        )
                    },
                    badgeColor = colors.secondary,
                    badgeTextColor = colors.onSecondary,
                    points = ""
                )
                FastbreakHomeList()
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
                FABWithExactShapeBorder(locked, showModal = { showModal.value = true })
            }
        }
    }
}