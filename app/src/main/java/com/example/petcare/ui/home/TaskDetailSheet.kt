package com.example.petcare.ui.home

import android.os.Bundle
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.widget.PopupMenu
import androidx.navigation.fragment.findNavController
import com.example.petcare.data.local.PetCareDatabase
import com.example.petcare.data.local.PetCareRepositories
import com.example.petcare.data.local.provider.ProviderRepository
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.petcare.R
import com.example.petcare.data.local.care.CareTaskSummary
import com.example.petcare.databinding.SheetTaskDetailBinding
import com.example.petcare.ui.MotionPrefs
import com.example.petcare.ui.GestureHaptics
import com.example.petcare.ui.UiSnackbar
import com.example.petcare.data.local.pet.PetColor
import com.example.petcare.reminders.CareReminderScheduler
import com.google.android.material.snackbar.Snackbar
import android.graphics.drawable.GradientDrawable
import com.example.petcare.location.PlaceLocation
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import kotlinx.coroutines.launch

/** Draggable task sheet with a short title/time peek and expanded care instructions. */
class TaskDetailSheet : BottomSheetDialogFragment() {
    private var _binding: SheetTaskDetailBinding? = null
    private val binding get() = _binding!!
    private var preview: MapView? = null
    private var sheetCallback: BottomSheetBehavior.BottomSheetCallback? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = SheetTaskDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        val data = requireArguments()
        binding.taskTitle.text = data.getString("title")
        val minutes = data.getInt("minutes")
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, minutes / 60)
                set(Calendar.MINUTE, minutes % 60)
            }.time
        )
        val day = DateFormat.getDateInstance(DateFormat.MEDIUM).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(data.getLong("day") * 86_400_000L))
        binding.taskTime.text = getString(R.string.task_detail_when, day, time)
        binding.taskPet.text = data.getString("pet")
        val petColor = PetColor.fromIndex(data.getInt("petColorIndex"))
        binding.taskPet.setTextColor(petColor.onContainer(requireContext()))
        binding.taskPet.background = GradientDrawable().apply {
            cornerRadius = resources.getDimension(R.dimen.radius_pill)
            setColor(petColor.container(requireContext()))
        }
        binding.taskCategory.text = data.getString("category")
        val supplies = data.getString("supplies").orEmpty()
        binding.taskSupplies.text = getString(R.string.today_checklist_supplies, supplies)
        binding.taskSupplies.visibility = if (supplies.isBlank()) View.GONE else View.VISIBLE
        val notes = data.getString("notes").orEmpty()
        binding.taskNotes.text = getString(R.string.task_detail_notes, notes)
        binding.taskNotes.visibility = if (notes.isBlank()) View.GONE else View.VISIBLE
        val lat = data.getDouble("latitude", Double.NaN)
        val lon = data.getDouble("longitude", Double.NaN)
        if (lat.isFinite() && lon.isFinite()) {
            binding.taskDirectionsButton.visibility = View.VISIBLE
            binding.taskDirectionsButton.setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon")))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(requireContext(), R.string.no_compatible_app, Toast.LENGTH_SHORT).show()
                }
            }
            if (PlaceLocation.mapsAvailable(requireContext())) {
                val mapView = MapView(requireContext(), GoogleMapOptions().liteMode(true))
                preview = mapView
                binding.taskMapPreview.visibility = View.VISIBLE
                binding.taskMapPreview.addView(mapView)
                mapView.onCreate(state)
                mapView.getMapAsync { map ->
                    val point = LatLng(lat, lon)
                    map.addMarker(MarkerOptions().position(point))
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(point, 14f))
                }
            }
        }
        var eventLocation = if (lat.isFinite() && lon.isFinite()) "$lat,$lon" else ""
        val placeId = data.getLong("placeId", 0L)
        if (placeId > 0L) lifecycleScope.launch {
            val place = PetCareRepositories(requireContext()).places.get(placeId)
            if (place != null) eventLocation = place.address
        }
        binding.taskCalendarButton.setOnClickListener {
            val start = localStartMillis(data.getLong("day"), minutes)
            val description = listOf(supplies.takeIf(String::isNotBlank)?.let {
                getString(R.string.today_checklist_supplies, it)
            }, notes.takeIf(String::isNotBlank)).filterNotNull().joinToString("\n")
            try {
                startActivity(Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.Events.TITLE, data.getString("title"))
                    .putExtra(CalendarContract.Events.DESCRIPTION, description)
                    .putExtra(CalendarContract.Events.EVENT_LOCATION, eventLocation)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 60 * 60 * 1000L))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(requireContext(), R.string.no_compatible_app, Toast.LENGTH_SHORT).show()
            }
        }
        val taskId = data.getLong("id")
        val completed = data.getBoolean("completed")
        binding.taskMarkDoneButton.visibility = if (completed) View.GONE else View.VISIBLE
        binding.taskMarkDoneButton.setOnClickListener {
            lifecycleScope.launch {
                val repository = PetCareRepositories(requireContext()).tasks
                val successor = repository.completeTask(taskId)
                CareReminderScheduler(requireContext()).cancel(taskId)
                successor?.let(CareReminderScheduler(requireContext())::schedule)
                UiSnackbar.make(requireActivity().findViewById(android.R.id.content),
                    getString(R.string.task_done_message, data.getString("title")),
                    Snackbar.LENGTH_LONG).setAction(R.string.undo) {
                    lifecycleScope.launch {
                        repository.reopenTask(taskId)?.let(CareReminderScheduler(requireContext())::schedule)
                    }
                }.show()
                dismiss()
            }
        }
        binding.taskShareButton.setOnClickListener {
            val body = getString(R.string.task_share_body, data.getString("pet"),
                data.getString("title"), day, time, notes)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, body)
            }, getString(R.string.share_list)))
        }
        binding.taskMoreButton.setOnClickListener { anchor ->
            PopupMenu(requireContext(), anchor).apply {
                inflate(R.menu.task_detail_actions)
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_edit_task -> {
                            dismiss()
                            requireParentFragment().findNavController().navigate(R.id.action_home_to_edit_care_task,
                                Bundle().apply { putLong("careTaskId", taskId) })
                            true
                        }
                        R.id.action_delete_task -> {
                            lifecycleScope.launch {
                                val repository = PetCareRepositories(requireContext()).tasks
                                val snapshot = repository.deleteTask(taskId) ?: return@launch
                                CareReminderScheduler(requireContext()).cancel(taskId)
                                UiSnackbar.make(requireActivity().findViewById(android.R.id.content),
                                    R.string.task_deleted, Snackbar.LENGTH_LONG).setAction(R.string.undo) {
                                    lifecycleScope.launch {
                                        repository.restoreTask(snapshot)
                                        if (!snapshot.isCompleted) CareReminderScheduler(requireContext()).schedule(snapshot)
                                    }
                                }.show()
                                dismiss()
                            }
                            true
                        }
                        else -> false
                    }
                }
                show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        preview?.onStart()
        (dialog as? BottomSheetDialog)?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        )?.setBackgroundResource(android.R.color.transparent)
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            peekHeight = resources.getDimensionPixelSize(R.dimen.task_detail_peek_height)
            state = BottomSheetBehavior.STATE_COLLAPSED
            sheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (newState == BottomSheetBehavior.STATE_EXPANDED ||
                        newState == BottomSheetBehavior.STATE_COLLAPSED) {
                        GestureHaptics.confirm(bottomSheet)
                    }
                }
                override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit
            }.also(::addBottomSheetCallback)
        }
        if (!MotionPrefs.animationsEnabled(requireContext())) {
            dialog?.window?.setWindowAnimations(0)
        }
    }

    override fun onDestroyView() {
        sheetCallback?.let { (dialog as? BottomSheetDialog)?.behavior?.removeBottomSheetCallback(it) }
        sheetCallback = null
        preview?.onDestroy()
        preview = null
        _binding = null
        super.onDestroyView()
    }

    override fun onPause() { preview?.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); preview?.onResume() }
    override fun onStop() { preview?.onStop(); super.onStop() }
    override fun onLowMemory() { preview?.onLowMemory(); super.onLowMemory() }

    /** The stored day is UTC-based; reconstruct its date before applying local task time. */
    private fun localStartMillis(day: Long, minutes: Int): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = day * 86_400_000L
        }
        return Calendar.getInstance().apply {
            clear()
            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH),
                minutes / 60, minutes % 60)
        }.timeInMillis
    }

    companion object {
        fun newInstance(task: CareTaskSummary): TaskDetailSheet = TaskDetailSheet().apply {
            arguments = Bundle().apply {
                putString("title", task.title)
                putLong("id", task.id)
                putString("pet", task.petName)
                putString("category", task.category)
                putString("supplies", task.requiredSupplies)
                putString("notes", task.notes)
                putInt("minutes", task.reminderMinutesOfDay)
                putLong("day", task.dueDateEpochDay)
                putDouble("latitude", task.latitude ?: Double.NaN)
                putDouble("longitude", task.longitude ?: Double.NaN)
                putLong("placeId", task.placeId ?: 0L)
                putInt("petColorIndex", task.petColorIndex)
                putBoolean("completed", task.isCompleted)
            }
        }
    }
}
