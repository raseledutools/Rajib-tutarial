package com.rajib.tutorial

// ============================================================
//  Rajib Tutorial App — Main Entry Point
//
//  শুধুমাত্র Course / Tutorial system।
//  RasFocus-এর কোনো feature নেই।
//
//  Flow:
//    SplashScreen
//      → LoginScreen (Firebase Email/Password)
//          → RajibTutorialRoot (CourseList → VideoList → VideoPlayer)
// ============================================================

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.rajib.tutorial.tutorial.RajibTutorialRoot
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// ── Colour tokens (same as Tutorial screens) ─────────────────
private val TutBg      = Color(0xFF0F1923)
private val TutCard    = Color(0xFF1A2535)
private val TutAccent  = Color(0xFF4A9EFF)
private val TutAccent2 = Color(0xFF7B5CF5)
private val TutText    = Color(0xFFE8EDF2)
private val TutSub     = Color(0xFF8A9BB0)
private val TutGreen   = Color(0xFF34C98A)
private val TutRed     = Color(0xFFFF6B6B)

// ── Sealed state ─────────────────────────────────────────────
private sealed class AppScreen {
    object Login   : AppScreen()
    object Main    : AppScreen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                RajibTutorialApp()
            }
        }
    }
}

@Composable
fun RajibTutorialApp() {
    val auth = FirebaseAuth.getInstance()

    // Already logged in → directly show main
    var screen by remember {
        mutableStateOf<AppScreen>(
            if (auth.currentUser != null) AppScreen.Main else AppScreen.Login
        )
    }

    when (screen) {
        is AppScreen.Login -> LoginScreen(
            onLoginSuccess = { screen = AppScreen.Main }
        )
        is AppScreen.Main  -> RajibTutorialRoot(
            onBack = { /* Root screen — back কিছু করে না */ }
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Login Screen
// ─────────────────────────────────────────────────────────────
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val auth  = FirebaseAuth.getInstance()
    val scope = rememberCoroutineScope()

    var email    by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var loading  by remember { mutableStateOf(false) }
    var error    by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0D1F38), TutBg, Color(0xFF0A1420))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Logo / icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(TutAccent.copy(alpha = 0.3f), TutAccent2.copy(alpha = 0.3f))
                        ),
                        RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint   = TutAccent,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                "Rajib Tutorial",
                fontSize    = 28.sp,
                fontWeight  = FontWeight.Bold,
                color       = TutText
            )
            Text(
                "Sign in to access your courses",
                fontSize = 14.sp,
                color    = TutSub
            )

            Spacer(Modifier.height(8.dp))

            // Email field
            OutlinedTextField(
                value          = email,
                onValueChange  = { email = it; error = "" },
                label          = { Text("Email", color = TutSub) },
                leadingIcon    = { Icon(Icons.Default.Email, null, tint = TutAccent) },
                modifier       = Modifier.fillMaxWidth(),
                shape          = RoundedCornerShape(14.dp),
                colors         = loginFieldColors(),
                singleLine     = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            // Password field
            OutlinedTextField(
                value              = password,
                onValueChange      = { password = it; error = "" },
                label              = { Text("Password", color = TutSub) },
                leadingIcon        = { Icon(Icons.Default.Lock, null, tint = TutAccent) },
                trailingIcon       = {
                    IconButton(onClick = { showPass = !showPass }) {
                        Icon(
                            if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null, tint = TutSub
                        )
                    }
                },
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                modifier    = Modifier.fillMaxWidth(),
                shape       = RoundedCornerShape(14.dp),
                colors      = loginFieldColors(),
                singleLine  = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            // Error text
            if (error.isNotEmpty()) {
                Text(
                    error,
                    color    = TutRed,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(4.dp))

            // Login button
            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        error = "Email ও password দাও।"
                        return@Button
                    }
                    loading = true
                    scope.launch {
                        try {
                            auth.signInWithEmailAndPassword(email.trim(), password.trim()).await()
                            // Register uid → email mapping so admin can find user
                            val uid = auth.currentUser?.uid ?: ""
                            if (uid.isNotEmpty()) {
                                val key = email.trim().replace(".", "_")
                                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                    .collection("tutorial_user_emails")
                                    .document(key)
                                    .set(mapOf("uid" to uid, "email" to email.trim()))
                                    .await()
                                // Ensure user doc exists
                                val userRef = com.google.firebase.firestore.FirebaseFirestore
                                    .getInstance()
                                    .collection("tutorial_users")
                                    .document(uid)
                                val snap = userRef.get().await()
                                if (!snap.exists()) {
                                    userRef.set(
                                        mapOf(
                                            "email"           to email.trim(),
                                            "enrolledCourses" to emptyList<String>()
                                        )
                                    ).await()
                                }
                            }
                            onLoginSuccess()
                        } catch (e: Exception) {
                            error = when {
                                e.message?.contains("password", ignoreCase = true) == true -> "Password ভুল।"
                                e.message?.contains("no user", ignoreCase = true) == true  -> "Email পাওয়া যায়নি।"
                                e.message?.contains("network", ignoreCase = true) == true  -> "Internet সংযোগ নেই।"
                                else -> "Login ব্যর্থ। আবার চেষ্টা করো।"
                            }
                        }
                        loading = false
                    }
                },
                modifier  = Modifier.fillMaxWidth().height(54.dp),
                shape     = RoundedCornerShape(14.dp),
                colors    = ButtonDefaults.buttonColors(containerColor = TutAccent),
                enabled   = !loading
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        color    = Color.White,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "Sign In",
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = Color.White
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "Account নেই? Admin-এর কাছে যোগাযোগ করো।",
                fontSize = 12.sp,
                color    = TutSub
            )
        }
    }
}

@Composable
private fun loginFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = TutAccent,
    unfocusedBorderColor = TutSub.copy(alpha = 0.3f),
    focusedTextColor     = TutText,
    unfocusedTextColor   = TutText,
    cursorColor          = TutAccent
)
