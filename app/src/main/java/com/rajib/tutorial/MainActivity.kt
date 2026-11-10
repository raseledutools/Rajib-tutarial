package com.rajib.tutorial

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.rajib.tutorial.tutorial.RajibTutorialRoot
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

private val TutBg     = Color(0xFF0F1923)
private val TutCard   = Color(0xFF1A2535)
private val TutAccent = Color(0xFF4A9EFF)
private val TutText   = Color(0xFFE8EDF2)
private val TutSub    = Color(0xFF8A9BB0)
private val TutRed    = Color(0xFFFF6B6B)
private val TutGreen  = Color(0xFF34C98A)
private val TutPurple = Color(0xFF7B5CF5)

private const val ADMIN_WHATSAPP = "8801566054963"   // country code + number

private sealed class AppScreen {
    object Login : AppScreen()
    object Main  : AppScreen()
}

// ═══════════════════════════════════════════════════════════════
//  DeviceInfo — Firebase তে store হওয়া device information
// ═══════════════════════════════════════════════════════════════
data class DeviceInfo(
    val token     : String = "",
    val model     : String = "",   // phone model name
    val osVersion : String = "",   // Android version
    val loginTime : Any?   = null  // server timestamp
)

// ═══════════════════════════════════════════════════════════════
//  SessionManager — single-device login management
// ═══════════════════════════════════════════════════════════════
object SessionManager {
    private const val PREFS     = "session_prefs"
    private const val KEY_TOKEN = "session_token"

    fun getOrCreateToken(context: android.content.Context): String {
        val prefs = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        var token = prefs.getString(KEY_TOKEN, null)
        if (token.isNullOrBlank()) {
            token = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_TOKEN, token).apply()
        }
        return token
    }

    fun getDeviceModel(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model        = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) model
               else "$manufacturer $model"
    }

    fun getOsVersion(): String = "Android ${Build.VERSION.RELEASE}"

    /**
     * Login সফল হলে Firebase তে এই device এর token + info লিখে রাখো।
     * পুরনো device এর token overwrite হয় → সে kick out পাবে।
     */
    suspend fun registerSession(context: android.content.Context, uid: String) {
        val token = getOrCreateToken(context)
        FirebaseFirestore.getInstance()
            .collection("tutorial_users")
            .document(uid)
            .update(mapOf(
                "sessionToken"       to token,
                "sessionDeviceModel" to getDeviceModel(),
                "sessionOsVersion"   to getOsVersion(),
                "sessionUpdated"     to FieldValue.serverTimestamp()
            )).await()
    }

    /**
     * Login করার আগে চেক: Firebase তে কোনো active session আছে কিনা।
     * Returns DeviceInfo if another device is active, null if free to login.
     */
    suspend fun checkExistingSession(context: android.content.Context, uid: String): DeviceInfo? {
        return try {
            val doc = FirebaseFirestore.getInstance()
                .collection("tutorial_users")
                .document(uid)
                .get().await()
            val storedToken = doc.getString("sessionToken") ?: return null
            val myToken     = getOrCreateToken(context)
            if (storedToken == myToken) return null   // এই device ই আগে login করেছিল
            // অন্য device এর info
            DeviceInfo(
                token     = storedToken,
                model     = doc.getString("sessionDeviceModel") ?: "অজানা device",
                osVersion = doc.getString("sessionOsVersion") ?: "",
                loginTime = doc.get("sessionUpdated")
            )
        } catch (_: Exception) {
            null
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  MainActivity
// ═══════════════════════════════════════════════════════════════
class MainActivity : ComponentActivity() {

    private var sessionListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent { MaterialTheme { RajibTutorialApp(onForceLogout = { forceLogout() }) } }
    }

    // reason: "blocked" | "session"
    fun startSessionWatch(uid: String, onInvalidSession: (reason: String) -> Unit) {
        sessionListener?.remove()
        val myToken = SessionManager.getOrCreateToken(this)
        sessionListener = FirebaseFirestore.getInstance()
            .collection("tutorial_users")
            .document(uid)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                // Block check — admin block করলে realtime kick
                val isBlocked = snap.getBoolean("deviceBlocked") ?: false
                if (isBlocked) { onInvalidSession("blocked"); return@addSnapshotListener }
                // Session token check
                val storedToken = snap.getString("sessionToken") ?: return@addSnapshotListener
                if (storedToken != myToken) {
                    onInvalidSession("session")
                }
            }
    }

    fun stopSessionWatch() {
        sessionListener?.remove()
        sessionListener = null
    }

    private fun forceLogout() {
        stopSessionWatch()
        FirebaseAuth.getInstance().signOut()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSessionWatch()
    }
}

