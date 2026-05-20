package nz.co.katproductions.notificationhub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlin.concurrent.thread


class MainActivity : ComponentActivity() {

    private val http = OkHttpClient()

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

                        // TODO: send idToken + fcmToken to your backend /registerDevice
                        registerDeviceWithBackend(idToken, fcmToken)
                    }

            } catch (e: ApiException) {
                Log.e("MainActivity", "Google sign-in failed", e)
            }
        }


    private fun registerDeviceWithBackend(idToken: String, fcmToken: String) {
        val url = "${BuildConfig.API_BASE_URL}/register-device"

        val json = JSONObject()
            .put("idToken", idToken)
            .put("deviceToken", fcmToken)
            .toString()

        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader("x-notificationhub-secret", BuildConfig.NOTIFICATION_HUB_SHARED_SECRET)
            .build()

        thread {
            try {
                http.newCall(request).execute().use { resp ->
                    val respText = resp.body?.string()
                    Log.d("MainActivity", "register-device HTTP ${resp.code} body=$respText")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "register-device call failed", e)
            }
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
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Pickle",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        // 3. Start Google Sign-In flow (for now we auto-launch it)
        startGoogleSignIn()
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    NotificationHubTheme {
        Greeting("Pickle")
    }
}


