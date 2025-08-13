
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import com.joebad.fastbreak.ui.screens.CacheStatus
import com.joebad.fastbreak.ui.theme.LocalColors

@Composable
fun HomeScreen(cacheStatus: CacheStatus = CacheStatus()) {
    val colors = LocalColors.current
    Text(
        "Home - Cache Status: ${if (cacheStatus.isCached) "Cached" else if (cacheStatus.isLoading) "Loading" else "Fresh"}",
        color = colors.text
    )
//
//    val totalPoints = fastbreakViewModel?.container?.stateFlow?.collectAsState()?.value?.totalPoints;
//
//    // Convert selectedDate (yyyyMMdd) to LocalDate and calculate Season/Week/Day
//    val dateComponents = try {
//        val year = selectedDate.substring(0, 4).toInt()
//        val month = selectedDate.substring(4, 6).toInt()
//        val day = selectedDate.substring(6, 8).toInt()
//        val localDate = LocalDate(year, month, day)
//        DateUtils.getSeasonWeekDay(localDate)
//    } catch (e: Exception) {
//        Triple(1, 1, 1) // fallback values
//    }
//
//    val (season, week, dayOfWeek) = dateComponents
//    val headerSubtitle = "Season $season • Week $week • Day $dayOfWeek"
//    Box(modifier = Modifier.fillMaxSize()) {
//        LazyColumn(
//            state = listState,
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(horizontal = 16.dp)
//                .zIndex(2f),
//            verticalArrangement = Arrangement.spacedBy(16.dp)
//        ) {
//            item {
//                Spacer(
//                    modifier = Modifier.height(105.dp)
//                )
//                if (error != null) {
//                    // Show error message instead of loading
//                    ErrorDailyFastbreak(error)
//                } else if (dailyFastbreak == null || fastbreakViewModel == null) {
//                    LoadingDailyFastbreak()
//                } else {
//                    FastbreakHomeList(dailyFastbreak, fastbreakViewModel)
//                }
//            }
//        }
//        Box(modifier = Modifier.zIndex(1f)) {
//            RoundedBottomHeaderBox(
//                "FASTBREAK",
//                headerSubtitle,
//                animatedAlpha = animatedAlpha
//            )
//        }
//        Box(
//            modifier = Modifier
//                .fillMaxSize()
//                .zIndex(3f)
//        ) {
//            if (dailyFastbreak != null && totalPoints != null && hasActiveItems(dailyFastbreak.fastbreakCard))
//                Box(
//                    modifier = Modifier
//                        .align(Alignment.BottomCenter)
//                        .padding(bottom = 16.dp)
//                ) {
//                    FABWithExactShapeBorder(
//                        locked,
//                        showModal = { showModal.value = true },
//                        totalPoints
//                    )
//                }
//        }
//    }
}