
import androidx.compose.runtime.Composable
import com.joebad.fastbreak.ui.App
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun AndroidAppRoot() {
    App()
//    var authReady by remember { mutableStateOf(false) }
//    LaunchedEffect(Unit) {
//
//        GoogleAuthProvider.create(
//            credentials = GoogleAuthCredentials(
//                serverId = GOOGLE_AUTH_SERVER_ID
//            )
//        )
//        authReady = true
//    }
//
//    MaterialTheme {
//        if (authReady) {
//            Box(
//                modifier = Modifier.fillMaxSize(),
//                contentAlignment = Alignment.Center
//            ) {
//                GoogleButtonUiContainer(
//                    onGoogleSignInResult = { googleUser ->
//                        val tokenId = googleUser?.idToken
//                        println("TOKEN: $tokenId")
//                    }
//                ) {
//                    GoogleSignInButton(
//                        onClick = { this.onClick() }
//                    )
//                }
//            }
//        }
//    }
}