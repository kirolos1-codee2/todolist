@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.kirolos.todoapp

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

class MainActivity : ComponentActivity() {

    private val viewModel: TaskViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ReminderScheduler.ensureChannel(this)
        val openNew = intent?.getBooleanExtra(EXTRA_NEW, false) ?: false
        setContent {
            GlassTheme { TodoScreen(viewModel, openNew) }
        }
    }

    companion object {
        const val EXTRA_NEW = "open_new"
    }
}

enum class TaskFilter { ALL, TODAY, OVERDUE, HIGH }

@Composable
fun TodoScreen(vm: TaskViewModel, openNew: Boolean) {
    val g = LocalGlass.current
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val all by vm.tasks.collectAsState()
    var filter by rememberSaveable { mutableStateOf(TaskFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var sheetOpen by rememberSaveable { mutableStateOf(openNew) }
    var editingId by rememberSaveable { mutableStateOf<Int?>(null) }

    // تراجع عن الحذف
    var undoTask by remember { mutableStateOf<Task?>(null) }
    var undoVisible by remember { mutableStateOf(false) }
    var undoTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(undoTick) {
        if (undoTick > 0) {
            undoVisible = true
            delay(4000)
            undoVisible = false
        }
    }

    // صلاحية الإشعارات (أندرويد 13+)
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val askNotifications: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val today = Dates.startOfDay()
    val tomorrow = Dates.plusDays(1)
    val pending = all.filter { !it.isDone }
    val q = query.trim()
    fun matches(t: Task) = q.isEmpty() || t.title.contains(q, ignoreCase = true) || t.notes.contains(q, ignoreCase = true)

    val shown = pending.filter { t ->
        matches(t) && when (filter) {
            TaskFilter.ALL -> true
            TaskFilter.TODAY -> t.dueDate == today
            TaskFilter.OVERDUE -> t.isOverdue()
            TaskFilter.HIGH -> t.priority == 3
        }
    }.sortedWith(compareBy<Task>({ it.dueDate ?: Long.MAX_VALUE }, { -it.priority }, { -it.createdAt }))

    val sections: List<Pair<String, List<Task>>> =
        if (filter == TaskFilter.ALL && q.isEmpty()) {
            listOf(
                "متأخرة" to shown.filter { it.isOverdue() },
                "اليوم" to shown.filter { it.dueDate == today },
                "بكرة" to shown.filter { it.dueDate == tomorrow },
                "قادمة" to shown.filter { val d = it.dueDate; d != null && d > tomorrow },
                "بدون موعد" to shown.filter { it.dueDate == null }
            ).filter { it.second.isNotEmpty() }
        } else {
            val title = when (filter) {
                TaskFilter.ALL -> "نتائج البحث"
                TaskFilter.TODAY -> "اليوم"
                TaskFilter.OVERDUE -> "متأخرة"
                TaskFilter.HIGH -> "أولوية عالية"
            }
            if (shown.isEmpty()) emptyList() else listOf(title to shown)
        }

    val doneList = if (filter == TaskFilter.ALL) all.filter { it.isDone && matches(it) } else emptyList()

    val countToday = pending.count { it.dueDate == today }
    val countOverdue = pending.count { it.isOverdue() }
    val countHigh = pending.count { it.priority == 3 }
    val doneCount = all.count { it.isDone }

    val onToggle: (Task) -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        vm.toggle(it)
    }
    val onOpen: (Task) -> Unit = { editingId = it.id; sheetOpen = true }
    val onDelete: (Task) -> Unit = {
        vm.delete(it)
        undoTask = it
        undoTick++
    }

    fun pick(f: TaskFilter) { filter = if (filter == f) TaskFilter.ALL else f }

    Box(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 150.dp)
        ) {
            item { TitleHeader(doneCount, all.size) }
            item { SearchField(query) { query = it } }
            item {
                Column {
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Tile(Modifier.weight(1f), "📅", "اليوم", countToday, g.blue, filter == TaskFilter.TODAY) { pick(TaskFilter.TODAY) }
                        Tile(Modifier.weight(1f), "⏰", "متأخرة", countOverdue, g.red, filter == TaskFilter.OVERDUE) { pick(TaskFilter.OVERDUE) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Tile(Modifier.weight(1f), "❗", "أولوية عالية", countHigh, g.orange, filter == TaskFilter.HIGH) { pick(TaskFilter.HIGH) }
                        Tile(Modifier.weight(1f), "📥", "الكل", pending.size, g.green, filter == TaskFilter.ALL) { filter = TaskFilter.ALL }
                    }
                }
            }
            if (sections.isEmpty()) {
                item {
                    Box(
                        Modifier.padding(top = 20.dp).fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✨ مفيش مهام هنا", color = g.secondary, fontSize = 16.sp)
                    }
                }
            }
            sections.forEach { (title, list) ->
                item(key = "h_$title") { SectionLabel(title) }
                items(list, key = { it.id }) { t ->
                    TaskRow(Modifier.animateItemPlacement(), t, { onToggle(t) }, { onOpen(t) }, { onDelete(t) })
                }
            }
            if (doneList.isNotEmpty()) {
                item(key = "done_header") {
                    Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "تمت (${doneList.size})", color = g.secondary, fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 4.dp)
                        )
                        TextButton(onClick = { vm.clearDone() }) {
                            Text("مسح الكل", color = g.blue, fontSize = 14.sp)
                        }
                    }
                }
                items(doneList, key = { it.id }) { t ->
                    TaskRow(Modifier.animateItemPlacement(), t, { onToggle(t) }, { onOpen(t) }, { onDelete(t) })
                }
            }
        }

        // شريط سفلي عايم: زرار الإضافة + رسالة التراجع
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, g.bgBottom.copy(alpha = 0.92f))))
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp)
        ) {
            AnimatedVisibility(
                visible = undoVisible,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                Row(
                    Modifier.padding(bottom = 10.dp).fillMaxWidth().glass(RoundedCornerShape(50), strong = true)
                        .padding(start = 18.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "اتحذفت: ${undoTask?.title ?: ""}", color = g.label, fontSize = 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        undoTask?.let { vm.restore(it) }
                        undoVisible = false
                    }) { Text("تراجع", color = g.blue, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                }
            }
            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(50), strong = true)
                    .clickable { editingId = null; sheetOpen = true }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(g.blueLight, g.blue))),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Text("تذكير جديد", color = g.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (sheetOpen) {
        val editing = all.firstOrNull { it.id == editingId }
        TaskSheet(
            initial = editing,
            onSave = { vm.save(it); sheetOpen = false; editingId = null },
            onDelete = editing?.let { e -> { onDelete(e); sheetOpen = false; editingId = null } },
            onDismiss = { sheetOpen = false; editingId = null },
            askNotifications = askNotifications
        )
    }
}

