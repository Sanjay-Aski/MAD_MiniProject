# 🏃‍♂️ GPS-Based Smart Running Tracker Application

## 📋 Overview

A comprehensive Android running tracking application that combines **GPS navigation**, **step counting sensors**, and **real-time analytics** to provide users with detailed performance metrics and visual route mapping.

### Key Features
✅ **Real-Time Run Tracking** - Start/Stop/Pause running sessions  
✅ **GPS Route Visualization** - Live path tracking on Google Maps  
✅ **Performance Metrics** - Distance, speed, steps, calories, duration  
✅ **Weekly Analytics** - Charts and performance trends  
✅ **User Profile Management** - Height, weight, age, gender  
✅ **Firebase Integration** - Cloud storage for runs and user data  
✅ **Push Notifications** - Goal achievements and milestones  
✅ **Dark UI Theme** - Modern professional interface  

---

## 🏗️ System Architecture

```
┌─────────────────────────────────────────┐
│         User Interface Layer            │
│  (Activities & Fragments)               │
├─────────────────────────────────────────┤
│         Service Layer                   │
│  • LocationService (GPS)                │
│  • StepCounterService (Sensors)         │
│  • NotificationService (Alerts)         │
├─────────────────────────────────────────┤
│         Data Layer                      │
│  • Firebase Firestore                   │
│  • Room Database (Local Cache)          │
├─────────────────────────────────────────┤
│         Hardware Integration            │
│  • GPS / FusedLocationProvider          │
│  • Step Counter Sensor                  │
│  • Google Maps SDK                      │
└─────────────────────────────────────────┘
```

---

## 📱 Screen Breakdown

### 1. **Morning Run Screen** (RunTrackingActivity)
- **Real-time metrics display**: Distance, Speed, Steps, Calories
- **Timer** for total run duration
- **Start/Pause/Stop/End buttons**  
- **Pace calculation** in min/km format
- Live heart rate zone indicator

### 2. **Dashboard Fragment**
- Daily stats overview with progress bars
- Total distance, steps, calories achieved today
- Daily goal tracking (default: 10,000 steps)
- Recent runs list
- Quick stats: Avg Speed, Max Speed, Duration

### 3. **Map Fragment**
- Google Maps integration
- **Real-time polyline** showing running path
- **Start marker** (green pin)
- **End marker** (red pin)
- Camera auto-fit to show entire route

### 4. **Analytics Fragment**
- **3 weekly charts**:
  - Bar chart: Weekly distance (km)
  - Line chart: Speed trends
  - Bar chart: Calories burned
- Performance summary statistics
- Best run, fastest pace, total calories

### 5. **Account Settings Fragment**
- **User profile editor**:
  - Name, Email
  - Height (cm), Weight (kg), Age
  - Gender selection
- **Preferences**:
  - Daily step goal
  - Push notifications toggle
- Save changes & Logout buttons

### 6. **Navigation**
- Bottom navigation bar with 4 tabs
- Smooth fragment transitions
- Dashboard → Map → Analytics → Settings

---

## 🔧 Technical Implementation

### Data Models

**RunSession** - Stores complete run data
```kotlin
data class RunSession(
    val runId: String,
    val startTime: Long,
    val endTime: Long,
    val duration: Long,        // seconds
    val distance: Double,      // km
    val avgSpeed: Double,      // km/h
    val maxSpeed: Double,      // km/h
    val steps: Int,
    val calories: Double,
    val pathPointsCount: Int
)
```

**GPSPoint** - Individual GPS coordinates
```kotlin
data class GPSPoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val speed: Float,          // m/s
    val altitude: Double,
    val accuracy: Float
)
```

**UserProfile** - User information
```kotlin
data class UserProfile(
    val userId: String,
    val name: String,
    val height: Int,           // cm
    val weight: Int,           // kg
    val gender: String,        // Male/Female
    val age: Int
)
```

### Calculation Formulas

**Distance (from GPS)**
```
distance = Σ(distanceTo(current_point, previous_point))
```

**Average Speed**
```
avgSpeed = totalDistance / totalTime
```

**Stride Length**
```
stride = height × (0.415 for male, 0.413 for female)
```

**Calories Burned**
```
calories = distance(km) × weight(kg) × 1.036
```

---

## 📦 Dependencies

```gradle
// Google Maps & Location Services
implementation("com.google.android.gms:play-services-maps:18.2.0")
implementation("com.google.android.gms:play-services-location:21.1.0")

// Firebase
implementation(libs.firebase.firestore)
implementation(libs.firebase.database)

// Charts
implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

// Navigation & Fragments
implementation("androidx.fragment:fragment-ktx:1.6.2")
implementation("androidx.navigation:navigation-fragment-ktx:2.7.6")

// Local Database (Room)
implementation("androidx.room:room-runtime:2.6.1")
```

