package com.rajib.tutorial.tutorial

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
//  Admin Panel — Rajib Tutorial
//
//  Admin login করার নিয়ম:
//    1. App এ normal user হিসেবে login করো (email/password)
//    2. Firebase Console → Firestore → tutorial_users → {তোমার UID}
//       → role field টা "admin" করে দাও
//    3. App restart করলে Admin button দেখাবে
//
//  Admin কী করতে পারবে:
//    Courses Tab:
//      - New course বানাও (courseName, title, class, subject)
//      - Course এ YouTube video add করো (URL paste করো)
//      - Video delete করো
//      - Course delete করো
//    Users Tab:
//      - Email দিয়ে user খোঁজো
//      - Course access দাও / সরাও (switch toggle)
//
//  Firestore structure:
//    tutorial_courses/{id}/
//        courseName, title, className, subject, description
//        videos: [{id, title, youtubeUrl, durationMinutes}]
//    tutorial_users/{uid}/enrolledCourses: [courseId, ...]
//    tutorial_user_emails/{email_key}/uid
// ============================================================

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

// ── Colours ──────────────────────────────────────────────────
private val AB  = Color(0xFF0F1923)
private val AC  = Color(0xFF1A2535)
private val AA  = Color(0xFF4A9EFF)
private val AG  = Color(0xFF34C98A)
private val AR  = Color(0xFFFF6B6B)
private val AT  = Color(0xFFE8EDF2)
private val AS  = Color(0xFF8A9BB0)
private val AP  = Color(0xFF7B5CF5)

private enum class ATab { COURSES, USERS }

// ── Root ─────────────────────────────────────────────────────

@Composable
fun TutorialAdminScreen(onBack: () -> Unit) {
    var tab            by remember { mutableStateOf(ATab.COURSES) }
    var selectedCourse by remember { mutableStateOf<TutorialCourse?>(null) }

    Column(Modifier.fillMaxSize().background(AB)) {

        // Header
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF0D1F38), AB)))
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = if (selectedCourse != null) ({ selectedCourse = null }) else onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = AT)
                }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (selectedCourse != null) selectedCourse!!.title else "Admin Panel",
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AT,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (selectedCourse != null) {
                            listOfNotNull(
                                selectedCourse!!.className.ifEmpty { null },
                                selectedCourse!!.subject.ifEmpty { null }
                            ).joinToString(" • ").ifEmpty { "Rajib Tutorial" }
                        } else "Rajib Tutorial",
                        fontSize = 12.sp, color = AS
                    )
                }
                Box(
                    Modifier.background(AP.copy(0.2f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AdminPanelSettings, null, tint = AP, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Admin", fontSize = 11.sp, color = AP, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (selectedCourse != null) {
            VideoManagementScreen(
                course          = selectedCourse!!,
                onCourseUpdated = { selectedCourse = it }
            )
        } else {
            TabRow(selectedTabIndex = tab.ordinal, containerColor = AC, contentColor = AA) {
                Tab(
                    selected = tab == ATab.COURSES,
                    onClick  = { tab = ATab.COURSES },
                    text     = { Text("Courses", color = if (tab == ATab.COURSES) AA else AS) },
                    icon     = { Icon(Icons.Default.VideoLibrary, null, tint = if (tab == ATab.COURSES) AA else AS) }
                )
                Tab(
                    selected = tab == ATab.USERS,
                    onClick  = { tab = ATab.USERS },
                    text     = { Text("Users", color = if (tab == ATab.USERS) AA else AS) },
                    icon     = { Icon(Icons.Default.People, null, tint = if (tab == ATab.USERS) AA else AS) }
                )
            }
            when (tab) {
                ATab.COURSES -> CourseManagementScreen(onCourseClick = { selectedCourse = it })
                ATab.USERS   -> UserEnrollmentScreen()
            }
        }
    }
}

// ── Courses Tab ───────────────────────────────────────────────

