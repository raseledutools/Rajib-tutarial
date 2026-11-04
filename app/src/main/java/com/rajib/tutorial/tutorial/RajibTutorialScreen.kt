package com.rajib.tutorial.tutorial

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*


// ============================================================
//  Rajib Tutorial — User Side
//
//  Flow:
//    RajibTutorialRoot
//      → CourseListScreen   (enrolled courses)
//          → VideoListScreen
//              → YouTubePlayerScreen
//
//  Firestore:
//    tutorial_courses/{id}/
//        courseName, title, className, subject, description
//        videos: [{id, title, youtubeUrl, durationMinutes}]
//    tutorial_users/{uid}/
//        enrolledCourses: ["course_001", ...]
//        role: "admin"  ← শুধু admin এর জন্য (Firestore console থেকে set)
// ============================================================

import android.app.Activity
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.viewinterop.AndroidView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

// ── Colours ──────────────────────────────────────────────────
internal val TutBg      = Color(0xFF0F1923)
internal val TutCard    = Color(0xFF1A2535)
internal val TutAccent  = Color(0xFF4A9EFF)
internal val TutAccent2 = Color(0xFF7B5CF5)
internal val TutText    = Color(0xFFE8EDF2)
internal val TutSub     = Color(0xFF8A9BB0)
internal val TutGreen   = Color(0xFF34C98A)
internal val TutRed     = Color(0xFFFF6B6B)

// ── Data models ──────────────────────────────────────────────

data class TutorialVideo(
    val id: String = "",
    val title: String = "",
    val youtubeUrl: String = "",       // full YouTube URL বা video ID
    val durationMinutes: Int = 0
)

data class TutorialCourse(
    val id: String = "",
    val courseName: String = "",
    val title: String = "",
    val className: String = "",
    val subject: String = "",
    val description: String = "",
    val videos: List<TutorialVideo> = emptyList()
)

// YouTube URL থেকে video ID বের করো
fun extractYouTubeId(url: String): String {
    if (url.isBlank()) return ""
    // Already an ID (11 chars, no slash/dot)
    if (url.length == 11 && !url.contains("/") && !url.contains(".")) return url
    // youtu.be/ID
    val shortPattern = Regex("""youtu\.be/([^?&\s]{11})""")
    shortPattern.find(url)?.groupValues?.get(1)?.let { return it }
    // youtube.com/watch?v=ID
    val longPattern = Regex("""[?&]v=([^&\s]{11})""")
    longPattern.find(url)?.groupValues?.get(1)?.let { return it }
    // youtube.com/embed/ID
    val embedPattern = Regex("""embed/([^?&\s]{11})""")
    embedPattern.find(url)?.groupValues?.get(1)?.let { return it }
    return url.takeLast(11) // fallback
}

// ── Root ─────────────────────────────────────────────────────

@Composable
fun RajibTutorialRoot(onBack: () -> Unit, updateState: com.rajib.tutorial.UpdateState? = null, onUpdateBannerClick: () -> Unit = {}) {
    var selectedCourse by remember { mutableStateOf<TutorialCourse?>(null) }
    var selectedVideo  by remember { mutableStateOf<TutorialVideo?>(null) }
    var isAdmin        by remember { mutableStateOf(false) }
    var showAdmin      by remember { mutableStateOf(false) }

    when {
        showAdmin -> TutorialAdminScreen(onBack = { showAdmin = false })

        selectedVideo != null -> YouTubePlayerScreen(
            video  = selectedVideo!!,
            onBack = { selectedVideo = null }
        )
        selectedCourse != null -> VideoListScreen(
            course        = selectedCourse!!,
            onVideoClick  = { selectedVideo = it },
            onBack        = { selectedCourse = null }
        )
        else -> CourseListScreen(
            updateState = updateState,
            onUpdateBannerClick = onUpdateBannerClick,
            isAdmin       = isAdmin,
            onAdminReady  = { isAdmin = it },
            onCourseClick = { selectedCourse = it },
            onAdminPanel  = { showAdmin = true },
            onBack        = onBack
        )
    }
}

// ── Course List ───────────────────────────────────────────────

