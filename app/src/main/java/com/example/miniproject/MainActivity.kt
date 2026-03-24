package com.example.miniproject

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val firebaseAuth = FirebaseAuth.getInstance()
        val user = firebaseAuth.currentUser

        if (user != null) {
            // User is logged in, go to home
            startActivity(Intent(this, HomeActivity::class.java))
        } else {
            // User is not logged in, go to login
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
    }
}
