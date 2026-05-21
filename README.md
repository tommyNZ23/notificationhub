# NotificationHub

NotificationHub is a native Android app that signs users in with Google, registers the device's Firebase Cloud Messaging token with a backend API, and displays incoming FCM messages as Android notifications.

## Stack

- Kotlin
- Jetpack Compose
- Firebase Cloud Messaging
- Google Sign-In
- OkHttp
- Gradle Kotlin DSL

## Local Setup

1. Open the project in Android Studio.
2. Ensure `local.properties` points to your Android SDK.
3. Add the local NotificationHub backend values to `local.properties`:

   ```properties
   notificationHubApiBaseUrl=https://your-api.example.com/dev
   notificationHubSharedSecret=your-local-shared-secret
   ```

4. Place your Firebase `google-services.json` at:

   ```text
   app/google-services.json
   ```

5. Build the debug app:

   ```powershell
   .\gradlew.bat assembleDebug
   ```

## Git Notes

The following files are intentionally not tracked:

- `local.properties`
- `app/google-services.json`
- Gradle and Android build output
- keystores and signing config

Use environment variables `NOTIFICATION_HUB_API_BASE_URL` and `NOTIFICATION_HUB_SHARED_SECRET` in CI if you build outside Android Studio.

## FCM Token Refresh

After the user signs in successfully, the app stores the registered email locally. If Firebase later issues a new FCM token, `MyFirebaseMessagingService.onNewToken()` calls the backend `/update-device-token` route with that email and the new token so the SNS endpoint can be repaired without opening the app.