@Composable
fun CourseListScreen(
    updateState: com.rajib.tutorial.UpdateState? = null,
    onUpdateBannerClick: () -> Unit = {},
    isAdmin: Boolean,
    onAdminReady: (Boolean) -> Unit,
    onCourseClick: (TutorialCourse) -> Unit,
    onAdminPanel: () -> Unit,
    onBack: () -> Unit
) {
    val db          = FirebaseFirestore.getInstance()
    val auth        = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser
    val uid         = currentUser?.uid ?: ""
    val userEmail   = currentUser?.email ?: ""

    var courses  by remember { mutableStateOf<List<TutorialCourse>>(emptyList()) }
    var loading  by remember { mutableStateOf(true) }
    var adminVal by remember { mutableStateOf(false) }

    LaunchedEffect(uid) {
        try {
            var enrolledIds: List<String> = emptyList()
            if (uid.isNotEmpty()) {
                val userDoc = db.collection("tutorial_users").document(uid).get().await()
                adminVal = userDoc.getString("role") == "admin"
                onAdminReady(adminVal)
                if (!adminVal) {
                    @Suppress("UNCHECKED_CAST")
                    enrolledIds = userDoc.get("enrolledCourses") as? List<String> ?: emptyList()
                }
            }
            val loaded = mutableListOf<TutorialCourse>()
            val snap   = db.collection("tutorial_courses").get().await()
            for (doc in snap.documents) {
                if (!adminVal && !enrolledIds.contains(doc.id)) continue
                @Suppress("UNCHECKED_CAST")
                val vr = doc.get("videos") as? List<Map<String, Any>> ?: emptyList()
                loaded.add(TutorialCourse(
                    id          = doc.id,
                    courseName  = doc.getString("courseName") ?: "",
                    title       = doc.getString("title") ?: "",
                    className   = doc.getString("className") ?: "",
                    subject     = doc.getString("subject") ?: "",
                    description = doc.getString("description") ?: "",
                    videos      = vr.map { v -> TutorialVideo(
                        id              = v["id"] as? String ?: "",
                        title           = v["title"] as? String ?: "",
                        youtubeUrl      = v["youtubeUrl"] as? String ?: "",
                        durationMinutes = (v["durationMinutes"] as? Long)?.toInt() ?: 0
                    )}
                ))
            }
            courses = loaded
        } catch (_: Exception) {}
        loading = false
    }

    Column(
        modifier = Modifier.fillMaxSize().background(TutBg)
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF0D1F38), TutBg)))
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = TutText)
                }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text("Rajib Tutorial", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TutText)
                    Text(userEmail, fontSize = 12.sp, color = TutSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (adminVal) {
                    Box(
                        modifier = Modifier
                            .background(TutAccent2.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .clickable { onAdminPanel() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AdminPanelSettings, null, tint = TutAccent2, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Admin", fontSize = 12.sp, color = TutAccent2, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TutAccent)
            }
        } else if (courses.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.VideoLibrary, null, tint = TutSub, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("কোনো course enrolled নেই", color = TutSub, fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Admin এর কাছে access চাও", color = TutSub.copy(alpha = 0.6f), fontSize = 13.sp)
                }
            }
        } else {
            // Update banner — নতুন version থাকলে দেখাবে
            com.rajib.tutorial.UpdateBanner(
                state         = updateState ?: return@Column,
                onUpdateClick = onUpdateBannerClick,
                onDismiss     = { updateState.dismissed = true }
            )
            LazyColumn(
                modifier       = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        if (adminVal) "সব Courses (${courses.size})" else "আমার Courses (${courses.size})",
                        fontSize   = 14.sp,
                        color      = TutSub,
                        fontWeight = FontWeight.Medium,
                        modifier   = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(courses) { course ->
                    CourseCard(course = course, onClick = { onCourseClick(course) })
                }
            }
        }
    }
}

@Composable
private fun CourseCard(course: TutorialCourse, onClick: () -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = TutCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        Brush.linearGradient(listOf(TutAccent.copy(0.3f), TutAccent2.copy(0.3f))),
                        RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.OndemandVideo, null, tint = TutAccent, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                if (course.courseName.isNotEmpty())
                    Text(course.courseName, fontSize = 12.sp, color = TutAccent2, fontWeight = FontWeight.Medium)
                Text(course.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TutText)
                val meta = listOfNotNull(
                    course.className.ifEmpty { null },
                    course.subject.ifEmpty { null }
                ).joinToString(" • ")
                if (meta.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(meta, fontSize = 12.sp, color = TutSub)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayCircle, null, tint = TutGreen, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${course.videos.size} টি video", fontSize = 12.sp, color = TutGreen)
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = TutSub)
        }
    }
}

