@file:OptIn(ExperimentalMaterial3Api::class)

package com.kirolos.todoapp

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

class MainActivity : ComponentActivity() {

    private val viewModel: TaskViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val openNew = intent?.getBooleanExtra(EXTRA_NEW, false) ?: false
        setContent {
            IosTheme { TodoScreen(viewModel, openNew) }
        }
    }

    companion object {
        const val EXTRA_NEW = "open_new"
    }
}

enum class TaskFilter { ALL, TODAY, OVERDUE, HIGH }

@Composable
fun TodoScreen(vm: TaskViewModel, openNew: Boolean) {
    val ios = LocalIos.current
    val haptic = LocalHapticFeedback.current
    val all by vm.tasks.collectAsState()
    var filter by rememberSaveable { mutableStateOf(TaskFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var sheetOpen by rememberSaveable { mutableStateOf(openNew) }
    var editingId by rememberSaveable { mutableStateOf<Int?>(null) }

    val today = Dates.startOfDay()
    val pending = all.filter { !it.isDone }
    val q = query.trim()
    val shown = pending.filter { t ->
        (q.isEmpty() || t.title.contains(q, ignoreCase = true)) && when (filter) {
            TaskFilter.ALL -> true
            TaskFilter.TODAY -> t.dueDate == today
            TaskFilter.OVERDUE -> t.isOverdue()
            TaskFilter.HIGH -> t.priority == 3
        }
    }.sortedWith(compareBy<Task>({ it.dueDate ?: Long.MAX_VALUE }, { -it.priority }, { -it.createdAt }))
    val doneList = if (filter == TaskFilter.ALL)
        all.filter { it.isDone && (q.isEmpty() || it.title.contains(q, ignoreCase = true)) }
    else emptyList()

    val countToday = pending.count { it.dueDate == today }
    val countOverdue = pending.count { it.isOverdue() }
    val countHigh = pending.count { it.priority == 3 }
    val doneCount = all.count { it.isDone }

    val onToggle: (Task) -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        vm.toggle(it)
    }
    val onOpen: (Task) -> Unit = { editingId = it.id; sheetOpen = true }

    fun pick(f: TaskFilter) { filter = if (filter == f) TaskFilter.ALL else f }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp)) {
            item { TitleHeader(doneCount, all.size) }
            item { SearchField(query) { query = it } }
            item {
              Column {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile(Modifier.weight(1f), "📅", "اليوم", countToday, ios.blue, filter == TaskFilter.TODAY) { pick(TaskFilter.TODAY) }
                    Tile(Modifier.weight(1f), "⏰", "متأخرة", countOverdue, ios.red, filter == TaskFilter.OVERDUE) { pick(TaskFilter.OVERDUE) }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile(Modifier.weight(1f), "❗", "أولوية عالية", countHigh, ios.orange, filter == TaskFilter.HIGH) { pick(TaskFilter.HIGH) }
                    Tile(Modifier.weight(1f), "📥", "الكل", pending.size, ios.green, filter == TaskFilter.ALL) { filter = TaskFilter.ALL }
                }
              }
            }
            item {
                val title = when (filter) {
                    TaskFilter.ALL -> "قيد التنفيذ"
                    TaskFilter.TODAY -> "اليوم"
                    TaskFilter.OVERDUE -> "متأخرة"
                    TaskFilter.HIGH -> "أولوية عالية"
                }
                SectionLabel(title)
            }
            if (shown.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ios.card).padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✨ مفيش مهام هنا", color = ios.secondary, fontSize = 16.sp)
                    }
                }
            }
            itemsIndexed(shown, key = { _, t -> t.id }) { i, t ->
                TaskRow(t, groupShape(i, shown.size), i < shown.size - 1, { onToggle(t) }, { onOpen(t) }, { vm.delete(t) })
            }
            if (doneList.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "تمت (${doneList.size})", color = ios.secondary, fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 4.dp)
                        )
                        TextButton(onClick = { vm.clearDone() }) {
                            Text("مسح الكل", color = ios.blue, fontSize = 14.sp)
                        }
                    }
                }
                itemsIndexed(doneList, key = { _, t -> t.id }) { i, t ->
                    TaskRow(t, groupShape(i, doneList.size), i < doneList.size - 1, { onToggle(t) }, { onOpen(t) }, { vm.delete(t) })
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }

        Row(
            Modifier.fillMaxWidth().background(ios.bg)
                .clickable { editingId = null; sheetOpen = true }
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(ios.blue), contentAlignment = Alignment.Center) {
                Text("+", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Text("تذكير جديد", color = ios.blue, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    if (sheetOpen) {
        val editing = all.firstOrNull { it.id == editingId }
        TaskSheet(
            initial = editing,
            onSave = { vm.save(it); sheetOpen = false; editingId = null },
            onDelete = editing?.let { e -> { vm.delete(e); sheetOpen = false; editingId = null } },
            onDismiss = { sheetOpen = false; editingId = null }
        )
    }
}

private fun groupShape(i: Int, n: Int): Shape {
    val r = 12.dp
    val top = if (i == 0) r else 0.dp
    val bottom = if (i == n - 1) r else 0.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

@Composable
private fun TitleHeader(done: Int, total: Int) {
    val ios = LocalIos.current
    val date = remember { SimpleDateFormat("EEEE، d MMMM", Dates.locale).format(Date()) }
    val progress = if (total == 0) 0f else done.toFloat() / total
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("المهام", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = ios.label)
            Text(date, fontSize = 15.sp, color = ios.secondary)
            if (total > 0) Text("تم $done من $total", fontSize = 13.sp, color = ios.secondary)
        }
        Ring(progress)
    }
}

@Composable
private fun Ring(progress: Float) {
    val ios = LocalIos.current
    Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 6.dp.toPx()
            val topLeft = Offset(w / 2, w / 2)
            val sz = Size(size.width - w, size.height - w)
            drawArc(ios.separator, 0f, 360f, false, topLeft = topLeft, size = sz, style = Stroke(w))
            drawArc(ios.blue, -90f, 360f * progress, false, topLeft = topLeft, size = sz, style = Stroke(w, cap = StrokeCap.Round))
        }
        Text("${(progress * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ios.label)
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    val ios = LocalIos.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ios.fill).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🔍", fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(color = ios.label, fontSize = 16.sp),
            cursorBrush = SolidColor(ios.blue),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text("بحث", color = ios.secondary, fontSize = 16.sp)
                    inner()
                }
            }
        )
        if (value.isNotEmpty()) {
            Text("✕", color = ios.secondary, fontSize = 15.sp, modifier = Modifier.clickable { onChange("") })
        }
    }
}

