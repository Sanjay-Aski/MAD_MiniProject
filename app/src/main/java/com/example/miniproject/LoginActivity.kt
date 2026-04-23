package com.example.miniproject

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
import android.util.Patterns
import com.example.miniproject.service.CloudDataSyncService
import com.example.miniproject.ui.dialog.EmailSelectionDialog
import com.example.miniproject.util.EmailPreferenceManager

class LoginActivity : AppCompatActivity() {

    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var tvCreateAccount: TextView
    private lateinit var tvForgotPassword: TextView
    private lateinit var googleSignInButton: SignInButton
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(Exception::class.java)
            if (account != null && account.idToken != null) {
                firebaseAuthWithGoogle(account.idToken!!)
            } else {
                Toast.makeText(this, "Google sign in failed: No account or ID token", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Google sign in failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        initViews()
        initFirebase()
        setupClickListeners()
    }

    private fun initViews() {
        etUsername = findViewById(R.id.etLoginUsername)
        etPassword = findViewById(R.id.etLoginPassword)
        btnLogin = findViewById(R.id.btnLogin)
        tvCreateAccount = findViewById(R.id.tvCreateAccount)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
        googleSignInButton = findViewById(R.id.googleSignInButton)
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
        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(username).matches()) {
                etUsername.error = "Enter a valid email"
                return@setOnClickListener
            }

            if (password.length < 6) {
                etPassword.error = "Password must be at least 6 characters"
                return@setOnClickListener
            }

            loginWithEmail(username, password)
        }

        tvCreateAccount.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }

        tvForgotPassword.setOnClickListener {
            val email = etUsername.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            resetPassword(email)
        }

        googleSignInButton.setOnClickListener {
            signInWithGoogle()
        }
    }

    private fun loginWithEmail(username: String, password: String) {
        btnLogin.isEnabled = false
        lifecycleScope.launch {
            try {
                firebaseAuth.signInWithEmailAndPassword(username, password).await()
                val user = firebaseAuth.currentUser
                
                if (user != null) {
                    // Get user's email address
                    val userEmail = user.email ?: username
                    
                    // Show email selection dialog with detected emails
                    showEmailSelectionDialog(userEmail, user.uid)
                } else {
                    Toast.makeText(
                        this@LoginActivity,
                        "Login failed: User not found",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@LoginActivity,
                    "Login failed: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                btnLogin.isEnabled = true
            }
        }
    }

    private fun showEmailSelectionDialog(detectedEmail: String, userId: String) {
        // Get any additional emails from Firebase user providers
        val emails = mutableListOf(detectedEmail)
        
        // Add emails from linked providers if available
        firebaseAuth.currentUser?.providerData?.forEach { userInfo ->
            userInfo.email?.let { email ->
                if (!emails.contains(email)) {
                    emails.add(email)
                }
            }
        }

        EmailSelectionDialog(
            context = this,
            emails = emails,
            onEmailSelected = { selectedEmail ->
                proceedWithLogin(selectedEmail, userId)
            },
            onCancel = {
                btnLogin.isEnabled = true
                Toast.makeText(
                    this,
                    "Login cancelled",
                    Toast.LENGTH_SHORT
                ).show()
            }
        ).show()
    }

    private fun proceedWithLogin(selectedEmail: String, userId: String) {
        lifecycleScope.launch {
            try {
                // Show sync progress
                Toast.makeText(
                    this@LoginActivity,
                    "📥 Syncing your data from cloud...",
                    Toast.LENGTH_SHORT
                ).show()

                // Save selected email preference
                EmailPreferenceManager.saveSelectedEmail(this@LoginActivity, selectedEmail)

                // Sync cloud data to local storage
                val syncService = CloudDataSyncService(this@LoginActivity)
                val syncSuccess = syncService.syncUserDataFromCloud(userId)

                if (syncSuccess) {
                    Toast.makeText(
                        this@LoginActivity,
                        " Login Successful ",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@LoginActivity,
                        " Login Successful ",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                // Navigate to Home Activity
                val intent = Intent(this@LoginActivity, HomeActivity::class.java)
                intent.putExtra("selected_email", selectedEmail)
                intent.putExtra("sync_success", syncSuccess)
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Toast.makeText(
                    this@LoginActivity,
                    "Error during sync: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                
                // Still proceed to home even if sync fails
                startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                finish()
            }
        }
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        lifecycleScope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                firebaseAuth.signInWithCredential(credential).await()
                
                val user = firebaseAuth.currentUser
                if (user != null) {
                    // Get user's email from Google account
                    val userEmail = user.email ?: "unknown@gmail.com"
                    
                    // Show email selection dialog
                    showEmailSelectionDialog(userEmail, user.uid)
                } else {
                    Toast.makeText(
                        this@LoginActivity,
                        "Google Login failed: User not found",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@LoginActivity,
                    "Firebase auth failed: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun resetPassword(email: String) {
        lifecycleScope.launch {
            try {
                firebaseAuth.sendPasswordResetEmail(email).await()
                Toast.makeText(
                    this@LoginActivity,
                    "Password reset email sent",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@LoginActivity,
                    "Error sending reset email: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
