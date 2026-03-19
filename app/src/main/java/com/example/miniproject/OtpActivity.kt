package com.example.miniproject

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class OtpActivity : AppCompatActivity() {

    private lateinit var etUsername: EditText
    private lateinit var etOtp: EditText
    private lateinit var btnSendOTP: Button
    private lateinit var btnVerifyOTP: Button
    private lateinit var googleSignUpOtpButton: SignInButton
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private var generatedOtp = ""
    private val RC_SIGN_IN = 122

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_otp)

        initViews()
        initFirebase()
        setupClickListeners()
    }

    private fun initViews() {
        etUsername = findViewById(R.id.etOtpUsername)
        etOtp = findViewById(R.id.etOtp)
        btnSendOTP = findViewById(R.id.btnSendOTP)
        btnVerifyOTP = findViewById(R.id.btnVerifyOTP)
        googleSignUpOtpButton = findViewById(R.id.googleSignUpOtpButton)
    }

    private fun initFirebase() {
        firebaseAuth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun setupClickListeners() {
        btnSendOTP.setOnClickListener {
            val username = etUsername.text.toString().trim()
            if (username.isEmpty()) {
                Toast.makeText(this, "Please enter username", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendOtp(username)
        }

        btnVerifyOTP.setOnClickListener {
            val otp = etOtp.text.toString().trim()
            if (otp.isEmpty()) {
                Toast.makeText(this, "Please enter OTP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            verifyOtp(otp)
        }

        googleSignUpOtpButton.setOnClickListener {
            signUpWithGoogle()
        }
    }

    private fun sendOtp(username: String) {
        // Generate a random 6-digit OTP
        generatedOtp = (100000..999999).random().toString()

        Toast.makeText(
            this,
            "OTP sent to $username\nDemo OTP: $generatedOtp",
            Toast.LENGTH_LONG
        ).show()

        // In production, you would send this via email/SMS
        // For demo purposes, we're just showing it
    }

    private fun verifyOtp(enteredOtp: String) {
        if (enteredOtp == generatedOtp) {
            Toast.makeText(this, "OTP Verified Successfully", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        } else {
            Toast.makeText(this, "Invalid OTP. Please try again", Toast.LENGTH_SHORT).show()
        }
    }

    private fun signUpWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.result
                if (account != null) {
                    firebaseAuthWithGoogle(account.idToken!!)
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Google sign up failed: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        lifecycleScope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                firebaseAuth.signInWithCredential(credential).await()
                Toast.makeText(this@OtpActivity, "Google Sign up Successful", Toast.LENGTH_SHORT)
                    .show()
                startActivity(Intent(this@OtpActivity, HomeActivity::class.java))
                finish()
            } catch (e: Exception) {
                Toast.makeText(
                    this@OtpActivity,
                    "Firebase auth failed: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