@Composable
private fun Tile(modifier: Modifier, emoji: String, title: String, count: Int, color: Color, selected: Boolean, onClick: () -> Unit) {
    val ios = LocalIos.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.clip(shape).background(ios.card)
            .then(if (selected) Modifier.border(2.dp, color, shape) else Modifier)
            .clickable(onClick = onClick).padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                Text(emoji, fontSize = 16.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("$count", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = ios.label)
        }
        Spacer(Modifier.height(8.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ios.secondary)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LocalIos.current.secondary,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun CheckCircle(done: Boolean, onClick: () -> Unit) {
    val ios = LocalIos.current
    Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(26.dp)) {
            if (done) {
                drawCircle(ios.blue)
                val p = Path().apply {
                    moveTo(size.width * 0.28f, size.height * 0.52f)
                    lineTo(size.width * 0.44f, size.height * 0.68f)
                    lineTo(size.width * 0.74f, size.height * 0.34f)
                }
                drawPath(p, Color.White, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            } else {
                drawCircle(ios.secondary, radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(1.6.dp.toPx()))
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: Task, shape: Shape, divider: Boolean,
    onToggle: () -> Unit, onOpen: () -> Unit, onDelete: () -> Unit
) {
    val ios = LocalIos.current
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        if (v == SwipeToDismissBoxValue.StartToEnd || v == SwipeToDismissBoxValue.EndToStart) onDelete()
        true
    })
    val overdue = task.isOverdue()
    val sub = task.dueDate?.let { (if (overdue) "متأخرة · " else "") + Dates.label(it) }

    SwipeToDismissBox(
        state = state,
        modifier = Modifier.clip(shape),
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(ios.red).padding(horizontal = 22.dp),
                contentAlignment = Alignment.CenterEnd
            ) { Text("حذف", color = Color.White, fontWeight = FontWeight.SemiBold) }
        }
    ) {
        Column(Modifier.fillMaxWidth().background(ios.card)) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CheckCircle(task.isDone, onToggle)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        task.title, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = if (task.isDone) ios.secondary else ios.label,
                        textDecoration = if (task.isDone) TextDecoration.LineThrough else null
                    )
                    if (sub != null && !task.isDone) {
                        Text(sub, fontSize = 13.sp, color = if (overdue) ios.red else ios.secondary)
                    }
                }
                if (task.priority > 0 && !task.isDone) {
                    Text("!".repeat(task.priority), color = ios.orange, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            }
            if (divider) {
                Box(Modifier.padding(start = 54.dp).fillMaxWidth().height(0.5.dp).background(ios.separator))
            }
        }
    }
}