// ───────────────────────────── العناصر ─────────────────────────────

@Composable
private fun TitleHeader(done: Int, total: Int) {
    val g = LocalGlass.current
    val date = remember { SimpleDateFormat("EEEE، d MMMM", Dates.locale).format(Date()) }
    val progress = if (total == 0) 0f else done.toFloat() / total
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("المهام", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = g.label)
            Text(date, fontSize = 15.sp, color = g.secondary)
            if (total > 0) Text("تم $done من $total", fontSize = 13.sp, color = g.secondary)
        }
        Ring(progress)
    }
}

@Composable
private fun Ring(progress: Float) {
    val g = LocalGlass.current
    val p by animateFloatAsState(progress, tween(700), label = "ring")
    Box(Modifier.size(68.dp).glass(CircleShape), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val w = 6.dp.toPx()
            val topLeft = Offset(w / 2, w / 2)
            val sz = Size(size.width - w, size.height - w)
            drawArc(g.secondary.copy(alpha = 0.25f), 0f, 360f, false, topLeft = topLeft, size = sz, style = Stroke(w))
            drawArc(
                if (p >= 0.999f) g.green else g.blue, -90f, 360f * p, false,
                topLeft = topLeft, size = sz, style = Stroke(w, cap = StrokeCap.Round)
            )
        }
        Text("${(p * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = g.label)
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    val g = LocalGlass.current
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🔍", fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(color = g.label, fontSize = 16.sp),
            cursorBrush = SolidColor(g.blue),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text("بحث", color = g.secondary, fontSize = 16.sp)
                    inner()
                }
            }
        )
        if (value.isNotEmpty()) {
            Text("✕", color = g.secondary, fontSize = 15.sp, modifier = Modifier.clickable { onChange("") })
        }
    }
}

@Composable
private fun Tile(modifier: Modifier, emoji: String, title: String, count: Int, color: Color, selected: Boolean, onClick: () -> Unit) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.glass(shape)
            .then(if (selected) Modifier.background(color.copy(alpha = 0.14f)).border(2.dp, color, shape) else Modifier)
            .clickable(onClick = onClick).padding(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(38.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(color.lighten(0.35f), color))),
                contentAlignment = Alignment.Center
            ) { Text(emoji, fontSize = 17.sp) }
            Spacer(Modifier.weight(1f))
            Text("$count", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = g.label)
        }
        Spacer(Modifier.height(8.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = g.secondary)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LocalGlass.current.secondary,
        modifier = Modifier.padding(start = 6.dp, top = 20.dp, bottom = 6.dp)
    )
}

