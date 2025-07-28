
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.with
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.ui.theme.LocalColors
import io.github.alexzhirkevich.cupertino.CupertinoIcon
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.filled.LockOpen

/**
 * AnimatedLockIcon - A composable that animates between locked and unlocked icons
 *
 * @param locked Boolean state determining whether to show the locked or unlocked icon
 * @param modifier Modifier for customizing the component's layout
 * @param size The size of the icon in dp
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AnimatedLockIcon(
    locked: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    val colors = LocalColors.current;
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(

            targetState = locked,
            transitionSpec = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(300)
                ) with slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(300)
                )
            }
        ) { isLocked ->
            if (isLocked) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Lock",
                    tint = colors.onSecondary,
                    modifier = Modifier.size(17.dp)
                )
            } else {
                CupertinoIcon(
                    imageVector = CupertinoIcons.Filled.LockOpen,
                    contentDescription = "Lock",
                    tint = colors.text,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun ThreeSectionLayout(
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    footer: @Composable () -> Unit
) {
    val colors = LocalColors.current;
    Column(
        modifier = modifier.fillMaxSize().background(color = colors.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            header()
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                content()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
        ) {
            footer()
        }
    }
}