@Composable
private fun TaskSheet(initial: Task?, onSave: (Task) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    val ios = LocalIos.current
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var priority by remember { mutableIntStateOf(initial?.priority ?: 0) }
    var due by remember { mutableStateOf(initial?.dueDate) }
    val todayMs = Dates.startOfDay()
    val tomorrow = Dates.plusDays(1)
    val week = Dates.plusDays(7)
    val custom = due != null && due != todayMs && due != tomorrow && due != week
    val canSave = title.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ios.bg
    ) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("إلغاء", color = ios.blue, fontSize = 16.sp) }
                Text(
                    if (initial == null) "تذكير جديد" else "التفاصيل", color = ios.label, fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                TextButton(
                    enabled = canSave,
                    onClick = {
                        onSave((initial ?: Task(title = "")).copy(title = title.trim(), priority = priority, dueDate = due))
                    }
                ) { Text("حفظ", color = if (canSave) ios.blue else ios.secondary, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }

            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ios.card).padding(16.dp)) {
                BasicTextField(
                    value = title, onValueChange = { title = it }, maxLines = 3,
                    textStyle = TextStyle(color = ios.label, fontSize = 17.sp),
                    cursorBrush = SolidColor(ios.blue), modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box {
                            if (title.isEmpty()) Text("عنوان المهمة", color = ios.secondary, fontSize = 17.sp)
                            inner()
                        }
                    }
                )
            }

            SectionLabel("الأولوية")
            Segmented(listOf("بدون", "منخفضة", "متوسطة", "عالية"), priority) { priority = it }

            SectionLabel("الموعد")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateChip("بدون", due == null) { due = null }
                DateChip("اليوم", due == todayMs) { due = todayMs }
                DateChip("بكرة", due == tomorrow) { due = tomorrow }
                DateChip("بعد أسبوع", due == week) { due = week }
                DateChip(if (custom) Dates.label(due ?: todayMs) else "اختر تاريخ…", custom) {
                    pickDate(ctx, due) { due = it }
                }
            }

            if (onDelete != null) {
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ios.card)
                        .clickable(onClick = onDelete).padding(14.dp),
                    contentAlignment = Alignment.Center
                ) { Text("حذف المهمة", color = ios.red, fontSize = 17.sp) }
            }
        }
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val ios = LocalIos.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ios.fill).padding(2.dp)) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .background(if (sel) ios.segSelected else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, fontSize = 14.sp, color = ios.label, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun DateChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val ios = LocalIos.current
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) ios.blue else ios.fill)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, fontSize = 14.sp, color = if (selected) Color.White else ios.label)
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
