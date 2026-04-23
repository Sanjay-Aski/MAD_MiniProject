    plugins {
        alias(libs.plugins.android.application)
        id("com.google.gms.google-services")
        id("com.google.devtools.ksp")
    }

    android {
        namespace = "com.example.miniproject"
        compileSdk {
            version = release(36)
        }

        defaultConfig {
            applicationId = "com.example.miniproject"
            minSdk = 24
            targetSdk = 36
            versionCode = 1
            versionName = "1.0"

            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        buildTypes {
            release {
                isMinifyEnabled = false
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            }
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
    }

    dependencies {
        implementation(libs.androidx.core.ktx)
        implementation(libs.androidx.appcompat)
        implementation(libs.material)
        implementation(libs.androidx.activity)
        implementation(libs.androidx.constraintlayout)

        // Firebase
        implementation(platform(libs.firebase.bom))
        implementation(libs.firebase.auth)
        implementation(libs.firebase.firestore)
        implementation(libs.firebase.database)
        implementation(libs.play.services.auth)

        // Kotlin coroutines
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

        // Location Services (keep for GPS tracking)
        implementation("com.google.android.gms:play-services-location:21.1.0")

        // OpenStreetMap (Free alternative to Google Maps)
        implementation("org.osmdroid:osmdroid-android:6.1.17")

        // Fragment and Navigation
        implementation("androidx.fragment:fragment-ktx:1.6.2")
        implementation("androidx.navigation:navigation-fragment-ktx:2.7.6")
        implementation("androidx.navigation:navigation-ui-ktx:2.7.6")

        // Charts
        implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

        // Gson for JSON serialization
        implementation("com.google.code.gson:gson:2.10.1")

        // Lifecycle
        implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
        implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")

        // Room for local database
        implementation("androidx.room:room-runtime:2.6.1")
        implementation("androidx.room:room-ktx:2.6.1")
        ksp("androidx.room:room-compiler:2.6.1")

        testImplementation(libs.junit)
        androidTestImplementation(libs.androidx.junit)
        androidTestImplementation(libs.androidx.espresso.core)
    }