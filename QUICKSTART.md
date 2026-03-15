# 🏃‍♂️ Running Tracker - Quick Start Guide

## What's Implemented

### ✅ All 5 Screens from Image
1. **Morning Run** - Real-time tracking with sensors & GPS
2. **Dashboard** - Daily stats overview with goal progress  
3. **Map** - Visual route on Google Maps
4. **Analytics** - 3 weekly performance charts
5. **Account Settings** - User profile & preferences

### ✅ Core Features
- **GPS Tracking**: Real-time location with FusedLocationClient
- **Step Counting**: Android step sensor integration
- **Distance Calculation**: From GPS points + speed computation
- **Metrics**: Steps, Distance, Speed (avg/max), Calories, Duration
- **Push Notifications**: Run milestones & achievement alerts
- **Firebase Backend**: Cloud storage for all data
- **Dark Professional UI**: Modern color scheme with proper theming
- **Navigation**: Bottom nav bar with smooth fragment transitions

---

## 📂 Project Structure

```
app/src/main/java/com/example/miniproject/
├── RunTrackingActivity.kt          ← Main run screen
├── HomeActivity.kt                 ← Navigation hub
├── service/
│   ├── LocationService.kt          ← GPS tracking
│   ├── StepCounterService.kt       ← Step counting
│   └── NotificationService.kt      ← Alerts
├── data/
│   ├── model/
│   │   ├── RunSession.kt           ← Run data
│   │   ├── GPSPoint.kt             ← GPS coordinates
│   │   └── UserProfile.kt          ← User info
│   └── FirebaseRepository.kt       ← Cloud storage
└── ui/fragments/
    ├── DashboardFragment.kt        ← Daily overview
    ├── MapFragment.kt              ← Route visualization
    ├── AnalyticsFragment.kt        ← Weekly charts
    └── AccountSettingsFragment.kt  ← Profile editor
```

---

## 🚀 How to Get Started

### 1. Add Google Maps API Key
```
1. Go to Google Cloud Console
2. Create project and enable Maps SDK
3. Create API key
4. In AndroidManifest.xml, find:
   <meta-data android:name="com.google.android.geo.API_KEY"
5. Replace "YOUR_GOOGLE_MAPS_API_KEY_HERE" with your key
```

### 2. Setup Firebase
```
1. Go to Firebase Console
2. Create new project
3. Register Android app
4. Download google-services.json
5. Place in app/ folder
6. Sync Gradle
```

### 3. Build & Run
```
./gradlew build
# Then run on Android device/emulator
```

---

## 📱 Using Each Screen

### Morning Run (RunTrackingActivity)
**What it does**: Tracks a single run session
**Buttons**:
- **Start** → Begins GPS tracking and step counting
- **Pause** → Pauses all tracking (shows "Resume")
- **Stop** → Stops tracking but keeps session open
- **End Run** → Saves run to Firebase and ends

**Metrics shown**:
- Distance (km) - Real-time from GPS
- Avg Speed (km/h) - Distance/Time
- Max Speed (km/h) - Highest speed recorded
- Steps - From step sensor
- Calories - Steps × 0.04 formula
- Pace - Minutes per km
- Timer - Total elapsed time

---

### Dashboard Tab
**What it shows**: Your daily summary
- Progress bar toward 10,000 step goal
- Color-coded metric cards:
  - 🔵 Distance (blue)
  - 💚 Steps (green)  
  - 🟠 Calories (orange)
  - Total time, avg speed, max speed
- Recent runs list (RecyclerView ready)

---

### Map Tab
**What it shows**: Visual running route
- Google Maps with your path
- 🟢 Green pin = Start point
- 🔴 Red pin = End point
- Blue polyline = Your actual route
- Auto-zoomed to fit entire run

---

### Analytics Tab
**What it shows**: Weekly performance  
3 charts:
1. Bar chart - Distance per day (km)
2. Line chart - Speed trend (km/h)
3. Bar chart - Calories per day

Card summary:
- Best run distance
- Fastest pace
- Total calories this week

---

### Settings Tab
**What you can do**:
- Edit name, email, height, weight, age
- Select gender (affects stride calculation)
- Set daily step goal
- Toggle push notifications
- Save profile changes
- Logout account

---

## 🎯 Key Features Explained

### GPS Tracking
```kotlin
// Every 1 second:
1. Get current location from phone GPS
2. Calculate distance from previous point
3. Update speed values
4. Store GPS point in database
5. Update UI with new metrics
```

### Step Counting
```kotlin
// From built-in sensor:
1. Register TYPE_STEP_COUNTER listener
2. Count steps automatically
3. Calculate distance: steps × stride_length
4. Calculate calories: distance × weight × 1.036
```

### Distance Calculation
```
From GPS: Sum of all distances between points
From Steps: Total_Steps × Stride_Length (in meters)
```

