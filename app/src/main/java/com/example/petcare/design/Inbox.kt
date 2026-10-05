package com.example.petcare.design

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/** What kind of event a notification is; decides its icon, colour and system channel. */
enum class InboxType { REMINDER, OVERDUE, HEALTH, DELEGATION, ACCOUNT }

data class InboxItem(
    val id: Long,
    val type: InboxType,
    val title: String,
    val body: String,
    val createdAt: Long,
    val read: Boolean = false,
    val taskId: Long? = null,
    val petId: Long? = null,
    val petColorIndex: Int? = null,
    /** Same key twice = stored once, e.g. "overdue:42:2026-09-23" so an alert isn't repeated. */
    val dedupeKey: String? = null,
)

/**
 * The in-app notification centre's storage. Kept separate from your Room database so it
 * needs no migration: a small JSON list in SharedPreferences (last 30 days, max 100 items).
 * Observe [items] or [unreadCount]; every change updates them immediately.
 */
class InboxStore private constructor(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Any()
    private val state = MutableStateFlow(load())

    val items: StateFlow<List<InboxItem>> = state.asStateFlow()
    val unreadCount: Flow<Int> = state.map { list -> list.count { !it.read } }

    /** Adds a notification. Returns null if [dedupeKey] was already stored. */
    fun add(
        type: InboxType,
        title: String,
        body: String,
        taskId: Long? = null,
        petId: Long? = null,
        petColorIndex: Int? = null,
        dedupeKey: String? = null,
        now: Long = System.currentTimeMillis(),
    ): InboxItem? {
        synchronized(lock) {
            val current = state.value
            if (dedupeKey != null && current.any { it.dedupeKey == dedupeKey }) return null
            val id = prefs.getLong(KEY_NEXT_ID, 1L)
            val item = InboxItem(id, type, title, body, now, false, taskId, petId, petColorIndex, dedupeKey)
            prefs.edit().putLong(KEY_NEXT_ID, id + 1L).apply()
            commit(listOf(item) + current, now)
            return item
        }
    }

    fun markRead(id: Long) = update { list -> list.map { if (it.id == id) it.copy(read = true) else it } }

    fun markAllRead() = update { list -> list.map { it.copy(read = true) } }

    /** Removes and returns the item, so a Snackbar can offer Undo via [restore]. */
    fun remove(id: Long): InboxItem? {
        synchronized(lock) {
            val removed = state.value.firstOrNull { it.id == id } ?: return null
            commit(state.value.filterNot { it.id == id })
            return removed
        }
    }

    fun restore(item: InboxItem) = update { list -> (list + item).sortedByDescending { it.createdAt } }

    /** Removes everything linked to a task, e.g. after the task is deleted. */
    fun removeForTask(taskId: Long) = update { list -> list.filterNot { it.taskId == taskId } }

    fun clear() = update { emptyList() }

    private fun update(transform: (List<InboxItem>) -> List<InboxItem>) {
        synchronized(lock) { commit(transform(state.value)) }
    }

    private fun commit(list: List<InboxItem>, now: Long = System.currentTimeMillis()) {
        val kept = list.filter { now - it.createdAt <= MAX_AGE_MS }.take(MAX_ITEMS)
        state.value = kept
        prefs.edit().putString(KEY_ITEMS, toJson(kept)).apply()
    }

    private fun load(): List<InboxItem> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index -> fromJson(array.getJSONObject(index)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun toJson(list: List<InboxItem>): String {
        val array = JSONArray()
        list.forEach { item ->
            val obj = JSONObject()
                .put("id", item.id)
                .put("type", item.type.name)
                .put("title", item.title)
                .put("body", item.body)
                .put("createdAt", item.createdAt)
                .put("read", item.read)
            item.taskId?.let { obj.put("taskId", it) }
            item.petId?.let { obj.put("petId", it) }
            item.petColorIndex?.let { obj.put("petColorIndex", it) }
            item.dedupeKey?.let { obj.put("dedupeKey", it) }
            array.put(obj)
        }
        return array.toString()
    }

    private fun fromJson(obj: JSONObject): InboxItem? {
        val type = InboxType.entries.firstOrNull { it.name == obj.optString("type") } ?: return null
        return InboxItem(
            id = obj.getLong("id"),
            type = type,
            title = obj.optString("title"),
            body = obj.optString("body"),
            createdAt = obj.getLong("createdAt"),
            read = obj.optBoolean("read"),
            taskId = if (obj.has("taskId")) obj.getLong("taskId") else null,
            petId = if (obj.has("petId")) obj.getLong("petId") else null,
            petColorIndex = if (obj.has("petColorIndex")) obj.getInt("petColorIndex") else null,
            dedupeKey = if (obj.has("dedupeKey")) obj.getString("dedupeKey") else null,
        )
    }

    companion object {
        private const val PREFS = "pc_inbox"
        private const val KEY_ITEMS = "items"
        private const val KEY_NEXT_ID = "next_id"
        private const val MAX_ITEMS = 100
        private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

        @Volatile
        private var instance: InboxStore? = null

        fun get(context: Context): InboxStore =
            instance ?: synchronized(this) {
                instance ?: InboxStore(context.applicationContext).also { instance = it }
            }
    }
}
