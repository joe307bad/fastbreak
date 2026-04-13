package com.joebad.fastbreak.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.navigation.TopicsV2Component
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val loremSentences = listOf(
    "Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
    "Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.",
    "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris.",
    "Duis aute irure dolor in reprehenderit in voluptate velit esse cillum.",
    "Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia.",
    "Curabitur pretium tincidunt lacus, nec gravida arcu fermentum sed.",
    "Vivamus sagittis lacus vel augue laoreet rutrum faucibus dolor auctor.",
    "Maecenas faucibus mollis interdum, sed posuere consectetur est at lobortis.",
    "Praesent commodo cursus magna, vel scelerisque nisl consectetur et.",
    "Fusce dapibus, tellus ac cursus commodo, tortor mauris condimentum nibh.",
    "Donec ullamcorper nulla non metus auctor fringilla vestibulum id ligula.",
    "Nullam quis risus eget urna mollis ornare vel eu leo morbi.",
)

@Composable
private fun SwipeToDismissItem(
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val offsetX = remember { Animatable(0f) }
    var width by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { width = it.width }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val w = width
                            if (w == 0) return@detectHorizontalDragGestures
                            val threshold = w * 0.4f
                            scope.launch {
                                if (abs(offsetX.value) > threshold) {
                                    val target = if (offsetX.value > 0) w.toFloat() else -w.toFloat()
                                    offsetX.animateTo(target, tween(200))
                                    onDismissed()
                                } else {
                                    offsetX.animateTo(0f, tween(200))
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(0f, tween(200)) }
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                    }
                }
        ) {
            content()
        }
    }
}

@Composable
fun TopicsV2Screen(
    component: TopicsV2Component
) {
    val items = remember { mutableStateListOf(*loremSentences.toTypedArray()) }
    var headerVisible by remember { mutableStateOf(true) }
    var showSizeSlider by remember { mutableStateOf(false) }
    var fontSize by remember { mutableStateOf(18f) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            if (headerVisible) {
                Column {
                    TopAppBar(
                        title = { },
                        navigationIcon = {
                            IconButton(onClick = component.onNavigateBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        },
                        actions = {
                            TextButton(onClick = { showSizeSlider = !showSizeSlider }) {
                                Text(
                                    text = "Aa",
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Light,
                                    fontSize = 18.sp
                                )
                            }
                            IconButton(onClick = { headerVisible = false }) {
                                Icon(
                                    imageVector = Icons.Default.ExpandLess,
                                    contentDescription = "Hide header"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground
                        )
                    )

                    // Size slider
                    AnimatedVisibility(visible = showSizeSlider) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "A",
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Light,
                                fontSize = 12.sp
                            )
                            Slider(
                                value = fontSize,
                                onValueChange = { fontSize = it },
                                valueRange = 12f..32f,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            Text(
                                text = "A",
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Light,
                                fontSize = 28.sp
                            )
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().then(
                    if (!headerVisible) Modifier.padding(top = 16.dp) else Modifier
                )
            ) {
                items(
                    items = items,
                    key = { it }
                ) { sentence ->
                    SwipeToDismissItem(
                        onDismissed = { items.remove(sentence) },
                        modifier = Modifier.animateItem()
                    ) {
                        Text(
                            text = sentence,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Light,
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * 1.4f).sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 60.dp, top = 16.dp, bottom = 16.dp)
                        )
                    }
                }
            }
        }

            // Small FAB to reopen header
            if (!headerVisible) {
                SmallFloatingActionButton(
                    onClick = { headerVisible = true },
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 12.dp, top = 32.dp)
                        .size(36.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "Show header",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