### Speed Calculation
```
Instant: GPS reports speed in m/s → convert to km/h (×3.6)
Average: Total Distance / Total Time
Maximum: Highest instant speed recorded
```

### Calories
```
Simple formula: Steps × 0.04
OR: distance(km) × weight(kg) × 1.036
```

---

## 💾 Data Saved to Firebase

After each run:
```
Firestore Collection: "runs"
├── Document: {runId}
│   ├── userId: "user123"
│   ├── distance: 9.5 km
│   ├── duration: 3600 seconds
│   ├── avgSpeed: 11.3 km/h
│   ├── maxSpeed: 15.2 km/h
│   ├── steps: 12450
│   ├── calories: 450.5
│   └── createdAt: 1234567890

Firestore Collection: "gps_points"
├── Document 1: {lat: 40.123, lng: -74.456, ...}
├── Document 2: {lat: 40.124, lng: -74.457, ...}
└── ... (hundreds of points)
```

---

## 🔔 Notifications Sent

1. **Run Started** → When user taps Start
2. **Run Ended** → After tapping End (with summary)
3. **Daily Goal Achieved** → When 10k steps reached
4. **Personal Record** → On new speed/distance high
5. **Pause Reminder** → After 30 mins running

---

## ⚙️ Technical Highlights

### Permission Handling
```kotlin
// Runtime permissions for:
- Fine location (GPS)
- Coarse location (approx.)
- Body sensors (step counter)
- Post notifications
- Storage (data export)
```

### LiveData Observers
```kotlin
// UI updates automatically with:
- locationService.gpsPoints
- locationService.avgSpeed
- locationService.maxSpeed
- stepCounterService.stepCount
- stepCounterService.calories
```

### Async Operations
```kotlin
// Using coroutines:
- Firebase saves happen without blocking UI
- Database queries run on background thread
- Location updates stream continuously
```

---

## 🎨 Color Reference

| Element | Color | Hex |
|---------|-------|-----|
| Background | Dark | #1A1A1E |
| Cards | Dark Gray | #252530 |
| Primary Accent | Blue | #00A8FF |
| Success | Green | #00FF7F |
| Danger | Red | #FF4444 |
| Warning | Orange | #FF9500 |

---

## 🐛 Troubleshooting

### No GPS signal
→ Make sure location permission is granted  
→ Test on real device with GPS (emulator may not work)  
→ Wait a few seconds for initial lock

### Steps not counting
→ Device must have step counter sensor  
→ Grant BODY_SENSORS permission  
→ Keep app in foreground during run

### Map not showing
→ Add Google Maps API key to manifest  
→ Check API is enabled in Google Cloud  
→ Verify internet connection

### Firebase not saving
→ Check google-services.json is in app/ folder  
→ Verify Firebase project setup  
→ Check Firestore security rules allow writes

---

## 📚 Code Examples

### Starting a Run
```kotlin
button.setOnClickListener {
    locationService.startLocationUpdates(runId)
    stepCounterService.startTracking()
    chronometer.start()
}
```

### Ending a Run
```kotlin
val runSession = RunSession(
    distance = locationService.getTotalDistance(),
    avgSpeed = locationService.getAvgSpeed(),
    steps = stepCounterService.getTotalSteps(),
    calories = stepCounterService.getTotalCalories()
)
firebaseRepository.saveRunSession(runSession)
```

### Displaying Map
```kotlin
val points = locationService.getGPSPointsForRun()
mapFragment.updateGPSPath(points)
```

---

## ✅ Checklist for Exam/College

- [x] Sensor integration (step counter)
- [x] GPS tracking (real-time)
- [x] Path visualization (maps)
- [x] Distance calculation (from GPS)
- [x] Speed metrics (avg & max)
- [x] Calorie calculation
- [x] Daily analytics
- [x] Weekly charts
- [x] Firebase backend
- [x] Push notifications
- [x] User profile management
- [x] Professional UI (dark theme)
- [x] Bottom navigation
- [x] Proper error handling
- [x] Runtime permissions

---

## ✅ IMPLEMENTATION COMPLETE

All 5 screens, 3 services, Firebase integration, and notifications are fully implemented and ready to use!
  - Select your support email
  - Click "Save"

## 🌐 Configure Google OAuth

