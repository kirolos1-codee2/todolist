package com.kirolos.todoapp

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Worker
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * مزامنة المهام مع Firebase.
 * الهيكل على السحابة:  users/{uid}/tasks/{cloudId}   (كل مهمة)
 *                      users/{uid}/meta/state         (حقل rev بيتغير مع كل تعديل، والإكستنشن بيراقبه)
 * Room فاضل هو المصدر المحلي للتطبيق والويدجت والتذكيرات.
 */
object CloudSync {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var registration: ListenerRegistration? = null
    private var activeUid: String? = null

    private fun uid(): String? = FirebaseAuth.getInstance().currentUser?.uid
    private fun userDoc(uid: String) = FirebaseFirestore.getInstance().collection("users").document(uid)
    private fun tasksCol(uid: String): CollectionReference = userDoc(uid).collection("tasks")

    private fun touch(uid: String) {
        userDoc(uid).collection("meta").document("state")
            .set(mapOf("rev" to System.currentTimeMillis()), SetOptions.merge())
    }

    private fun Task.toMap(): Map<String, Any?> = hashMapOf(
        "title" to title,
        "isDone" to isDone,
        "createdAt" to createdAt,
        "priority" to priority,
        "dueDate" to dueDate,
        "notes" to notes,
        "repeatMode" to repeatMode,
        "reminderMin" to reminderMin
    )

    private fun DocumentSnapshot.toTask(localId: Int): Task? {
        val title = getString("title") ?: return null
        return Task(
            id = localId,
            title = title,
            isDone = getBoolean("isDone") ?: false,
            createdAt = getLong("createdAt") ?: System.currentTimeMillis(),
            priority = getLong("priority")?.toInt() ?: 0,
            dueDate = getLong("dueDate"),
            notes = getString("notes") ?: "",
            repeatMode = getLong("repeatMode")?.toInt() ?: 0,
            reminderMin = getLong("reminderMin")?.toInt(),
            cloudId = id
        )
    }

    // ───────── كتابة (من التطبيق للسحابة) ─────────

    fun push(t: Task) {
        val u = uid() ?: return
        if (t.cloudId.isBlank()) return
        tasksCol(u).document(t.cloudId).set(t.toMap())
        touch(u)
    }

    fun remove(t: Task) {
        val u = uid() ?: return
        if (t.cloudId.isBlank()) return
        tasksCol(u).document(t.cloudId).delete()
        touch(u)
    }

    // ───────── قراءة (من السحابة للتطبيق) ─────────

    suspend fun applyRemote(ctx: Context, docs: List<DocumentSnapshot>, deleteMissing: Boolean) = lock.withLock {
        val dao = TaskDatabase.getInstance(ctx).taskDao()
        val seen = HashSet<String>()
        var changed = false
        val nm = NotificationManagerCompat.from(ctx)

        for (d in docs) {
            seen.add(d.id)
            if (d.metadata.hasPendingWrites()) continue  // تعديل محلي لسه طالع، الحالة المحلية أحدث
            val existing = dao.byCloudId(d.id)
            val incoming = d.toTask(existing?.id ?: 0) ?: continue
            if (existing == null) {
                val newId = dao.insert(incoming).toInt()
                ReminderScheduler.schedule(ctx, incoming.copy(id = newId))
                changed = true
            } else if (existing != incoming) {
                dao.update(incoming)
                ReminderScheduler.schedule(ctx, incoming)
                if (incoming.isDone) nm.cancel(incoming.id)  // اتقفلت من جهاز تاني: شيل الإشعار
                changed = true
            }
        }

        if (deleteMissing) {
            val cutoff = System.currentTimeMillis() - 2 * 60 * 1000L
            for (t in dao.allNow()) {
                if (t.cloudId.isNotBlank() && t.cloudId !in seen && t.createdAt < cutoff) {
                    dao.delete(t)
                    ReminderScheduler.cancel(ctx, t.id)
                    nm.cancel(t.id)
                    changed = true
                }
            }
        }
        if (changed) TodoWidgetProvider.refresh(ctx)
    }

    // أول تسجيل دخول على الجهاز: ارفع المهام اللي موجودة محليًا على الحساب
    private suspend fun initialPush(ctx: Context, uid: String) {
        val prefs = ctx.getSharedPreferences("sync", Context.MODE_PRIVATE)
        if (prefs.getString("initial_push_uid", null) == uid) return
        lock.withLock {
            val dao = TaskDatabase.getInstance(ctx).taskDao()
            val all = dao.allNow()
            for (t in all) {
                val withId = if (t.cloudId.isBlank()) t.copy(cloudId = UUID.randomUUID().toString()).also { dao.update(it) } else t
                tasksCol(uid).document(withId.cloudId).set(withId.toMap())
            }
            if (all.isNotEmpty()) touch(uid)
        }
        prefs.edit().putString("initial_push_uid", uid).apply()
    }

    @Synchronized
    fun start(ctx: Context) {
        val u = uid() ?: return
        if (activeUid == u) return
        stopInternal()
        activeUid = u
        val app = ctx.applicationContext
        scope.launch {
            initialPush(app, u)
            if (activeUid != u) return@launch
            val reg = tasksCol(u).addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val docs = snap.documents
                // ماننفّذش حذف إلا لما اللقطة تيجي من السيرفر فعلًا (مش من الكاش)
                val deleteMissing = !snap.metadata.isFromCache
                scope.launch { applyRemote(app, docs, deleteMissing) }
            }
            synchronized(this@CloudSync) {
                if (activeUid == u) registration = reg else reg.remove()
            }
        }
        SyncWorker.schedule(app)
    }

    @Synchronized
    fun stop() = stopInternal()

    private fun stopInternal() {
        registration?.remove()
        registration = null
        activeUid = null
    }

    /** تسجيل خروج: بنمسح المهام من الجهاز ده (هتفضل محفوظة على الحساب) */
    fun signOut(ctx: Context) {
        val app = ctx.applicationContext
        stop()
        SyncWorker.cancel(app)
        scope.launch {
            lock.withLock {
                val dao = TaskDatabase.getInstance(app).taskDao()
                val nm = NotificationManagerCompat.from(app)
                dao.allNow().forEach {
                    ReminderScheduler.cancel(app, it.id)
                    nm.cancel(it.id)
                }
                dao.wipe()
                app.getSharedPreferences("sync", Context.MODE_PRIVATE).edit().clear().apply()
            }
            TodoWidgetProvider.refresh(app)
        }
        FirebaseAuth.getInstance().signOut()
    }
}

/** كل ١٥ دقيقة بيجيب المهام من السحابة ويظبط التذكيرات، حتى لو التطبيق مقفول */
class SyncWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        return try {
            val col = FirebaseFirestore.getInstance().collection("users").document(uid).collection("tasks")
            val snap = Tasks.await(col.get(Source.SERVER), 30, TimeUnit.SECONDS)
            runBlocking { CloudSync.applyRemote(applicationContext, snap.documents, deleteMissing = true) }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "cloud_sync"

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(NAME)
        }
    }
}