@Composable
fun CourseManagementScreen(onCourseClick: (TutorialCourse) -> Unit) {
    val db    = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope()
    var courses      by remember { mutableStateOf<List<TutorialCourse>>(emptyList()) }
    var loading      by remember { mutableStateOf(true) }
    var showAdd      by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<TutorialCourse?>(null) }
    var snack        by remember { mutableStateOf("") }

    fun load() {
        scope.launch {
            loading = true
            try {
                val snap = db.collection("tutorial_courses").get().await()
                courses = snap.documents.mapNotNull { doc ->
                    if (!doc.exists()) return@mapNotNull null
                    @Suppress("UNCHECKED_CAST")
                    val vr = doc.get("videos") as? List<Map<String, Any>> ?: emptyList()
                    TutorialCourse(
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
                    )
                }
            } catch (_: Exception) {}
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }

    Box(Modifier.fillMaxSize()) {
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AA)
            }
        } else if (courses.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.VideoLibrary, null, tint = AS, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("কোনো course নেই", color = AS)
                    Text("+ বাটন চেপে course বানাও", color = AS.copy(0.6f), fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("মোট ${courses.size} টি course", fontSize = 13.sp, color = AS,
                        modifier = Modifier.padding(bottom = 4.dp))
                }
                items(courses, key = { it.id }) { c ->
                    AdminCourseCard(
                        course    = c,
                        onEdit    = { onCourseClick(c) },
                        onDelete  = { deleteTarget = c }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick        = { showAdd = true },
            modifier       = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = AA,
            contentColor   = Color.White
        ) { Icon(Icons.Default.Add, "নতুন Course") }

        if (snack.isNotEmpty()) {
            Snackbar(
                modifier         = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                containerColor   = AC
            ) { Text(snack, color = AT) }
            LaunchedEffect(snack) { kotlinx.coroutines.delay(2200); snack = "" }
        }
    }

    if (showAdd) {
        AddCourseDialog(
            onDismiss = { showAdd = false },
            onSave    = { cn, ti, cl, su, de ->
                scope.launch {
                    try {
                        val id = "course_${UUID.randomUUID().toString().take(8)}"
                        db.collection("tutorial_courses").document(id).set(
                            mapOf(
                                "courseName"  to cn,
                                "title"       to ti,
                                "className"   to cl,
                                "subject"     to su,
                                "description" to de,
                                "videos"      to emptyList<Any>()
                            )
                        ).await()
                        snack = "Course তৈরি হয়েছে ✓"
                        load()
                    } catch (_: Exception) { snack = "Error হয়েছে" }
                }
                showAdd = false
            }
        )
    }

    deleteTarget?.let { c ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor   = AC,
            title            = { Text("Course মুছবে?", color = AT) },
            text             = { Text("\"${c.title}\" permanently delete হবে।", color = AS) },
            confirmButton    = {
                TextButton(onClick = {
                    scope.launch {
                        db.collection("tutorial_courses").document(c.id).delete().await()
                        snack = "Deleted"
                        load()
                    }
                    deleteTarget = null
                }) { Text("মুছে দাও", color = AR) }
            },
            dismissButton    = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel", color = AA) } }
        )
    }
}

@Composable
private fun AdminCourseCard(course: TutorialCourse, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape  = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AC)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(50.dp).background(AA.copy(0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.OndemandVideo, null, tint = AA, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (course.courseName.isNotEmpty())
                    Text(course.courseName, fontSize = 11.sp, color = AP, fontWeight = FontWeight.Medium)
                Text(course.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AT,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(
                    course.className.ifEmpty { null },
                    course.subject.ifEmpty { null }
                ).joinToString(" • ")
                if (meta.isNotEmpty()) Text(meta, fontSize = 12.sp, color = AS)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayCircle, null, tint = AG, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("${course.videos.size} টি video", fontSize = 11.sp, color = AG)
                }
            }
            IconButton(onClick = onEdit)   { Icon(Icons.Default.Edit,   "Edit",   tint = AA) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = AR) }
        }
    }
}