- [ ] Go to [Google Cloud Console](https://console.cloud.google.com/)
- [ ] Select your Firebase project
- [ ] Go to **APIs & Services** → **OAuth consent screen**
- [ ] Set User Type to "External"
- [ ] Fill in:
  - App name: Runsense
  - User support email: your@email.com
  - Developer contact: your@email.com
- [ ] Add scopes: `userinfo.email`, `userinfo.profile`
- [ ] Go to **APIs & Services** → **Credentials**
- [ ] Click **Create Credentials** → **OAuth 2.0 Client ID**
- [ ] Select **Android**
- [ ] Copy Web Client ID

## 📝 Update App Configuration

- [ ] Run in terminal: `./gradlew signingReport`
- [ ] Copy the **SHA1** value
- [ ] Go back to Google Cloud Console → **Credentials**
- [ ] Paste SHA1 into Android credential
- [ ] In Android Studio, open `app/src/main/res/values/strings.xml`
- [ ] Replace `YOUR_WEB_CLIENT_ID_HERE` with your **Web Client ID**

```xml
<!-- File: app/src/main/res/values/strings.xml -->
<string name="default_web_client_id">paste_your_web_client_id_here</string>
```

## ✅ Build & Test

- [ ] In Android Studio: **File** → **Sync Now**
- [ ] Build: **Build** → **Make Project** (or `./gradlew build`)
- [ ] Run: Click **Run** ▶️ or press `Shift + F10`
- [ ] Test **Login** screen
- [ ] Test **Sign Up** screen
- [ ] Test **Google Sign-In**
- [ ] Test logout from **Home** screen

## 🎯 Test Scenarios

### Email/Password Flow
```
1. Sign Up with email/password
2. Verify account created in Firebase Console
3. Login with credentials
4. Verify user info displayed on Home screen
5. Logout and verify redirect to Login
```

### Google Sign-In Flow
```
1. Click "Login With Google Account"
2. Select Google account from list
3. Verify successful login to Home screen
4. Check user name and email displayed
5. Test logout
```

### Password Reset
```
1. On Login screen, click "Forgot Password?"
2. Enter email
3. Check email for reset link (in test mode, Firebase sends to inbox)
4. Reset password
5. Login with new password
```

## 💾 Files Modified/Created

### ✅ Created Files
- `app/src/main/java/com/example/miniproject/LoginActivity.kt`
- `app/src/main/java/com/example/miniproject/SignupActivity.kt`
- `app/src/main/java/com/example/miniproject/OtpActivity.kt`
- `app/src/main/java/com/example/miniproject/HomeActivity.kt`
- `app/src/main/res/layout/activity_login.xml`
- `app/src/main/res/layout/activity_signup.xml`
- `app/src/main/res/layout/activity_otp.xml`
- `app/src/main/res/layout/activity_home.xml`
- `app/src/main/res/drawable/button_background.xml`
- `app/src/main/res/drawable/edittext_background.xml`
- `app/src/main/res/drawable/login_background.xml`
- `FIREBASE_SETUP.md` - Full setup guide
- `IMPLEMENTATION_SUMMARY.md` - What was implemented

### ✅ Modified Files
- `app/build.gradle.kts` - Added Firebase & Google Play Services
- `build.gradle.kts` - Added Google Services plugin
- `gradle/libs.versions.toml` - Added dependency versions
- `app/src/main/AndroidManifest.xml` - Added activities & permissions
- `app/src/main/java/com/example/miniproject/MainActivity.kt` - Made it a router
- `app/src/main/res/values/strings.xml` - Added web client ID

### ⚠️ Still Needed
- `app/google-services.json` ← Download from Firebase Console

## 🚀 Architecture Overview

```
App Launch
    ↓
MainActivity (checks if user logged in)
    ├─ Logged In? → HomeActivity
    └─ Not Logged In? → LoginActivity
    
LoginActivity (Email or Google)
    ├ Email Login → (Firebase Auth) → HomeActivity
    └ Google Login → (OAuth 2.0) → HomeActivity
    
SignupActivity (Email or Google)
    ├ Email Registration → HomeActivity
    └ Google Sign-Up → HomeActivity

HomeActivity (Authenticated)
    ├ Display User Info (Name & Email)
    └ Logout → LoginActivity
```

## 📞 Troubleshooting

**Build fails with "Plugin id com.google.gms.google-services not found"**
- Solution: Make sure you added `id("com.google.gms.google-services")` to `app/build.gradle.kts`

**Google Sign-In button not showing**
- Solution: Make sure Play Services are downloaded: **Tools** → **SDK Manager** → Install "Google Repository"

**"invalid_client" error during Google Sign-In**
- Solution: Verify SHA-1 certificate fingerprint matches in Google Cloud Console

**"INVALID_API_KEY" error**
- Solution: Make sure `google-services.json` is in `app/` folder before building

## 📱 Default Test Account

For development/testing purposes, you can use any Google account or create test accounts in Firebase Console:
- Firebase → Authentication → Users → Create custom user

## ✨ Next Steps After Setup

1. **Customize UI** - Modify layouts to match your branding
2. **Add Validation** - Add more input validation (email format, password strength)
3. **Add Database** - Store user data in Firestore
4. **Add Profile Management** - Edit user profile, change password
5. **Add Social Sharing** - Share app with friends
6. **Analytics** - Add Firebase Analytics to track user behavior

---

**Need Help?**
- Check `FIREBASE_SETUP.md` for detailed instructions
- Check `IMPLEMENTATION_SUMMARY.md` for what was implemented
- Visit [Firebase Docs](https://firebase.google.com/docs)
