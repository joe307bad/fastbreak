
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.ui.PhysicalButton
import com.joebad.fastbreak.ui.theme.LocalColors


fun generateRandomUsername(): String {
    val adjectives = listOf("cool", "fast", "bright", "smart", "bold", "swift", "wild", "calm", "brave", "wise")
    val nouns = listOf("tiger", "eagle", "wolf", "lion", "bear", "shark", "hawk", "fox", "deer", "owl")
    val verbs = listOf("runs", "jumps", "flies", "swims", "climbs", "dives", "soars", "leaps", "glides", "races")
    
    val randomNumber = (1000..9999).random()
    return "${adjectives.random()}-${nouns.random()}-${verbs.random()}-$randomNumber"
}

@Composable
fun ProfileScreen(
    userId: String,
    email: String,
    userName: String,
    loading: Boolean? = false,
    onSaveUserName: (String) -> Unit
) {
    val colors = LocalColors.current
    val gmailUsername = email.substringBefore("@").replace("\"", "")
    var randomUsername by remember { mutableStateOf(if (userName != gmailUsername) userName else generateRandomUsername()) }
    
    var selectedOption by remember { mutableStateOf(if (userName == gmailUsername) 0 else 1) }
    val usernameOptions = listOf(gmailUsername, randomUsername)

    val selectedUsername = usernameOptions[selectedOption]

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column(
            modifier = Modifier.fillMaxSize().background(colors.background).padding(8.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Title("PROFILE")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                color = colors.text,
                text = "",
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier.fillMaxSize().background(colors.background),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.Start
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().background(colors.background),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            text = "User ID:",
                            color = colors.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.width(100.dp)
                        )
                        Text(
                            text = userId,
                            color = colors.onPrimary,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Text(
                        text = "Leaderboard display name:",
                        color = colors.onPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp, top = 16.dp)
                    )
                    
                    usernameOptions.forEachIndexed { index, option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = (selectedOption == index),
                                    onClick = { 
                                        selectedOption = index
                                    }
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (selectedOption == index),
                                onClick = { 
                                    selectedOption = index
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = colors.onPrimary,
                                    unselectedColor = colors.onPrimary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = option,
                                color = colors.onPrimary,
                                fontSize = 16.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )
                            if (index == 1) {
                                OutlinedButton(
                                    onClick = { 
                                        randomUsername = generateRandomUsername()
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = colors.onPrimary
                                    ),
                                    shape = RectangleShape,
                                    modifier = Modifier.size(width = 80.dp, height = 32.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Text(
                                            text = "NEW",
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    PhysicalButton(
                        onClick = {
                            onSaveUserName(selectedUsername)
                        },
                        backgroundColor = colors.secondary,
                        contentColor = colors.onSecondary,
                        bottomBorderColor = colors.accent,
                        shape = RectangleShape,
                        loading = loading,
                        modifier = Modifier.fillMaxWidth(),
                        textSize = 16
                    ) {
                        Text(
                            text = "SAVE PROFILE",
                            textAlign = TextAlign.Center
                        )
                    }

                }

            }
        }
    }
}