@Composable
private fun CheckCircle(done: Boolean, onClick: () -> Unit) {
    val g = LocalGlass.current
    val a by animateFloatAsState(if (done) 1f else 0f, tween(220), label = "check")
    Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(28.dp)) {
            val r = size.minDimension / 2 - 1.dp.toPx()
            drawCircle(color = g.secondary, radius = r, style = Stroke(1.8.dp.toPx()), alpha = 1f - a)
            drawCircle(
                brush = Brush.linearGradient(listOf(g.blueLight, g.blue)),
                radius = size.minDimension / 2 * (0.6f + 0.4f * a),
                alpha = a
            )
            val p = Path().apply {
                moveTo(size.width * 0.28f, size.height * 0.52f)
                lineTo(size.width * 0.44f, size.height * 0.68f)
                lineTo(size.width * 0.74f, size.height * 0.34f)
            }
            drawPath(p, Color.White, alpha = a, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private fun metaLine(t: Task): String? {
    val parts = ArrayList<String>()
    val due = t.dueDate
    if (due != null) {
        val bell = if (t.reminderMin != null) "🔔 " else ""
        val time = t.reminderMin?.let { " " + Dates.timeLabel(it) } ?: ""
        parts.add(bell + (if (t.isOverdue()) "متأخرة · " else "") + Dates.label(due) + time)
    }
    if (t.repeatMode != 0) parts.add("🔁 " + Dates.repeatLabel(t.repeatMode))
    return if (parts.isEmpty()) null else parts.joinToString("   ")
}

@Composable
private fun TaskRow(modifier: Modifier, task: Task, onToggle: () -> Unit, onOpen: () -> Unit, onDelete: () -> Unit) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(22.dp)
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        if (v == SwipeToDismissBoxValue.StartToEnd || v == SwipeToDismissBoxValue.EndToStart) onDelete()
        true
    })
    val overdue = task.isOverdue()
    val meta = metaLine(task)
    val priColor = when (task.priority) {
        3 -> g.red
        2 -> g.orange
        else -> g.blue
    }

    Box(modifier.padding(vertical = 4.dp)) {
        SwipeToDismissBox(
            state = state,
            modifier = Modifier.clip(shape),
            backgroundContent = {
                val dir = state.dismissDirection
                if (dir != SwipeToDismissBoxValue.Settled) {
                    Box(
                        Modifier.fillMaxSize()
                            .background(Brush.horizontalGradient(listOf(g.red, g.red.lighten(0.25f))))
                            .padding(horizontal = 24.dp),
                        contentAlignment = if (dir == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                    ) { Text("حذف", color = Color.White, fontWeight = FontWeight.SemiBold) }
                }
            }
        ) {
            Row(
                Modifier.fillMaxWidth().glass(shape).clickable(onClick = onOpen)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CheckCircle(task.isDone, onToggle)
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        task.title, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = if (task.isDone) g.secondary else g.label,
                        textDecoration = if (task.isDone) TextDecoration.LineThrough else null
                    )
                    if (task.notes.isNotBlank() && !task.isDone) {
                        Text(task.notes, fontSize = 14.sp, color = g.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (meta != null && !task.isDone) {
                        Text(meta, fontSize = 13.sp, color = if (overdue) g.red else g.secondary)
                    }
                }
                if (task.priority > 0 && !task.isDone) {
                    Text(
                        "!".repeat(task.priority), color = priColor, fontWeight = FontWeight.Bold,
                        fontSize = 18.sp, modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }
        }
    }
}

// ───────────────────────────── شيت الإضافة/التعديل ─────────────────────────────

@Composable
private fun TaskSheet(
    initial: Task?, onSave: (Task) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit,
    askNotifications: () -> Unit
) {
    val g = LocalGlass.current
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var priority by remember { mutableIntStateOf(initial?.priority ?: 0) }
    var due by remember { mutableStateOf(initial?.dueDate) }
    var reminder by remember { mutableStateOf(initial?.reminderMin) }
    var repeat by remember { mutableIntStateOf(initial?.repeatMode ?: 0) }

    val todayMs = Dates.startOfDay()
    val tomorrow = Dates.plusDays(1)
    val week = Dates.plusDays(7)
    val customDate = due != null && due != todayMs && due != tomorrow && due != week
    val presets = listOf(9 * 60, 13 * 60, 18 * 60, 21 * 60)
    val customTime = reminder != null && reminder !in presets
    val canSave = title.isNotBlank()

    fun setReminder(min: Int?) {
        reminder = min
        if (min != null) {
            if (due == null) {
                val now = Calendar.getInstance()
                val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                due = if (min > nowMin) todayMs else tomorrow
            }
            askNotifications()
        }
    }

    fun setRepeat(mode: Int) {
        repeat = mode
        if (mode != 0 && due == null) due = todayMs
    }

    val sheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.32f),
        shape = sheetShape,
        dragHandle = null
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(g.sheetFill, sheetShape)
                .border(1.dp, g.edge, sheetShape)
                .imePadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 16.dp)
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 5.dp)
                    .clip(CircleShape).background(g.secondary.copy(alpha = 0.35f))
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("إلغاء", color = g.blue, fontSize = 16.sp) }
                Text(
                    if (initial == null) "تذكير جديد" else "التفاصيل", color = g.label, fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center
                )
                TextButton(
                    enabled = canSave,
                    onClick = {
                        onSave(
                            (initial ?: Task(title = "")).copy(
                                title = title.trim(),
                                notes = notes.trim(),
                                priority = priority,
                                dueDate = due,
                                reminderMin = if (due != null) reminder else null,
                                repeatMode = if (due != null) repeat else 0
                            )
                        )
                    }
                ) { Text("حفظ", color = if (canSave) g.blue else g.secondary, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }

            // العنوان + الملاحظات
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(16.dp)) {
                BasicTextField(
                    value = title, onValueChange = { title = it }, maxLines = 3,
                    textStyle = TextStyle(color = g.label, fontSize = 18.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(g.blue), modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box {
                            if (title.isEmpty()) Text("عنوان المهمة", color = g.secondary, fontSize = 18.sp)
                            inner()
                        }
                    }
                )
                Box(Modifier.padding(vertical = 12.dp).fillMaxWidth().height(0.5.dp).background(g.separator))
                BasicTextField(
                    value = notes, onValueChange = { notes = it }, maxLines = 5,
                    textStyle = TextStyle(color = g.label, fontSize = 15.sp),
                    cursorBrush = SolidColor(g.blue), modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box {
                            if (notes.isEmpty()) Text("ملاحظات", color = g.secondary, fontSize = 15.sp)
                            inner()
                        }
                    }
                )
            }

            SectionLabel("الأولوية")
            Segmented(listOf("بدون", "منخفضة", "متوسطة", "عالية"), priority) { priority = it }

            SectionLabel("الموعد")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateChip("بدون", due == null) { due = null; reminder = null; repeat = 0 }
                DateChip("اليوم", due == todayMs) { due = todayMs }
                DateChip("بكرة", due == tomorrow) { due = tomorrow }
                DateChip("بعد أسبوع", due == week) { due = week }
                DateChip(if (customDate) Dates.label(due ?: todayMs) else "اختر تاريخ…", customDate) {
                    pickDate(ctx, due) { due = it }
                }
            }

            SectionLabel("تذكير بإشعار")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateChip("بدون", reminder == null) { reminder = null }
                presets.forEach { m ->
                    DateChip(Dates.timeLabel(m), reminder == m) { setReminder(m) }
                }
                DateChip(if (customTime) Dates.timeLabel(reminder ?: 0) else "اختر وقت…", customTime) {
                    pickTime(ctx, reminder) { setReminder(it) }
                }
            }

            SectionLabel("التكرار")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 1, 2, 3).forEach { m ->
                    DateChip(Dates.repeatLabel(m), repeat == m) { setRepeat(m) }
                }
            }

            if (onDelete != null) {
                Spacer(Modifier.height(22.dp))
                Box(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp))
                        .clickable(onClick = onDelete).padding(15.dp),
                    contentAlignment = Alignment.Center
                ) { Text("حذف المهمة", color = g.red, fontSize = 17.sp, fontWeight = FontWeight.Medium) }
            }
        }
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val g = LocalGlass.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(g.control).padding(3.dp)) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            val bg by animateColorAsState(if (sel) g.controlSelected else Color.Transparent, label = "seg")
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(bg)
                    .clickable { onSelect(i) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, fontSize = 14.sp, color = g.label, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun DateChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.clip(shape)
            .background(if (selected) Brush.linearGradient(listOf(g.blueLight, g.blue)) else SolidColor(g.control))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(text, fontSize = 14.sp, color = if (selected) Color.White else g.label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

private fun pickDate(ctx: Context, initial: Long?, onPicked: (Long) -> Unit) {
    val c = Calendar.getInstance()
    if (initial != null) c.timeInMillis = initial
    DatePickerDialog(
        ctx,
        { _, y, m, d ->
            val r = Calendar.getInstance()
            r.set(y, m, d, 0, 0, 0)
            r.set(Calendar.MILLISECOND, 0)
            onPicked(r.timeInMillis)
        },
        c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
    ).show()
}

private fun pickTime(ctx: Context, initialMin: Int?, onPicked: (Int) -> Unit) {
    val start = initialMin ?: (9 * 60)
    TimePickerDialog(
        ctx,
        { _, h, m -> onPicked(h * 60 + m) },
        start / 60, start % 60, DateFormat.is24HourFormat(ctx)
    ).show()
}