// ── Video List ────────────────────────────────────────────────

@Composable
fun VideoListScreen(
    course: TutorialCourse,
    onVideoClick: (TutorialVideo) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(TutBg)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF0D1F38), TutBg)))
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = TutText)
                }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(course.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TutText,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val meta = listOfNotNull(
                        course.className.ifEmpty { null },
                        course.subject.ifEmpty { null }
                    ).joinToString(" • ")
                    Text(
                        if (meta.isNotEmpty()) meta else "${course.videos.size} টি video",
                        fontSize = 12.sp, color = TutSub
                    )
                }
            }
        }

        if (course.videos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.VideoLibrary, null, tint = TutSub, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("এখনো কোনো video নেই", color = TutSub)
                }
            }
        } else {
            LazyColumn(
                modifier       = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("${course.videos.size} টি video", fontSize = 13.sp, color = TutSub,
                        modifier = Modifier.padding(bottom = 4.dp))
                }
                items(course.videos.withIndex().toList()) { (i, video) ->
                    VideoListItem(index = i + 1, video = video, onClick = { onVideoClick(video) })
                }
            }
        }
    }
}

@Composable
private fun VideoListItem(index: Int, video: TutorialVideo, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = TutCard)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(38.dp).background(TutAccent.copy(0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("$index", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TutAccent)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(video.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TutText)
                if (video.durationMinutes > 0) {
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, tint = TutSub, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("${video.durationMinutes} মিনিট", fontSize = 11.sp, color = TutSub)
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(TutGreen.copy(0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = TutGreen, modifier = Modifier.size(22.dp))
            }
        }
    }
}

// ── YouTube Player Screen ─────────────────────────────────────

@Composable
fun YouTubePlayerScreen(video: TutorialVideo, onBack: () -> Unit) {
    val context  = LocalContext.current
    val videoId  = extractYouTubeId(video.youtubeUrl)

    // FLAG_SECURE — screenshot ও screen record block করে
    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black)
    ) {
        // Top bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }
                Text(
                    video.title,
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = Color.White,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier.weight(1f).padding(end = 8.dp)
                )
            }
        }

        if (videoId.isBlank()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ErrorOutline, null, tint = TutRed, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Video পাওয়া যায়নি", color = Color.White, fontSize = 16.sp)
                    Text("Admin সঠিক YouTube URL দেয়নি।", color = TutSub, fontSize = 13.sp)
                }
            }
            return@Column
        }

        // YouTube embed — 16:9 ratio
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    webViewClient    = WebViewClient()
                    webChromeClient  = WebChromeClient()
                    settings.apply {
                        javaScriptEnabled       = true
                        domStorageEnabled        = true
                        mediaPlaybackRequiresUserGesture = false
                        loadWithOverviewMode     = true
                        useWideViewPort          = true
                        mixedContentMode         = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }
                    // YouTube embed HTML — autoplay, no related videos, no download option
                    val html = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
                            <style>
                                * { margin:0; padding:0; box-sizing:border-box; background:#000; }
                                body { background:#000; overflow:hidden; }
                                .container { position:relative; width:100%; padding-top:56.25%; }
                                iframe { position:absolute; top:0; left:0; width:100%; height:100%;
                                         border:none; }
                            </style>
                        </head>
                        <body>
                            <div class="container">
                                <iframe
                                    src="https://www.youtube.com/embed/$videoId?autoplay=1&rel=0&modestbranding=1&playsinline=1"
                                    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                                    allowfullscreen>
                                </iframe>
                            </div>
                        </body>
                        </html>
                    """.trimIndent()
                    loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        )

        // Video info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(video.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (video.durationMinutes > 0) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = TutSub, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${video.durationMinutes} মিনিট", fontSize = 13.sp, color = TutSub)
                }
            }

            Spacer(Modifier.height(16.dp))

            // YouTube notice
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A2E), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Lock, null, tint = TutAccent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "এই video শুধু এই app এর মধ্যে দেখা যাবে।",
                    fontSize = 12.sp,
                    color    = TutSub
                )
            }
        }
    }
}