// ── Add Course Dialog ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCourseDialog(
    onDismiss: () -> Unit,
    onSave: (courseName: String, title: String, className: String, subject: String, description: String) -> Unit
) {
    var courseName by remember { mutableStateOf("") }
    var title      by remember { mutableStateOf("") }
    var className  by remember { mutableStateOf("") }
    var subject    by remember { mutableStateOf("") }
    var desc       by remember { mutableStateOf("") }

    val classes = listOf(
        "Class 1","Class 2","Class 3","Class 4","Class 5",
        "Class 6","Class 7","Class 8","Class 9","Class 10",
        "SSC","HSC","Degree","Masters","Other"
    )
    val subjects = listOf(
        "Bangla","English","Math","Science","Physics",
        "Chemistry","Biology","ICT","History","Geography","Economics","Other"
    )
    var classExpanded   by remember { mutableStateOf(false) }
    var subjectExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = AC,
        title            = { Text("নতুন Course", color = AT, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                // Course Name
                OutlinedTextField(
                    value           = courseName,
                    onValueChange   = { courseName = it },
                    label           = { Text("Course Name *", color = AS) },
                    placeholder     = { Text("যেমন: Rajib Math Batch", color = AS.copy(0.4f), fontSize = 12.sp) },
                    modifier        = Modifier.fillMaxWidth(),
                    shape           = RoundedCornerShape(10.dp),
                    colors          = afc(),
                    singleLine      = true
                )

                // Title
                OutlinedTextField(
                    value           = title,
                    onValueChange   = { title = it },
                    label           = { Text("Course Title *", color = AS) },
                    placeholder     = { Text("যেমন: Math Master 2025", color = AS.copy(0.4f), fontSize = 12.sp) },
                    modifier        = Modifier.fillMaxWidth(),
                    shape           = RoundedCornerShape(10.dp),
                    colors          = afc(),
                    singleLine      = true
                )

                // Class dropdown
                ExposedDropdownMenuBox(
                    expanded        = classExpanded,
                    onExpandedChange = { classExpanded = it }
                ) {
                    OutlinedTextField(
                        value           = className,
                        onValueChange   = {},
                        readOnly        = true,
                        label           = { Text("Class", color = AS) },
                        trailingIcon    = { ExposedDropdownMenuDefaults.TrailingIcon(classExpanded) },
                        modifier        = Modifier.fillMaxWidth().menuAnchor(),
                        shape           = RoundedCornerShape(10.dp),
                        colors          = afc()
                    )
                    ExposedDropdownMenu(
                        expanded        = classExpanded,
                        onDismissRequest = { classExpanded = false },
                        modifier        = Modifier.background(AC)
                    ) {
                        classes.forEach { cl ->
                            DropdownMenuItem(
                                text    = { Text(cl, color = AT) },
                                onClick = { className = cl; classExpanded = false }
                            )
                        }
                    }
                }

                // Subject dropdown
                ExposedDropdownMenuBox(
                    expanded        = subjectExpanded,
                    onExpandedChange = { subjectExpanded = it }
                ) {
                    OutlinedTextField(
                        value           = subject,
                        onValueChange   = {},
                        readOnly        = true,
                        label           = { Text("Subject", color = AS) },
                        trailingIcon    = { ExposedDropdownMenuDefaults.TrailingIcon(subjectExpanded) },
                        modifier        = Modifier.fillMaxWidth().menuAnchor(),
                        shape           = RoundedCornerShape(10.dp),
                        colors          = afc()
                    )
                    ExposedDropdownMenu(
                        expanded        = subjectExpanded,
                        onDismissRequest = { subjectExpanded = false },
                        modifier        = Modifier.background(AC)
                    ) {
                        subjects.forEach { su ->
                            DropdownMenuItem(
                                text    = { Text(su, color = AT) },
                                onClick = { subject = su; subjectExpanded = false }
                            )
                        }
                    }
                }

                // Description
                OutlinedTextField(
                    value           = desc,
                    onValueChange   = { desc = it },
                    label           = { Text("বিবরণ (optional)", color = AS) },
                    modifier        = Modifier.fillMaxWidth(),
                    shape           = RoundedCornerShape(10.dp),
                    colors          = afc(),
                    maxLines        = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick  = {
                    if (courseName.isNotBlank() && title.isNotBlank())
                        onSave(courseName.trim(), title.trim(), className, subject, desc.trim())
                },
                enabled  = courseName.isNotBlank() && title.isNotBlank()
            ) { Text("তৈরি করো", color = AA, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = AS) } }
    )
}

// ── Video Management — YouTube URL paste ──────────────────────

