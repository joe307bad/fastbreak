package com.joebad.fastbreak.ui.screens

import AuthRepository
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.Text
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appstractive.jwt.JWT
import com.appstractive.jwt.from
import com.joebad.fastbreak.BuildKonfig
import com.joebad.fastbreak.data.cache.CachedHttpClient
import com.joebad.fastbreak.data.cache.KotbaseApiCache
import com.joebad.fastbreak.data.dailyFastbreak.DailyFastbreakResult
import com.joebad.fastbreak.data.dailyFastbreak.getDailyFastbreakCached
import com.joebad.fastbreak.ui.theme.LocalColors
import kotbase.Database
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

data class CacheStatus(
    val isLoading: Boolean = false,
    val isCached: Boolean = false,
    val rawJson: String? = null,
    val error: String? = null
)

/**
 * Gets the current fastbreak date in yyyyMMdd format.
 * If current time is before 4am ET, returns previous day's date.
 */
private fun getCurrentFastbreakDate(): String {
    val etTimeZone = TimeZone.of("America/New_York")
    val now = Clock.System.now()
    val nowET = now.toLocalDateTime(etTimeZone)
    val today = nowET.date
    val fourAmET = LocalTime(4, 0)
    
    val dateToUse = if (nowET.time < fourAmET) {
        // Before 4am ET, use previous day
        today.minus(1, DateTimeUnit.DAY)
    } else {
        // After 4am ET, use today
        today
    }
    
    return "${dateToUse.year.toString().padStart(4, '0')}${dateToUse.monthNumber.toString().padStart(2, '0')}${dateToUse.dayOfMonth.toString().padStart(2, '0')}"
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun TokenDisplayScreen(
    authRepository: AuthRepository,
    database: Database? = null,
    httpClient: io.ktor.client.HttpClient? = null,
    onLogout: () -> Unit
) {
    val colors = LocalColors.current
    val user = authRepository.getUser()
    val coroutineScope = rememberCoroutineScope()
    
    // Cache status state
    var cacheStatus by remember { mutableStateOf(CacheStatus()) }
    
    // Bottom sheet state
    val bottomSheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden
    )
    
    // Real cache behavior when user is logged in
    LaunchedEffect(user) {
        if (user != null && database != null && httpClient != null) {
            try {
                // Show loading state
                cacheStatus = cacheStatus.copy(isLoading = true, isCached = false, error = null)
                
                // Calculate the correct date based on 4am ET cutoff
                val dateString = getCurrentFastbreakDate()
                
                // Create cached HTTP client
                val apiCache = KotbaseApiCache(database)
                val cachedHttpClient = CachedHttpClient(httpClient, apiCache)
                
                // Make the API call
                val apiUrl = "${BuildKonfig.API_BASE_URL}/api/day/${dateString}"
                val result = getDailyFastbreakCached(cachedHttpClient, apiUrl, null)
                
                when (result) {
                    is DailyFastbreakResult.Success -> {
                        cacheStatus = cacheStatus.copy(
                            isLoading = false,
                            isCached = result.isFromCache,
                            rawJson = result.rawJson,
                            error = null
                        )
                    }
                    is DailyFastbreakResult.Error -> {
                        cacheStatus = cacheStatus.copy(
                            isLoading = false,
                            isCached = false,
                            rawJson = null,
                            error = result.message
                        )
                    }
                }
            } catch (e: Exception) {
                cacheStatus = cacheStatus.copy(
                    isLoading = false,
                    isCached = false,
                    rawJson = null,
                    error = "Error: ${e.message}"
                )
            }
        } else if (user != null) {
            // Fallback to simulation if dependencies not provided
            cacheStatus = cacheStatus.copy(isLoading = true, isCached = false, error = null)
            kotlinx.coroutines.delay(1500)
            val mockJson = """{"leaderboard":null,"fastbreakCard":[{"id":"demo","type":"game","homeTeam":"Demo","awayTeam":"Mode"}],"statSheet":null}"""
            cacheStatus = cacheStatus.copy(
                isLoading = false,
                isCached = false,
                rawJson = mockJson,
                error = null
            )
        }
    }
    
    ModalBottomSheetLayout(
        sheetState = bottomSheetState,
        sheetContent = {
            CacheDetailsBottomSheet(cacheStatus = cacheStatus)
        }
    ) {
        Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        
        // Receipt-style container
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = Color.White,
            elevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Text(
                    text = "═══ TOKEN RECEIPT ═══",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (user != null) {
                    // User info section
                    ReceiptLine("USER INFO")
                    ReceiptDivider()
                    ReceiptItem("EMAIL", user.email)
//                    ReceiptItem("USER_ID", user.userId)
//                    ReceiptItem("USERNAME", user.userName)
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Collapsible Token Info
                    var tokenInfoExpanded by remember { mutableStateOf(false) }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { tokenInfoExpanded = !tokenInfoExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (tokenInfoExpanded) "▼ TOKEN INFO" else "▶ TOKEN INFO",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Text(
                            text = "[TAP]",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.Gray
                        )
                    }
                    
                    if (tokenInfoExpanded) {
                        ReceiptDivider()
                        
                        // Token expiration
                        val expireTime = try {
                            val currentTime = kotlinx.datetime.Clock.System.now()
                            val etTimestamp = user.exp - (4 * 3600)  // UTC-4 for ET
                            val etInstant = kotlinx.datetime.Instant.fromEpochSeconds(etTimestamp)
                            val etTimeString = etInstant.toString().replace('T', ' ').substringBefore('.')
                            val etDatePart = etTimeString.substringBefore(' ')
                            val etTimePart = etTimeString.substringAfter(' ')
                            val etHour = etTimePart.substringBefore(':').toInt()
                            val etMinuteSecond = etTimePart.substringAfter(':')
                            
                            val (hour12, amPm) = if (etHour == 0) {
                                Pair(12, "AM")
                            } else if (etHour < 12) {
                                Pair(etHour, "AM")
                            } else if (etHour == 12) {
                                Pair(12, "PM")
                            } else {
                                Pair(etHour - 12, "PM")
                            }
                            
                            "$etDatePart $hour12:$etMinuteSecond $amPm ET"
                        } catch (e: Exception) {
                            "INVALID"
                        }
                        
                        ReceiptItem("EXPIRES", expireTime)
                        ReceiptItem("RAW_EXP", user.exp.toString())
                        ReceiptItem("LENGTH", "${user.idToken.length} chars")
                        
                        // JWT Claims
                        val jwt = try { JWT.from(user.idToken) } catch (e: Exception) { null }
                        if (jwt != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            ReceiptLine("JWT CLAIMS")
                            ReceiptDivider()
                            jwt.claims.forEach { (key, value) ->
                                ReceiptItem(key.uppercase(), value.toString())
                            }
                        }
                        
                        // Token preview
                        Spacer(modifier = Modifier.height(4.dp))
                        ReceiptLine("TOKEN PREVIEW")
                        ReceiptDivider()
                        Text(
                            text = user.idToken,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // API Endpoints
                    ReceiptLine("API ENDPOINTS")
                    ReceiptDivider()
                    ReceiptItem("HEALTH", "GET /")
                    DailyEndpointItem(
                        cacheStatus = cacheStatus,
                        onItemClick = {
                            coroutineScope.launch {
                                bottomSheetState.show()
                            }
                        }
                    )
                    ReceiptItem("LOCK_CARD", "POST /api/lock")
                    ReceiptItem("PROFILE_SAVE", "POST /api/profile")
                    ReceiptItem("INIT", "POST /api/profile/initialize/{userId}")
                    ReceiptItem("TRIGGER", "POST /trigger/all-jobs")
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Footer
                Text(
                    text = "═══════════════════",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
        
        // Logout Button
        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color.Red,
                contentColor = Color.White
            )
        ) {
            Text("LOGOUT", fontSize = 14.sp, fontFamily = FontFamily.Monospace)
        }
        }
    }
}

@Composable
private fun ReceiptLine(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ReceiptDivider() {
    Text(
        text = "┄".repeat(30),
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        color = Color.Gray,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ReceiptItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(2f)
        )
    }
}

@Composable
private fun DailyEndpointItem(
    cacheStatus: CacheStatus,
    onItemClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "DAILY",
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        
        Row(
            modifier = Modifier.weight(2f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "GET /api/day/{date}",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Cache indicator badge
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(16.dp)
                    .background(
                        color = when {
                            cacheStatus.isLoading -> Color.Yellow.copy(alpha = 0.7f)
                            cacheStatus.isCached -> Color.Green.copy(alpha = 0.7f)
                            cacheStatus.error != null -> Color.Red.copy(alpha = 0.7f)
                            cacheStatus.rawJson != null && !cacheStatus.isCached -> Color.Blue.copy(alpha = 0.7f)
                            else -> Color.Gray.copy(alpha = 0.3f)
                        },
                        shape = RoundedCornerShape(2.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        cacheStatus.isLoading -> "LOADING"
                        cacheStatus.isCached -> "CACHED"
                        cacheStatus.error != null -> "ERROR"
                        cacheStatus.rawJson != null && !cacheStatus.isCached -> "FRESH"
                        else -> "NONE"
                    },
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
private fun CacheDetailsBottomSheet(cacheStatus: CacheStatus) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "═══ CACHE DETAILS ═══",
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Status info
        ReceiptLine("STATUS")
        ReceiptDivider()
        ReceiptItem("CACHED", if (cacheStatus.isCached) "YES" else "NO")
        ReceiptItem("LOADING", if (cacheStatus.isLoading) "YES" else "NO")
        if (cacheStatus.error != null) {
            ReceiptItem("ERROR", cacheStatus.error)
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // JSON preview
        if (cacheStatus.rawJson != null) {
            ReceiptLine("JSON PREVIEW")
            ReceiptDivider()
            
            val preview = if (cacheStatus.rawJson.length > 100) {
                cacheStatus.rawJson.take(100) + "..."
            } else {
                cacheStatus.rawJson
            }
            
            Text(
                text = preview,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.Black,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            ReceiptItem("LENGTH", "${cacheStatus.rawJson.length} chars")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "═══════════════════",
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.Black,
            modifier = Modifier.fillMaxWidth()
        )
    }
}