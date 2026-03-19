package com.example.miniproject

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SignupActivity : AppCompatActivity() {

    private lateinit var etFullName: EditText
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var etDOB: EditText
    private lateinit var etCity: EditText
    private lateinit var btnSignup: Button
    private lateinit var tvLoginHere: TextView
    private lateinit var googleSignUpButton: SignInButton
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 121

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        initViews()
        initFirebase()
        setupClickListeners()
    }

    private fun initViews() {
        etFullName = findViewById(R.id.etSignupFullName)
        etUsername = findViewById(R.id.etSignupUsername)
        etPassword = findViewById(R.id.etSignupPassword)
        etDOB = findViewById(R.id.etSignupDOB)
        etCity = findViewById(R.id.etSignupCity)
        btnSignup = findViewById(R.id.btnSignup)
        tvLoginHere = findViewById(R.id.tvLoginHere)
        googleSignUpButton = findViewById(R.id.googleSignUpButton)
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
        btnSignup.setOnClickListener {
            val fullName = etFullName.text.toString().trim()
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val dob = etDOB.text.toString().trim()
            val city = etCity.text.toString().trim()

            if (fullName.isEmpty() || username.isEmpty() || password.isEmpty() ||
                dob.isEmpty() || city.isEmpty()
            ) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            signupWithEmail(username, password, fullName)
        }

        tvLoginHere.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        googleSignUpButton.setOnClickListener {
            signUpWithGoogle()
        }
    }

    private fun signupWithEmail(email: String, password: String, fullName: String) {
        lifecycleScope.launch {
            try {
                firebaseAuth.createUserWithEmailAndPassword(email, password).await()

                val user = firebaseAuth.currentUser
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(fullName)
                    .build()

                user?.updateProfile(profileUpdates)?.await()

                Toast.makeText(this@SignupActivity, "Signup Successful", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this@SignupActivity, HomeActivity::class.java))
                finish()
            } catch (e: Exception) {
                Toast.makeText(
                    this@SignupActivity,
                    "Signup failed: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
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
                Toast.makeText(this@SignupActivity, "Google Sign up Successful", Toast.LENGTH_SHORT)
                    .show()
                startActivity(Intent(this@SignupActivity, HomeActivity::class.java))
                finish()
            } catch (e: Exception) {
                Toast.makeText(
                    this@SignupActivity,
                    "Firebase auth failed: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