@Composable
fun VideoManagementScreen(course: TutorialCourse, onCourseUpdated: (TutorialCourse) -> Unit) {
    val db    = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope()

    var videos       by remember { mutableStateOf(course.videos) }
    var deleteTarget by remember { mutableStateOf<TutorialVideo?>(null) }
    var snack        by remember { mutableStateOf("") }
    var showAddVideo by remember { mutableStateOf(false) }

    fun saveVideos(updated: List<TutorialVideo>) {
        scope.launch {
            try {
                db.collection("tutorial_courses").document(course.id)
                    .update("videos", updated.map { v ->
                        mapOf(
                            "id"              to v.id,
                            "title"           to v.title,
                            "youtubeUrl"      to v.youtubeUrl,
                            "durationMinutes" to v.durationMinutes
                        )
                    }).await()
                videos = updated
                onCourseUpdated(course.copy(videos = updated))
                snack  = "সেভ হয়েছে ✓"
            } catch (_: Exception) { snack = "Save হয়নি" }
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (videos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.VideoLibrary, null, tint = AS, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("এখনো কোনো video নেই", color = AS)
                    Spacer(Modifier.height(6.dp))
                    Text("+ বাটন চেপে YouTube video add করো", color = AS.copy(0.6f), fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("${videos.size} টি video", fontSize = 13.sp, color = AS,
                        modifier = Modifier.padding(bottom = 4.dp))
                }
                items(videos.withIndex().toList(), key = { it.value.id }) { (idx, v) ->
                    AdminVideoCard(idx + 1, v, onDelete = { deleteTarget = v })
                }
            }
        }

        // FAB
        FloatingActionButton(
            onClick        = { showAddVideo = true },
            modifier       = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = AG,
            contentColor   = Color.White
        ) { Icon(Icons.Default.Add, "Video add") }

        if (snack.isNotEmpty()) {
            Snackbar(
                modifier       = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp, start = 16.dp, end = 16.dp),
                containerColor = AC
            ) { Text(snack, color = AT) }
            LaunchedEffect(snack) { kotlinx.coroutines.delay(2500); snack = "" }
        }
    }

    // Add Video Dialog
    if (showAddVideo) {
        AddYouTubeVideoDialog(
            onDismiss = { showAddVideo = false },
            onSave    = { title, url, dur ->
                val newVideo = TutorialVideo(
                    id              = "vid_${UUID.randomUUID().toString().take(8)}",
                    title           = title,
                    youtubeUrl      = url,
                    durationMinutes = dur
                )
                saveVideos(videos + newVideo)
                showAddVideo = false
            }
        )
    }

    // Delete confirm
    deleteTarget?.let { v ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor   = AC,
            title            = { Text("Video মুছবে?", color = AT) },
            text             = { Text("\"${v.title}\" delete হবে।", color = AS) },
            confirmButton    = {
                TextButton(onClick = {
                    saveVideos(videos.filter { it.id != v.id })
                    deleteTarget = null
                }) { Text("মুছে দাও", color = AR) }
            },
            dismissButton    = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel", color = AA) } }
        )
    }
}

