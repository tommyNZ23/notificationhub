package nz.co.katproductions.notificationhub

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import nz.co.katproductions.notificationhub.ui.theme.NotificationHubTheme
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import nz.co.katproductions.notificationhub.BuildConfig
import java.text.DateFormat
import java.util.Date

private val TigerOrange = Color(0xFFF47A20)
private val TigerBlack = Color(0xFF17120E)

class MainActivity : ComponentActivity() {

    // -----------------------------------------
    // GOOGLE SIGN-IN SETUP
    // -----------------------------------------

    // 1. Create the Activity Result launcher for Google Sign-In
    private val signInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)

                val idToken = account.idToken
                val email = account.email

                Log.d("MainActivity", "Google sign-in success. Email = $email")
                Log.d("MainActivity", "Google ID token (first 40 chars) = ${idToken?.take(40)}...")

                if (BuildConfig.DEBUG) {
                    Log.d("MainActivity", "Google ID token FULL = $idToken")
                }

                if (idToken == null || email == null) {
                    Log.e("MainActivity", "Missing idToken or email from Google account")
                    return@registerForActivityResult
                }

                // After we have a valid Google identity, get the FCM token
                FirebaseMessaging.getInstance().token
                    .addOnCompleteListener { taskToken ->
                        if (!taskToken.isSuccessful) {
                            Log.e("MainActivity", "Fetching FCM token failed", taskToken.exception)
                            return@addOnCompleteListener
                        }

                        val fcmToken = taskToken.result
                        Log.d("MainActivity", "FCM token = $fcmToken")

                        NotificationHubRegistration.registerDeviceWithBackend(
                            this,
                            idToken,
                            email,
                            fcmToken
                        )
                    }

            } catch (e: ApiException) {
                Log.e("MainActivity", "Google sign-in failed", e)
            }
        }


    // 2. Build the Google Sign-In client
    private val googleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(
            GoogleSignInOptions.DEFAULT_SIGN_IN
        )
            .requestIdToken(getString(R.string.server_client_id))  // Your Web Client ID
            .requestEmail()
            .build()

        GoogleSignIn.getClient(this, gso)
    }


    private fun startGoogleSignIn() {
        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }
    // -----------------------------------------
    // END GOOGLE SIGN-IN SETUP
    // -----------------------------------------

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            Log.d("MainActivity", "Notification permission granted? $isGranted")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Request POST_NOTIFICATIONS on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 2. Usual UI setup
        setContent {
            NotificationHubTheme {
                NotificationInboxApp()
            }
        }

        // 3. Start Google Sign-In flow (for now we auto-launch it)
        startGoogleSignIn()
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun NotificationInboxApp() {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var messages by remember { mutableStateOf(NotificationMessageStore.getMessages(context)) }

    DisposableEffect(context) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            mainHandler.post {
                messages = NotificationMessageStore.getMessages(context)
            }
        }

        NotificationMessageStore.registerChangeListener(context, listener)
        onDispose {
            NotificationMessageStore.unregisterChangeListener(context, listener)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(R.mipmap.ic_launcher_notificationhub_foreground),
                            contentDescription = "NotificationHub logo",
                            modifier = Modifier.size(32.dp)
                        )
                        Text("NotificationHub")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = TigerBlack
                )
            )
        }
    ) { innerPadding ->
        NotificationInboxScreen(
            messages = messages,
            onDismissMessage = { message ->
                NotificationMessageStore.deleteMessage(context, message.id)
                messages = NotificationMessageStore.getMessages(context)
            },
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
fun NotificationInboxScreen(
    messages: List<NotificationMessage>,
    onDismissMessage: (NotificationMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    if (messages.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No messages yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            NotificationMessageRow(
                message = message,
                onDismiss = { onDismissMessage(message) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationMessageRow(
    message: NotificationMessage,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onDismiss()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.fillMaxWidth(),
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TigerOrange)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = "Clear",
                    color = TigerBlack,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        ) {
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(5.dp)
                        .background(TigerOrange)
                )
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = formatReceivedAt(message.receivedAtMillis),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = message.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TigerBlack
                    )
                    Text(
                        text = message.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatReceivedAt(receivedAtMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(receivedAtMillis))

private val previewMessages = listOf(
    NotificationMessage(
        id = "1",
        receivedAtMillis = 1_767_213_600_000,
        title = "BodyGood New booking",
        body = "New booking: BodyGood - Alex Morgan - Thu 21 May 2026 9:30AM - Massage"
    ),
    NotificationMessage(
        id = "2",
        receivedAtMillis = 1_767_210_000_000,
        title = "BodyGood Booking moved",
        body = "Booking moved: BodyGood - Jamie Lee - Thu 21 May 2026 1:15PM"
    )
)

@Preview(showBackground = true)
@Composable
fun NotificationInboxPreview() {
    NotificationHubTheme {
        NotificationInboxScreen(
            messages = previewMessages,
            onDismissMessage = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun EmptyNotificationInboxPreview() {
    NotificationHubTheme {
        NotificationInboxScreen(
            messages = emptyList(),
            onDismissMessage = {}
        )
    }
}
