# Firebase Setup Guide for Runsense App

## Prerequisites
- Android Studio installed
- Google Account
- Firebase Project

## Step 1: Create a Firebase Project

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Click "Create a project" or select an existing project
3. Enter project name: "Runsense" (or your preferred name)
4. Click "Continue"
5. Enable Google Analytics (optional)
6. Click "Create project"

## Step 2: Register Your Android App

1. In Firebase Console, click on the Android icon
2. Package name: `com.example.miniproject`
3. App nickname: `Runsense` (optional)
4. SHA-1 certificate fingerprint (for Google Sign-In):
   - Obtain it by running in Android Studio Terminal:
   ```bash
   ./gradlew signingReport
   ```
   - Copy the SHA1 value from the output
5. Click "Register app"

## Step 3: Download google-services.json

1. Click "Download google-services.json"
2. Save the file to: `app/google-services.json`
   - Path should be: `MiniProject/app/google-services.json`

## Step 4: Enable Firebase Authentication

1. In Firebase Console, go to Authentication
2. Click "Get started"
3. Enable the following sign-in methods:
   - **Email/Password**: Click "Email/Password" → Enable → Save
   - **Google**: Click "Google" → Enable → Select your project support email → Save

## Step 5: Configure OAuth Consent Screen (for Google Sign-In)

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Select your Firebase project
3. Navigate to "APIs & Services" → "OAuth consent screen"
4. Configure the following:
   - User Type: External (while in development)
   - Fill in required fields:
     - App name: Runsense
     - User support email: Your email
     - Developer contact information: Your email
5. Click "Save and continue"
6. Add scopes:
   - Search for `auth/userinfo.email` and `auth/userinfo.profile`
   - Select both and click "Update"
7. Click "Save and continue" → "Save and continue" again

## Step 6: Create OAuth 2.0 Client ID

1. Go to "APIs & Services" → "Credentials"
2. Click "Create Credentials" → "OAuth 2.0 Client ID"
3. Select "Android"
4. Paste the SHA-1 certificate fingerprint from Step 2
5. Enter package name: `com.example.miniproject`
6. Enter signing certificate SHA-1 fingerprint
7. Click "Create"
8. Copy the Web Client ID

## Step 7: Update Your App

### File: `app/src/main/res/values/strings.xml`
Replace `YOUR_WEB_CLIENT_ID_HERE` with the Web Client ID from Step 6:
```xml
<string name="default_web_client_id">YOUR_WEB_CLIENT_ID_HERE</string>
```

## Step 8: Build and Run

1. Sync Gradle files
2. Build the project: `./gradlew build`
3. Run on emulator or device: Click "Run" in Android Studio

## Project Structure

```
app/
├── src/
│   └── main/
│       ├── java/com/example/miniproject/
│       │   ├── MainActivity.kt          (Launcher/Router)
│       │   ├── LoginActivity.kt         (Login screen)
│       │   ├── SignupActivity.kt        (Sign up screen)
│       │   ├── OtpActivity.kt           (OTP verification)
│       │   └── HomeActivity.kt          (Main app after login)
│       ├── res/
│       │   ├── layout/
│       │   │   ├── activity_login.xml
│       │   │   ├── activity_signup.xml
│       │   │   ├── activity_otp.xml
│       │   │   └── activity_home.xml
│       │   └── drawable/
│       │       ├── button_background.xml
│       │       ├── edittext_background.xml
│       │       └── login_background.xml
│       └── AndroidManifest.xml
├── build.gradle.kts
├── google-services.json (⚠️ ADD THIS FILE)
└── ...
```

## Features Implemented

### ✅ Authentication
- **Email/Password Login**: Users can sign in with email and password
- **Email/Password Registration**: Users can create an account with email
- **Google Sign-In**: Seamless Google account login on both Login and Sign Up screens
- **OTP Verification**: Email-based OTP verification for security
- **Password Reset**: Forgot password functionality

### ✅ UI Screens
1. **Login Screen** (`LoginActivity`)
   - Username/Email field
   - Password field
   - Forgot Password link
   - Google Sign-In button
   - Link to Sign Up

2. **Sign Up Screen** (`SignupActivity`)
   - Full Name field
   - Username/Email field
   - Password field
   - Date of Birth field
   - City field
   - Google Sign-Up button
   - Link to Login

3. **OTP Verification Screen** (`OtpActivity`)
   - Username field
   - Send OTP button
   - OTP input field
   - Verify OTP button
   - Google Sign-In button

4. **Home Screen** (`HomeActivity`)
   - Displays logged-in user information
   - User name and email
   - Logout button

### ✅ Security Features
- Firebase Authentication with automatic token management
- Google Play Services for secure OAuth
- Password encryption by Firebase
- Session management

## Troubleshooting

### Issue: Build fails with "google-services.json not found"
**Solution**: Make sure `google-services.json` is placed in the `app/` directory

### Issue: "Invalid Web Client ID"
**Solution**: 
- Double-check the Web Client ID in `strings.xml`
- Regenerate the OAuth client ID in Google Cloud Console if needed

### Issue: Google Sign-In not working
**Solution**:
- Verify SHA-1 fingerprint matches in Google Cloud Console
- Check that OAuth consent screen is configured
- Ensure internet permission is enabled in AndroidManifest.xml

### Issue: Firebase Authentication not responding
**Solution**:
- Check internet connection
- Verify Firebase project is active
- Check Firebase Authentication is enabled for Email/Password and Google

## Next Steps

1. **Customize the UI**: Modify the layout files to match your exact design
2. **Add Database**: Integrate Firestore/Realtime Database for storing user data
3. **Implement Email Verification**: Add email verification for new sign-ups
4. **Add More Authentication Methods**: Facebook, Twitter, Phone Authentication
5. **Enhance Security**: Add biometric authentication, two-factor authentication

## Dependencies Added

```kotlin
// Firebase
implementation(platform(libs.firebase.bom))
implementation(libs.firebase.auth)
implementation(libs.play.services.auth)

// Kotlin Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
```

## Support

For more information:
- [Firebase Documentation](https://firebase.google.com/docs)
- [Firebase Authentication Guide](https://firebase.google.com/docs/auth)
- [Google Sign-In Documentation](https://developers.google.com/identity/sign-in)
