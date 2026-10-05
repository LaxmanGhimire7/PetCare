package com.example.petcare.design

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.example.petcare.R
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

interface NotificationActions {
    /** Open whatever the notification is about, e.g. the task detail sheet for item.taskId. */
    fun onOpen(item: InboxItem)
    fun onBack()
}

/** Binds pc_fragment_notifications.xml. Tap opens and marks read; swipe removes with Undo. */
class NotificationsScreenBinder(
    root: View,
    lifecycleOwner: LifecycleOwner,
    private val actions: NotificationActions,
) {
    private val context = root.context
    private val store = InboxStore.get(context)
    private val list: RecyclerView = root.findViewById(R.id.pcInboxList)
    private val empty: View = root.findViewById(R.id.pcInboxEmpty)
    private val markAll: View = root.findViewById(R.id.pcInboxMarkAll)
    private val adapter = InboxAdapter { item ->
        store.markRead(item.id)
        actions.onOpen(item)
    }

    init {
        root.applySystemBarTopPadding()
        root.findViewById<View>(R.id.pcInboxBack).setOnClickListener { actions.onBack() }
        markAll.setOnClickListener {
            store.markAllRead()
            Toast.makeText(context, R.string.notifications_marked_read, Toast.LENGTH_SHORT).show()
        }
        list.layoutManager = LinearLayoutManager(context)
        list.adapter = adapter
        ItemTouchHelper(SwipeToRemove()).attachToRecyclerView(list)

        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                store.items.collect { render(it) }
            }
        }
    }

    private fun render(items: List<InboxItem>) {
        adapter.submitList(InboxRows.build(context, items, System.currentTimeMillis()))
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        markAll.visibility = if (items.any { !it.read }) View.VISIBLE else View.INVISIBLE
    }

    /** Swipe either way to remove a notification; section headers can't be swiped. */
    private inner class SwipeToRemove : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ): Boolean = false

        override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int =
            if (adapter.rowAt(viewHolder.bindingAdapterPosition) is InboxRow.Entry) {
                super.getSwipeDirs(recyclerView, viewHolder)
            } else {
                0
            }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val row = adapter.rowAt(viewHolder.bindingAdapterPosition) as? InboxRow.Entry ?: return
            Haptics.tick(viewHolder.itemView)
            val removed = store.remove(row.item.id) ?: return
            Snackbar.make(list, R.string.pc_notification_removed, Snackbar.LENGTH_LONG)
                .setAction(R.string.pc_undo) { store.restore(removed) }
                .show()
        }
    }
}

/** A row in the notification centre: a "Today"/"Earlier" heading or a notification. */
sealed interface InboxRow {
    val key: String

    data class Header(val title: String) : InboxRow {
        override val key: String get() = "header_$title"
    }

    data class Entry(val item: InboxItem, val timeLabel: String) : InboxRow {
        override val key: String get() = "item_${item.id}"
    }
}

object InboxRows {
    /** Newest first, grouped into Today and Earlier. */
    fun build(context: Context, items: List<InboxItem>, now: Long): List<InboxRow> {
        val startOfToday = startOfDay(now)
        val startOfYesterday = startOfToday - DAY_MS
        val clock = SimpleDateFormat("HH:mm", Locale.getDefault())
        val date = SimpleDateFormat("EEE d MMM", Locale.getDefault())
        val sorted = items.sortedByDescending { it.createdAt }
        val (today, earlier) = sorted.partition { it.createdAt >= startOfToday }

        fun label(item: InboxItem): String = when {
            item.createdAt >= startOfToday -> clock.format(Date(item.createdAt))
            item.createdAt >= startOfYesterday -> context.getString(R.string.pc_yesterday)
            else -> date.format(Date(item.createdAt))
        }

        return buildList {
            if (today.isNotEmpty()) {
                add(InboxRow.Header(context.getString(R.string.pc_group_today)))
                today.forEach { add(InboxRow.Entry(it, label(it))) }
            }
            if (earlier.isNotEmpty()) {
                add(InboxRow.Header(context.getString(R.string.pc_group_earlier)))
                earlier.forEach { add(InboxRow.Entry(it, label(it))) }
            }
        }
    }

    private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private const val DAY_MS = 24L * 60 * 60 * 1000
}

private class InboxAdapter(
    private val onOpen: (InboxItem) -> Unit,
) : ListAdapter<InboxRow, RecyclerView.ViewHolder>(Diff) {

    fun rowAt(position: Int): InboxRow? = if (position in 0 until itemCount) getItem(position) else null

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is InboxRow.Header -> TYPE_HEADER
        is InboxRow.Entry -> TYPE_ENTRY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(inflater.inflate(R.layout.pc_item_notification_header, parent, false))
        } else {
            EntryHolder(inflater.inflate(R.layout.pc_item_notification, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is InboxRow.Header -> (holder as HeaderHolder).bind(row)
            is InboxRow.Entry -> (holder as EntryHolder).bind(row)
        }
    }

    private class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.pcInboxHeader)
        fun bind(row: InboxRow.Header) {
            title.text = row.title
        }
    }

    private inner class EntryHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.pcInboxIcon)
        private val title: TextView = view.findViewById(R.id.pcInboxTitle)
        private val body: TextView = view.findViewById(R.id.pcInboxBody)
        private val time: TextView = view.findViewById(R.id.pcInboxTime)
        private val unread: View = view.findViewById(R.id.pcInboxUnread)

        fun bind(row: InboxRow.Entry) {
            val ctx = itemView.context
            val item = row.item
            val (background, foreground) = colorsFor(ctx, item)
            icon.setImageResource(PcNotifier.iconFor(item.type))
            ViewCompat.setBackgroundTintList(icon, ColorStateList.valueOf(background))
            ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(foreground))

            title.text = item.title
            title.setTextColor(ContextCompat.getColor(ctx, if (item.read) R.color.pc_text_secondary else R.color.pc_text_primary))
            body.text = item.body
            time.text = row.timeLabel
            unread.visibility = if (item.read) View.INVISIBLE else View.VISIBLE
            itemView.contentDescription = "${item.title}. ${item.body}. ${row.timeLabel}"
            itemView.setOnClickListener { onOpen(item) }
        }

        /** Background and icon colour per type; health items take the pet's colour. */
        private fun colorsFor(ctx: Context, item: InboxItem): Pair<Int, Int> {
            fun c(id: Int) = ContextCompat.getColor(ctx, id)
            return when (item.type) {
                InboxType.REMINDER -> c(R.color.pc_primary_container) to c(R.color.pc_primary_text)
                InboxType.OVERDUE -> c(R.color.pc_error_container) to c(R.color.pc_error)
                InboxType.HEALTH -> item.petColorIndex?.let { index ->
                    val pet = PetColor.fromIndex(index)
                    pet.tint(ctx) to pet.main(ctx)
                } ?: (c(R.color.pc_success_container) to c(R.color.pc_success))
                InboxType.DELEGATION, InboxType.ACCOUNT -> c(R.color.pc_surface_muted) to c(R.color.pc_text_primary)
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<InboxRow>() {
        override fun areItemsTheSame(oldItem: InboxRow, newItem: InboxRow) = oldItem.key == newItem.key
        override fun areContentsTheSame(oldItem: InboxRow, newItem: InboxRow) = oldItem == newItem
    }

    private companion object {
        const val TYPE_HEADER = 1
        const val TYPE_ENTRY = 2
    }
}