@Composable
private fun AddYouTubeVideoDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, youtubeUrl: String, durationMinutes: Int) -> Unit
) {
    var title    by remember { mutableStateOf("") }
    var url      by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var urlError by remember { mutableStateOf("") }

    // URL validate করো
    fun isValidYouTube(u: String): Boolean {
        return u.contains("youtube.com") || u.contains("youtu.be") || u.length == 11
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = AC,
        title            = { Text("YouTube Video যোগ করো", color = AT, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // YouTube URL info box
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AA.copy(0.1f), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.Info, null, tint = AA, modifier = Modifier.size(16.dp).padding(top = 1.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "YouTube video open করো → Share → Copy link → এখানে paste করো",
                        fontSize = 12.sp, color = AS, lineHeight = 18.sp
                    )
                }

                // Video Title
                OutlinedTextField(
                    value         = title,
                    onValueChange = { title = it },
                    label         = { Text("Video Title *", color = AS) },
                    placeholder   = { Text("যেমন: Lesson 1 - ভূমিকা", color = AS.copy(0.4f), fontSize = 12.sp) },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(10.dp),
                    colors        = afc(),
                    singleLine    = true,
                    leadingIcon   = { Icon(Icons.Default.Title, null, tint = AA) }
                )

                // YouTube URL
                OutlinedTextField(
                    value         = url,
                    onValueChange = { url = it; urlError = "" },
                    label         = { Text("YouTube URL *", color = AS) },
                    placeholder   = { Text("https://youtu.be/xxxxx", color = AS.copy(0.4f), fontSize = 12.sp) },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(10.dp),
                    colors        = afc(),
                    singleLine    = true,
                    leadingIcon   = { Icon(Icons.Default.Link, null, tint = AG) },
                    isError       = urlError.isNotEmpty(),
                    supportingText = if (urlError.isNotEmpty()) { { Text(urlError, color = AR, fontSize = 11.sp) } } else null
                )

                // Duration (optional)
                OutlinedTextField(
                    value         = duration,
                    onValueChange = { duration = it.filter { c -> c.isDigit() } },
                    label         = { Text("সময় (মিনিট, optional)", color = AS) },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(10.dp),
                    colors        = afc(),
                    singleLine    = true,
                    leadingIcon   = { Icon(Icons.Default.Schedule, null, tint = AS) }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!isValidYouTube(url.trim())) {
                        urlError = "সঠিক YouTube URL দাও"
                        return@TextButton
                    }
                    if (title.isBlank()) return@TextButton
                    onSave(title.trim(), url.trim(), duration.toIntOrNull() ?: 0)
                },
                enabled = title.isNotBlank() && url.isNotBlank()
            ) { Text("যোগ করো", color = AG, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = AS) } }
    )
}

@Composable
private fun AdminVideoCard(index: Int, v: TutorialVideo, onDelete: () -> Unit) {
    val videoId = extractYouTubeId(v.youtubeUrl)
    Card(
        Modifier.fillMaxWidth(),
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AC)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).background(AG.copy(0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("$index", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AG) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(v.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AT,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Link, null, tint = AG, modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(
                        if (videoId.isNotBlank()) "youtube.com/watch?v=$videoId" else "URL নেই",
                        fontSize = 11.sp,
                        color    = if (videoId.isNotBlank()) AS else AR,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (v.durationMinutes > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text("${v.durationMinutes} মিনিট", fontSize = 11.sp, color = AA)
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = AR) }
        }
    }
}

// ── Users Tab ─────────────────────────────────────────────────