// ═══════════════════════════════════════════════════════════════
//  RajibTutorialApp — root composable
// ═══════════════════════════════════════════════════════════════
@Composable
fun RajibTutorialApp(onForceLogout: () -> Unit = {}) {
    val auth     = FirebaseAuth.getInstance()
    val context  = LocalContext.current
    val activity = context as? MainActivity

    var screen by remember {
        mutableStateOf<AppScreen>(
            if (auth.currentUser != null) AppScreen.Main else AppScreen.Login
        )
    }

    var showKickedDialog by remember { mutableStateOf(false) }
    var kickReason       by remember { mutableStateOf("session") } // "session" | "blocked"
    val updateState      = remember { UpdateState() }

    LaunchedEffect(screen) {
        if (screen is AppScreen.Main) {
            val uid = auth.currentUser?.uid
            if (uid != null) {
                // Check block status once on enter
                try {
                    val doc = FirebaseFirestore.getInstance()
                        .collection("tutorial_users").document(uid).get().await()
                    if (doc.getBoolean("deviceBlocked") == true) {
                        kickReason = "blocked"; showKickedDialog = true; return@LaunchedEffect
                    }
                } catch (_: Exception) {}

                activity?.startSessionWatch(uid) { reason ->
                    kickReason       = reason
                    showKickedDialog = true
                }
            }
            if (!updateState.checking) {
                updateState.checking = true
                val release = UpdateManager.fetchLatestRelease()
                if (release != null) {
                    updateState.release   = release
                    val hasUpd  = UpdateManager.hasUpdate(context, release)
                    val skipped = UpdateManager.isVersionSkipped(context, release.versionCode)
                    updateState.hasUpdate = hasUpd && !skipped
                }
                updateState.checking = false
            }
        } else {
            activity?.stopSessionWatch()
        }
    }

    // ── Kicked-out dialog ─────────────────────────────────────
    if (showKickedDialog) {
        val isBlocked = kickReason == "blocked"
        AlertDialog(
            onDismissRequest = { },
            icon  = {
                Icon(
                    if (isBlocked) Icons.Default.Block else Icons.Default.DevicesOther,
                    null,
                    tint     = TutRed,
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text(
                    if (isBlocked) "Account Block করা হয়েছে" else "অন্য device এ login হয়েছে",
                    color      = TutText,
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center
                )
            },
            text  = {
                if (isBlocked) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "তোমার account টি admin দ্বারা block করা হয়েছে।\n\n" +
                            "যোগাযোগ করো:",
                            color = TutSub, fontSize = 14.sp, lineHeight = 20.sp
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TutGreen.copy(0.1f), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Chat, null, tint = TutGreen, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("WhatsApp: 01566054963", fontSize = 14.sp, color = TutText, fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    Text(
                        "তোমার account অন্য কোনো device থেকে login করা হয়েছে।\n\n" +
                        "একসাথে দুটো device এ login করা যাবে না।\n\n" +
                        "তুমি যদি নিজে করনি তাহলে password পরিবর্তন করো।",
                        color = TutSub, fontSize = 14.sp, lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showKickedDialog = false; onForceLogout(); screen = AppScreen.Login },
                    colors  = ButtonDefaults.buttonColors(containerColor = TutRed)
                ) { Text("ঠিক আছে, Logout", color = Color.White) }
            },
            containerColor = TutCard,
            shape          = RoundedCornerShape(20.dp)
        )
    }

    when (screen) {
        is AppScreen.Login -> LoginScreen(onLoginSuccess = { screen = AppScreen.Main })
        is AppScreen.Main  -> {
            Box(Modifier.fillMaxSize()) {
                RajibTutorialRoot(
                    onBack              = { },
                    updateState         = updateState,
                    onUpdateBannerClick = { updateState.showDialog = true }
                )
                UpdateDialog(
                    state     = updateState,
                    onDismiss = { updateState.showDialog = false; updateState.dismissed = true }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  LoginScreen
// ═══════════════════════════════════════════════════════════════
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val auth    = FirebaseAuth.getInstance()
    val scope   = rememberCoroutineScope()
    val context = LocalContext.current

    var email    by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var loading  by remember { mutableStateOf(false) }
    var error    by remember { mutableStateOf("") }

    // ── Active-session warning dialog ─────────────────────────
    var pendingUid          by remember { mutableStateOf("") }
    var showSessionWarning  by remember { mutableStateOf(false) }
    var existingDevice      by remember { mutableStateOf<DeviceInfo?>(null) }

    // ── Force-login helper ────────────────────────────────────
    suspend fun doRegisterAndLogin() {
        SessionManager.registerSession(context, pendingUid)
        onLoginSuccess()
    }

    // ── Active-session warning dialog ─────────────────────────
    if (showSessionWarning && existingDevice != null) {
        AlertDialog(
            onDismissRequest = { showSessionWarning = false; loading = false },
            icon  = { Icon(Icons.Default.PhoneAndroid, null, tint = TutAccent, modifier = Modifier.size(40.dp)) },
            title = { Text("অন্য device এ login আছে", color = TutText, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Current device info box
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TutAccent.copy(0.1f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Smartphone, null, tint = TutAccent, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(existingDevice!!.model, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TutText)
                            if (existingDevice!!.osVersion.isNotEmpty())
                                Text(existingDevice!!.osVersion, fontSize = 12.sp, color = TutSub)
                        }
                    }
                    Text(
                        "উপরের device এ এই account টি এখন active আছে।\n\n" +
                        "যদি এই device এ login করো তাহলে ওই device টি automatic logout হয়ে যাবে।\n\n" +
                        "এগিয়ে যাবেন?",
                        color = TutSub, fontSize = 13.sp, lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSessionWarning = false
                        scope.launch {
                            try { doRegisterAndLogin() }
                            catch (e: Exception) { error = "Login ব্যর্থ।" }
                            loading = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TutAccent)
                ) { Text("হ্যাঁ, এখানে Login করো", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showSessionWarning = false; loading = false }) {
                    Text("বাতিল", color = TutSub)
                }
            },
            containerColor = TutCard,
            shape          = RoundedCornerShape(20.dp)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0D1F38), TutBg, Color(0xFF0A1420)))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        Brush.linearGradient(listOf(TutAccent.copy(0.3f), TutPurple.copy(0.3f))),
                        RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayCircle, null, tint = TutAccent, modifier = Modifier.size(48.dp))
            }

            Spacer(Modifier.height(4.dp))
            Text("Rajib Tutorial", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TutText)
            Text("Sign in to access your courses", fontSize = 14.sp, color = TutSub)
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value           = email,
                onValueChange   = { email = it; error = "" },
                label           = { Text("Email", color = TutSub) },
                leadingIcon     = { Icon(Icons.Default.Email, null, tint = TutAccent) },
                modifier        = Modifier.fillMaxWidth(),
                shape           = RoundedCornerShape(14.dp),
                colors          = loginFieldColors(),
                singleLine      = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            OutlinedTextField(
                value                = password,
                onValueChange        = { password = it; error = "" },
                label                = { Text("Password", color = TutSub) },
                leadingIcon          = { Icon(Icons.Default.Lock, null, tint = TutAccent) },
                trailingIcon         = {
                    IconButton(onClick = { showPass = !showPass }) {
                        Icon(
                            if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null, tint = TutSub
                        )
                    }
                },
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                modifier        = Modifier.fillMaxWidth(),
                shape           = RoundedCornerShape(14.dp),
                colors          = loginFieldColors(),
                singleLine      = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            if (error.isNotEmpty()) {
                Text(error, color = TutRed, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(4.dp))

            // ── Sign In button ─────────────────────────────────
            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) { error = "Email ও password দাও।"; return@Button }
                    loading = true
                    scope.launch {
                        try {
                            auth.signInWithEmailAndPassword(email.trim(), password.trim()).await()
                            val uid = auth.currentUser?.uid ?: ""
                            if (uid.isNotEmpty()) {
                                val key = email.trim().replace(".", "_")
                                FirebaseFirestore.getInstance()
                                    .collection("tutorial_user_emails").document(key)
                                    .set(mapOf("uid" to uid, "email" to email.trim())).await()
                                val userRef = FirebaseFirestore.getInstance()
                                    .collection("tutorial_users").document(uid)
                                val snap = userRef.get().await()
                                if (!snap.exists()) {
                                    userRef.set(mapOf(
                                        "email"           to email.trim(),
                                        "enrolledCourses" to emptyList<String>()
                                    )).await()
                                }

                                // ── Admin block check ──────────────────────────────
                                val userDoc2 = FirebaseFirestore.getInstance()
                                    .collection("tutorial_users").document(uid).get().await()
                                val isBlocked = userDoc2.getBoolean("deviceBlocked") ?: false
                                if (isBlocked) {
                                    auth.signOut()
                                    error = "তোমার account block করা হয়েছে। Admin এর সাথে যোগাযোগ করো।"
                                    loading = false
                                    return@launch
                                }

                                // ── Single-device check ────────────────────────────
                                val existing = SessionManager.checkExistingSession(context, uid)
                                if (existing != null) {
                                    // অন্য device active আছে → warning দেখাও
                                    pendingUid         = uid
                                    existingDevice     = existing
                                    showSessionWarning = true
                                    // loading = false টা dialog dismiss এ handle হবে
                                    return@launch
                                }
                                // কোনো active session নেই → সরাসরি register করো
                                SessionManager.registerSession(context, uid)
                            }
                            onLoginSuccess()
                        } catch (e: Exception) {
                            error = when {
                                e.message?.contains("password", ignoreCase = true) == true -> "Password ভুল।"
                                e.message?.contains("no user",  ignoreCase = true) == true -> "Email পাওয়া যায়নি।"
                                e.message?.contains("network",  ignoreCase = true) == true -> "Internet নেই।"
                                else -> "Login ব্যর্থ।"
                            }
                        }
                        if (!showSessionWarning) loading = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = TutAccent),
                enabled  = !loading
            ) {
                if (loading && !showSessionWarning)
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                else
                    Text("Sign In", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }

            Spacer(Modifier.height(4.dp))

            // ── Contact Admin via WhatsApp ─────────────────────
            ContactAdminButton(context)
        }
    }
}

// ── Contact Admin WhatsApp button ────────────────────────────
@Composable
private fun ContactAdminButton(context: android.content.Context) {
    OutlinedButton(
        onClick = {
            try {
                // WhatsApp directly (wa.me link)
                val uri = Uri.parse("https://wa.me/$ADMIN_WHATSAPP?text=Rajib+Tutorial+App+এ+access+চাই")
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
                // Fallback: regular phone dialer
                val uri = Uri.parse("tel:+$ADMIN_WHATSAPP")
                context.startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        },
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape    = RoundedCornerShape(14.dp),
        colors   = ButtonDefaults.outlinedButtonColors(contentColor = TutGreen),
        border   = androidx.compose.foundation.BorderStroke(1.dp, TutGreen.copy(0.5f))
    ) {
        Icon(Icons.Default.Chat, null, tint = TutGreen, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Contact Admin", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TutGreen)
            Text("Account নেই? WhatsApp করো", fontSize = 11.sp, color = TutSub)
        }
    }
}

@Composable
private fun loginFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = TutAccent,
    unfocusedBorderColor = TutSub.copy(0.3f),
    focusedTextColor     = TutText,
    unfocusedTextColor   = TutText,
    cursorColor          = TutAccent
)
