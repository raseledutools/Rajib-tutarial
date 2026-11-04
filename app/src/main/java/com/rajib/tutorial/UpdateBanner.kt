package com.rajib.tutorial

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff

// ============================================================
//  Update Banner — App এর ভেতরে দেখাবে
//
//  দুটো UI:
//    1. UpdateBanner — CourseList এর উপরে ছোট banner
//    2. UpdateDialog — বড় dialog, release notes সহ
// ============================================================

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Colours ──────────────────────────────────────────────────
private val UpdBg      = Color(0xFF0F1923)
private val UpdCard    = Color(0xFF1A2535)
private val UpdAccent  = Color(0xFF4A9EFF)
private val UpdGreen   = Color(0xFF34C98A)
private val UpdText    = Color(0xFFE8EDF2)
private val UpdSub     = Color(0xFF8A9BB0)
private val UpdYellow  = Color(0xFFFFD166)
private val UpdRed     = Color(0xFFFF6B6B)

// ── State holder ─────────────────────────────────────────────

class UpdateState {
    var release      by mutableStateOf<ReleaseInfo?>(null)
    var hasUpdate    by mutableStateOf(false)
    var checking     by mutableStateOf(false)
    var showDialog   by mutableStateOf(false)
    var dismissed    by mutableStateOf(false)
}

// ── Compact Banner (CourseList এর উপরে) ──────────────────────

@Composable
fun UpdateBanner(
    state: UpdateState,
    onUpdateClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val release = state.release ?: return
    if (!state.hasUpdate || state.dismissed) return

    // Pulsing dot animation
    val pulse = rememberInfiniteTransition(label = "pulse")
    val dotAlpha by pulse.animateFloat(
        initialValue   = 0.4f,
        targetValue    = 1f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(900, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot"
    )

    AnimatedVisibility(
        visible = state.hasUpdate && !state.dismissed,
        enter   = slideInVertically { -it } + fadeIn(),
        exit    = slideOutVertically { -it } + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF1A3A5C),
                            Color(0xFF1A2D4A),
                            Color(0xFF1A3A5C)
                        )
                    )
                )
                .clickable { onUpdateClick() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Pulsing dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(UpdGreen.copy(alpha = dotAlpha), CircleShape)
                )

                // Text
                Column(Modifier.weight(1f)) {
                    Text(
                        "নতুন update আছে! ${release.versionName}",
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = UpdText,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                    Text(
                        "Tap করো download করতে",
                        fontSize = 11.sp,
                        color    = UpdSub
                    )
                }

                // Update button
                Box(
                    modifier = Modifier
                        .background(UpdAccent, RoundedCornerShape(8.dp))
                        .clickable { onUpdateClick() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Download,
                            null,
                            tint     = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Update",
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = Color.White
                        )
                    }
                }

                // Dismiss
                IconButton(
                    onClick  = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        null,
                        tint     = UpdSub,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ── Full Update Dialog ────────────────────────────────────────

@Composable
fun UpdateDialog(
    state: UpdateState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val release = state.release ?: return
    if (!state.showDialog) return

    val suggestedApk = UpdateManager.suggestApk(release.apkAssets)
    val installedVersion = UpdateManager.getInstalledVersionName(context)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = UpdCard,
        shape            = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Update icon with glow effect
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(UpdGreen.copy(0.15f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.SystemUpdate,
                        null,
                        tint     = UpdGreen,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "নতুন Update পাওয়া গেছে!",
                        fontSize   = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color      = UpdText
                    )
                    Text(
                        "${release.versionName} এখন available",
                        fontSize = 13.sp,
                        color    = UpdGreen
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

                // Version comparison
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F1923), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("এখন আছে", fontSize = 11.sp, color = UpdSub)
                        Text(
                            installedVersion,
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color      = UpdText
                        )
                    }
                    Icon(
                        Icons.Default.ArrowForward,
                        null,
                        tint     = UpdAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("নতুন version", fontSize = 11.sp, color = UpdSub)
                        Text(
                            release.versionName,
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color      = UpdGreen
                        )
                    }
                }

                // Release notes
                if (release.releaseNotes.isNotBlank()) {
                    Column {
                        Text(
                            "কী নতুন আছে",
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = UpdText
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            release.releaseNotes,
                            fontSize   = 12.sp,
                            color      = UpdSub,
                            lineHeight = 18.sp,
                            maxLines   = 5,
                            overflow   = TextOverflow.Ellipsis
                        )
                    }
                }

                // Suggested APK info
                suggestedApk?.let { apk ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UpdAccent.copy(0.08f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PhoneAndroid,
                            null,
                            tint     = UpdAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "তোমার ফোনের জন্য: ${apk.name}",
                                fontSize = 12.sp,
                                color    = UpdAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "Size: ${UpdateManager.formatSize(apk.sizeBytes)}",
                                fontSize = 11.sp,
                                color    = UpdSub
                            )
                        }
                    }
                }

                // How to install info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(UpdYellow.copy(0.08f), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info,
                        null,
                        tint     = UpdYellow,
                        modifier = Modifier.size(14.dp).padding(top = 1.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "GitHub page খুলবে → তোমার ফোনের জন্য APK file download করো → Install করো।",
                        fontSize   = 11.sp,
                        color      = UpdYellow.copy(0.8f),
                        lineHeight = 16.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    UpdateManager.openReleasePage(context, release.releaseUrl)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = UpdGreen),
                shape  = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    Icons.Default.Download,
                    null,
                    modifier = Modifier.size(16.dp),
                    tint     = Color.White
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Download করো",
                    color      = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            Column {
                TextButton(onClick = {
                    UpdateManager.skipVersion(context, release.versionCode)
                    onDismiss()
                }) {
                    Text("এই version skip করো", color = UpdSub, fontSize = 12.sp)
                }
                TextButton(onClick = onDismiss) {
                    Text("পরে করব", color = UpdAccent)
                }
            }
        }
    )
}