@Composable
fun UserEnrollmentScreen() {
    val db    = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope()

    var email       by remember { mutableStateOf("") }
    var foundUid    by remember { mutableStateOf("") }
    var foundEmail  by remember { mutableStateOf("") }
    var enrolled    by remember { mutableStateOf<List<String>>(emptyList()) }
    var allCourses  by remember { mutableStateOf<List<TutorialCourse>>(emptyList()) }
    var searching   by remember { mutableStateOf(false) }
    var snack       by remember { mutableStateOf("") }
    var searchError by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            val snap = db.collection("tutorial_courses").get().await()
            allCourses = snap.documents.map { doc ->
                TutorialCourse(
                    id         = doc.id,
                    courseName = doc.getString("courseName") ?: "",
                    title      = doc.getString("title") ?: doc.id,
                    className  = doc.getString("className") ?: "",
                    subject    = doc.getString("subject") ?: ""
                )
            }
        } catch (_: Exception) {}
    }

    fun search() {
        if (email.isBlank()) return
        searching = true; searchError = ""; foundUid = ""; enrolled = emptyList()
        scope.launch {
            try {
                val key = email.trim().replace(".", "_")
                val doc = db.collection("tutorial_user_emails").document(key).get().await()
                if (!doc.exists()) {
                    searchError = "User পাওয়া যায়নি। User কে একবার app এ login করতে বলো।"
                    searching = false; return@launch
                }
                val uid = doc.getString("uid") ?: ""
                if (uid.isEmpty()) { searchError = "UID পাওয়া যায়নি।"; searching = false; return@launch }
                val ud = db.collection("tutorial_users").document(uid).get().await()
                @Suppress("UNCHECKED_CAST")
                enrolled    = ud.get("enrolledCourses") as? List<String> ?: emptyList()
                foundUid    = uid
                foundEmail  = email.trim()
            } catch (_: Exception) { searchError = "Search fail হয়েছে।" }
            searching = false
        }
    }

    fun toggle(courseId: String, give: Boolean) {
        if (foundUid.isEmpty()) return
        scope.launch {
            try {
                val ref = db.collection("tutorial_users").document(foundUid)
                if (give) {
                    ref.update("enrolledCourses", FieldValue.arrayUnion(courseId)).await()
                    enrolled = enrolled + courseId
                    snack    = "Access দেওয়া হয়েছে ✓"
                } else {
                    ref.update("enrolledCourses", FieldValue.arrayRemove(courseId)).await()
                    enrolled = enrolled - courseId
                    snack    = "Access সরানো হয়েছে"
                }
            } catch (_: Exception) { snack = "Update fail" }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // Info box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AP.copy(0.1f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Default.Info, null, tint = AP, modifier = Modifier.size(16.dp).padding(top = 1.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "User এর email দিয়ে খোঁজো, তারপর কোন course দেখতে পাবে সেটা toggle করো।",
                    fontSize = 12.sp, color = AS, lineHeight = 18.sp
                )
            }

            Text("User খোঁজো", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AT)

            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value         = email,
                    onValueChange = { email = it; searchError = "" },
                    label         = { Text("Email", color = AS) },
                    modifier      = Modifier.weight(1f),
                    shape         = RoundedCornerShape(12.dp),
                    colors        = afc(),
                    singleLine    = true,
                    leadingIcon   = { Icon(Icons.Default.Search, null, tint = AA) }
                )
                Button(
                    onClick  = ::search,
                    colors   = ButtonDefaults.buttonColors(containerColor = AA),
                    shape    = RoundedCornerShape(12.dp),
                    enabled  = !searching && email.isNotBlank()
                ) {
                    if (searching) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("খোঁজো", color = Color.White)
                }
            }

            if (searchError.isNotEmpty()) {
                Text(searchError, color = AR, fontSize = 13.sp)
            }

            if (foundUid.isNotEmpty()) {
                HorizontalDivider(color = AS.copy(0.2f))

                // Found user card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AG.copy(0.08f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(40.dp).background(AG.copy(0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Person, null, tint = AG, modifier = Modifier.size(24.dp)) }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(foundEmail, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AT)
                        Text("${enrolled.size} টি course এ enrolled", fontSize = 12.sp, color = AS)
                    }
                }

                Text("Course Access", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AT)

                allCourses.forEach { c ->
                    val isOn = enrolled.contains(c.id)
                    Card(
                        Modifier.fillMaxWidth(),
                        shape  = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isOn) AG.copy(0.1f) else AC
                        )
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                if (c.courseName.isNotEmpty())
                                    Text(c.courseName, fontSize = 11.sp, color = AP)
                                Text(c.title, fontSize = 14.sp, color = AT,
                                    fontWeight = if (isOn) FontWeight.SemiBold else FontWeight.Normal)
                                val meta = listOfNotNull(
                                    c.className.ifEmpty { null },
                                    c.subject.ifEmpty { null }
                                ).joinToString(" • ")
                                if (meta.isNotEmpty()) Text(meta, fontSize = 11.sp, color = AS)
                            }
                            Switch(
                                checked         = isOn,
                                onCheckedChange = { toggle(c.id, it) },
                                colors          = SwitchDefaults.colors(
                                    checkedThumbColor   = Color.White,
                                    checkedTrackColor   = AG,
                                    uncheckedThumbColor = AS,
                                    uncheckedTrackColor = AS.copy(0.3f)
                                )
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(80.dp))
        }

        if (snack.isNotEmpty()) {
            Snackbar(
                modifier       = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                containerColor = AC
            ) { Text(snack, color = AT) }
            LaunchedEffect(snack) { kotlinx.coroutines.delay(2000); snack = "" }
        }
    }
}

// ── Field colours ─────────────────────────────────────────────
@Composable
private fun afc() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = AA,
    unfocusedBorderColor = AS.copy(0.3f),
    focusedTextColor     = AT,
    unfocusedTextColor   = AT,
    cursorColor          = AA
)