---

## 🛡️ Permissions Required

```xml
<!-- Location Services -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- Sensors -->
<uses-permission android:name="android.permission.BODY_SENSORS" />

<!-- Notifications -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Storage -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```

---

## 🚀 Getting Started

### Prerequisites
- Android Studio 2023+
- Android SDK 24+
- Google Maps API Key
- Firebase Project

### Setup Steps

1. **Get Google Maps API Key**
   - Go to [Google Cloud Console](https://console.cloud.google.com/)
   - Create new project
   - Enable Maps SDK for Android
   - Create API key
   - Add to AndroidManifest.xml:
   ```xml
   <meta-data
       android:name="com.google.android.geo.API_KEY"
       android:value="YOUR_API_KEY_HERE" />
   ```

2. **Setup Firebase**
   - Create Firebase project in Firebase Console
   - Connect to Android app
   - Download `google-services.json`
   - Place in `app/` directory

3. **Clone & Build**
   ```bash
   git clone <repository>
   cd MiniProject
   ./gradlew build
   ```

4. **Run App**
   - Select target Android device
   - Click Run in Android Studio

---

## 💾 Data Storage

### Firebase Firestore Structure
```
users/
  └── {userId}/
      ├── name: String
      ├── email: String
      ├── height: Int
      ├── weight: Int
      └── ...

runs/
  └── {runId}/
      ├── userId: String
      ├── distance: Double
      ├── duration: Long
      ├── avgSpeed: Double
      └── ...

gps_points/
  └── {collection}/
      ├── runId: String
      ├── latitude: Double
      ├── longitude: Double
      └── ...
```

### Local Database (Room)
- Cached runs for offline access
- GPS points for route reconstruction
- User preferences

---

## 🔔 Notification Types

1. **Run Started** - When user taps Start
2. **Run Ended** - When run completes with summary
3. **Goal Achieved** - When daily step goal reached
4. **Personal Record** - When new speed/distance record
5. **Pause Reminder** - After 30 mins of running

---

## 📊 Analytics Capabilities

✓ Weekly distance tracking  
✓ Speed trend analysis  
✓ Calories burned chart  
✓ Best run finder  
✓ Personal records  
✓ Daily vs weekly comparison  

---

## 🎨 UI/UX Features

- **Dark Theme** (#1A1A1E background)
- **Accent Colors**: Blue (#00A8FF), Green (#00FF7F), Orange (#FF9500)
- **Card-based Layout** with rounded corners
- **Smooth Transitions** between fragments
- **Real-time Updates** with LiveData
- **Responsive Design** for all screen sizes

---

## 📋 Project Scope (Exam-Ready)

### Uniqueness Statement
> "Unlike basic fitness apps, our system combines step sensors and GPS-based path tracking to provide accurate distance, average speed, maximum speed, and visual route mapping, making it suitable for organizational-level health monitoring."

### Core Highlights
✓ Sensor-based step counting  
✓ GPS route visualization on maps  
✓ Real-time distance & speed calculation  
✓ Session-based tracking (Start/Stop/End)  
✓ Firebase cloud integration  
✓ Weekly analytics with charts  
✓ Push notifications  

---

## 🤝 Contributing

Feel free to fork and submit PRs for improvements!

## 📄 License

MIT License - See LICENSE file

## 👨‍💻 Author

Developed as a comprehensive Android project showcasing:
- Android Sensor APIs
- Google Maps Integration  
- Firebase Backend
- Modern UI/UX Patterns
- Real-time Data Processing

---

**Ready for College Evaluation ✓**
│   │   ├── res/layout/
│   │   │   ├── activity_login.xml          [NEW]
│   │   │   ├── activity_signup.xml         [NEW]
│   │   │   ├── activity_otp.xml            [NEW]
│   │   │   └── activity_home.xml           [NEW]
│   │   │
│   │   ├── res/drawable/
│   │   │   ├── button_background.xml       [NEW - Blue button style]
│   │   │   ├── edittext_background.xml     [NEW - Input field style]
│   │   │   └── login_background.xml        [NEW - Gradient background]
│   │   │
│   │   ├── res/values/
│   │   │   └── strings.xml                 [UPDATED - Added web client ID]
│   │   │
│   │   └── AndroidManifest.xml             [UPDATED - Activities & permissions]
│   │
│   └── google-services.json                [⚠️ MISSING - Need to add from Firebase]
│
├── build.gradle.kts                        [UPDATED - Google Services plugin]
├── gradle/libs.versions.toml               [UPDATED - Firebase & Play Services versions]
│
├── QUICKSTART.md                           [NEW - Quick setup checklist]
├── FIREBASE_SETUP.md                       [NEW - Detailed Firebase guide]
└── IMPLEMENTATION_SUMMARY.md               [NEW - What was implemented]
```

## 🚀 Features Implemented

### Authentication
- [x] Email/Password Login
- [x] Email/Password Registration  
- [x] Google Sign-In (OAuth 2.0)
- [x] Google Sign-Up
- [x] Password Reset
- [x] OTP Verification
- [x] Session Management
- [x] Auto-logout

### UI/UX
- [x] Login Screen
- [x] Sign Up Screen with profile fields
- [x] OTP Verification Screen
- [x] Home Screen with user info
- [x] Smooth navigation between screens
- [x] Input validation
- [x] Toast notifications
- [x] Error handling

### Security
- [x] Firebase Authentication
- [x] Google OAuth 2.0
- [x] Password encryption by Firebase
- [x] HTTPS enforcement
- [x] Secure token management
- [x] Network security

## 📦 Dependencies Added

```kotlin
// Firebase
implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
implementation("com.google.firebase:firebase-auth-ktx")
implementation("com.google.android.gms:play-services-auth:20.7.0")

// Kotlin Coroutines (for async operations)
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
```

## 🛠️ Setup Instructions

### Quick Start (5 minutes)
1. Open `QUICKSTART.md` for step-by-step checklist
2. Create Firebase project
3. Download `google-services.json`
4. Add `google-services.json` to `app/` folder
5. Update Web Client ID in `strings.xml`
6. Build and run

### Detailed Setup (20 minutes)
1. Follow all steps in `FIREBASE_SETUP.md`
2. Configure OAuth consent screen
3. Register Android app with SHA-1
4. Test all authentication flows

## 🧪 Testing

### Test Scenarios

**Scenario 1: Email Login**
```
1. Launch app → See Login screen
2. Enter email and password
3. Click Login button
4. Should navigate to Home screen
5. Verify user info displayed
6. Click Logout → Back to Login
```

**Scenario 2: Email Sign Up**
```
1. From Login → Click "Create Here"
2. Fill all fields (name, email, password, DOB, city)
3. Click Sign Up
4. Should navigate to Home screen
5. Verify account in Firebase Console
```

**Scenario 3: Google Sign-In**
```
1. Click Google Sign-In button
2. Select Google account
3. Should navigate to Home screen
4. Verify Google account linked in Firebase
```

**Scenario 4: Password Reset**
```
1. On Login screen → Click "Forgot Password?"
2. Enter email address
3. Check email for reset link
4. Reset password
5. Login with new password
```

## 📱 Screen Navigation Flow

```
┌─────────────────────────────────────┐
│         MainActivity                 │
│    (Checks login status)            │
└────────────┬────────────────────────┘
             │
      ┌──────┴──────┐
      │             │
   YES│             │NO
      │             │
   ┌──┴──┐    ┌────┴─────┐
   │Home │    │ LoginActivity
   └──┬──┘    │  ├─ Login
      │       │  ├─ Forgot Password
      │       │  ├─ Google Sign-In
      │       │  └─ Create Account ──┐
      │       └────────────────────┐  │
      │                            │  │
      │                    ┌───────┘  │
      │                    │          │
      │               ┌────┴──────────┘
      │               │
      │         ┌─────▼──────────────┐
      │         │ SignupActivity      │
      │         │  ├─ Sign Up         │
      │         │  ├─ Google Sign-Up  │
      │         │  └─ Login Here ─────┘
      │         │          │
      │         └──────┬───┘
      │                │
      │         ┌──────▼──────┐
      │         │ OtpActivity  │
      │         │ ├─ Send OTP  │
      │         │ └─ Verify OTP│
      │         └──────┬───────┘
      │                │
      │         ┌──────▼──────┐
      └────────►│ HomeActivity │
                │ ├─ User Info │
                │ └─ Logout ───┘
                └──────────────┘
```

## 🔧 Code Examples

### Login with Email
```kotlin
firebaseAuth.signInWithEmailAndPassword(email, password).await()
// Automatically navigates to home on success
```

### Google Sign-In
```kotlin
val credential = GoogleAuthProvider.getCredential(idToken, null)
firebaseAuth.signInWithCredential(credential).await()
// User authenticated with Google account
```

### Password Reset
```kotlin
firebaseAuth.sendPasswordResetEmail(email).await()
// User receives password reset email
```

### Check User Status
```kotlin
val user = firebaseAuth.currentUser
if (user != null) {
    // User is logged in
} else {
    // User is not logged in
}
```

## ⚙️ Configuration

### Step 1: Firebase Project Creation
```
Firebase Console → Create Project → Register Android App
→ Download google-services.json → Save to app/ folder
```

### Step 2: Firebase Authentication Setup
```
Firebase → Authentication → Get Started
→ Enable Email/Password
→ Enable Google
```

### Step 3: Google OAuth Configuration
```
Google Cloud Console → APIs & Services
→ Configure OAuth Consent Screen
→ Create Android OAuth Credential
→ Add SHA-1 fingerprint
```

### Step 4: Update App Code
```
strings.xml → Update default_web_client_id
build.gradle.kts → Already configured with plugins
AndroidManifest.xml → Already has required permissions
```

## 📊 User Flow Diagram

```
New User
│
├─ Email Sign Up
│  └─ Fill profile → Firebase creates account → Home
│
└─ Google Sign Up
   └─ Select account → Firebase OAuth → Home

Existing User
│
├─ Email Login
│  └─ Enter credentials → Firebase validates → Home
│
└─ Google Login
   └─ Select account → Firebase OAuth → Home

Logged In User
│
└─ Home Screen
   ├─ View profile info
   └─ Logout → Back to Login
```

## 🎯 What Each Activity Does

| Activity | Purpose | Features |
|----------|---------|----------|
| **MainActivity** | App launcher & router | Checks if user logged in, routes to Login or Home |
| **LoginActivity** | User sign in | Email/password login, Google sign-in, password reset |
| **SignupActivity** | User registration | Email/password registration, profile setup, Google sign-up |
| **OtpActivity** | OTP verification | Generate OTP, verify OTP, Google sign-in backup |
| **HomeActivity** | Main app screen | Display user info, logout functionality |

## 📚 Documentation Files

1. **QUICKSTART.md** - Fast checklist for setup
2. **FIREBASE_SETUP.md** - Complete step-by-step guide  
3. **IMPLEMENTATION_SUMMARY.md** - Technical implementation details
4. **This file (README)** - Overview and architecture

## 🐛 Troubleshooting

| Problem | Solution |
|---------|----------|
| Google sign-in not working | Check SHA-1 in Google Cloud Console |
| Build error: missing google-services.json | Download from Firebase Console, add to app/ |
| "Invalid Web Client ID" | Update strings.xml with correct Web Client ID from Google Cloud |
| Firebase auth fails | Check internet connection, verify Firebase project is active |
| Google button not showing | Install Google Repository via SDK Manager |

## 🌟 Features You Can Add Next

1. **Email Verification** - Verify email before account activation
2. **Two-Factor Authentication** - Add 2FA for security
3. **Biometric Login** - Fingerprint/Face recognition
4. **Social Login** - Facebook, Twitter, LinkedIn
5. **Profile Management** - Edit user profile, change password
6. **Friends List** - Follow other users
7. **Activity Tracking** - Track fitness activities
8. **Notifications** - Push notifications
9. **Leaderboards** - Compare with friends
10. **Settings** - App preferences, privacy controls

## 📞 Support & Resources

- **Firebase Documentation**: https://firebase.google.com/docs
- **Firebase Authentication**: https://firebase.google.com/docs/auth
- **Google Sign-In**: https://developers.google.com/identity/sign-in
- **Android Architecture**: https://developer.android.com/guide/architecture
- **Kotlin Coroutines**: https://kotlinlang.org/docs/coroutines-overview.html

## ✅ Implementation Checklist

- [x] Create login screen UI
- [x] Create signup screen UI
- [x] Create OTP screen UI
- [x] Create home screen UI
- [x] Add Firebase dependencies
- [x] Implement email/password authentication
- [x] Implement Google OAuth
- [x] Add password reset
- [x] Add session management
- [x] Add navigation between screens
- [x] Update AndroidManifest.xml
- [x] Create drawable resources
- [x] Create documentation
- [ ] Download google-services.json from Firebase
- [ ] Update Web Client ID in strings.xml
- [ ] Test all authentication flows
- [ ] Deploy to Play Store (later)

## 🎉 You're All Set!

The authentication system is ready for Firebase configuration. Follow the **QUICKSTART.md** to complete the setup and get the app running!

---

**Last Updated**: March 2024  
**Status**: Ready for Firebase Configuration  
**Next Step**: Follow QUICKSTART.md
