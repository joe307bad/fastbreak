package com.joebad.fastbreak.ui.screens

import AuthRepository
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
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
import com.joebad.fastbreak.ui.theme.LocalColors

@Composable
fun TokenDisplayScreen(
    authRepository: AuthRepository,
    onLogout: () -> Unit
) {
    val colors = LocalColors.current
    val user = authRepository.getUser()
    
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
                    
                    ReceiptLine("TOKEN INFO")
                    ReceiptDivider()
                    ReceiptItem("EXPIRES", expireTime)
                    ReceiptItem("RAW_EXP", user.exp.toString())
                    ReceiptItem("LENGTH", "${user.idToken.length} chars")
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // JWT Claims
                    val jwt = try { JWT.from(user.idToken) } catch (e: Exception) { null }
                    if (jwt != null) {
                        ReceiptLine("JWT CLAIMS")
                        ReceiptDivider()
                        jwt.claims.forEach { (key, value) ->
                            ReceiptItem(key.uppercase(), value.toString())
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    // Token preview
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
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // API Endpoints
                    ReceiptLine("API ENDPOINTS")
                    ReceiptDivider()
                    ReceiptItem("HEALTH", "GET /")
                    ReceiptItem("DAILY", "GET /api/day/{date}")
                    ReceiptItem("LOCK_SAVE", "POST /api/lock")
                    ReceiptItem("LOCK_GET", "GET /api/lock/{userId}")
                    ReceiptItem("PROFILE", "POST /api/profile")
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