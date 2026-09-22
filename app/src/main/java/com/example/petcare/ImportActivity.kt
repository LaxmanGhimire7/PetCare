package com.example.petcare

import com.example.petcare.ui.UiSnackbar
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.petcare.databinding.ActivityImportBinding
import com.example.petcare.integration.AppointmentDraft
import com.example.petcare.integration.SharedAppointmentParser
import com.example.petcare.integration.IcsAppointmentParser
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Receives shared text or calendar files and requires a visible edit pass before task creation. */
class ImportActivity : AppCompatActivity() {
    private lateinit var binding: ActivityImportBinding
    private var drafts: List<AppointmentDraft> = emptyList()
    private var draftIndex = 0
    private val reviewed = arrayListOf<Bundle>()
    private var sharedContent: String? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityImportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.importRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
        binding.continueButton.isEnabled = false
        binding.continueButton.setOnClickListener { continueToTask() }
        lifecycleScope.launch {
            val content = state?.getString(STATE_CONTENT)
                ?: withContext(Dispatchers.IO) { readSharedContent(intent) }
            if (content.isNullOrBlank()) {
                UiSnackbar.make(binding.root, R.string.import_error_read, Snackbar.LENGTH_INDEFINITE).show()
            } else {
                sharedContent = content
                drafts = if (isCalendarType(intent.type) || content.contains("BEGIN:VEVENT"))
                    IcsAppointmentParser.parseAll(content).map { appointment ->
                        AppointmentDraft(appointment.title, appointment.dateEpochDay,
                            appointment.minutesOfDay, appointment.location, appointment.notes)
                    }
                else listOf(SharedAppointmentParser.parse(content, intent.type))
                if (drafts.isEmpty()) UiSnackbar.make(binding.root, R.string.import_error_read,
                    Snackbar.LENGTH_INDEFINITE).show()
                else {
                    // Keep every reviewed event and the current edits across rotation.
                    draftIndex = state?.getInt(STATE_INDEX, 0)?.coerceIn(drafts.indices) ?: 0
                    @Suppress("DEPRECATION")
                    val saved = state?.getParcelableArrayList<Bundle>(STATE_REVIEWED)
                    reviewed.addAll(saved.orEmpty())
                    bind(drafts[draftIndex])
                    state?.let {
                        binding.titleInput.setText(it.getString(STATE_TITLE))
                        binding.dateInput.setText(it.getString(STATE_DATE))
                        binding.timeInput.setText(it.getString(STATE_TIME))
                        binding.clinicInput.setText(it.getString(STATE_CLINIC))
                        binding.notesInput.setText(it.getString(STATE_NOTES))
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        sharedContent?.let { outState.putString(STATE_CONTENT, it) }
        outState.putInt(STATE_INDEX, draftIndex)
        outState.putParcelableArrayList(STATE_REVIEWED, reviewed)
        outState.putString(STATE_TITLE, binding.titleInput.text?.toString())
        outState.putString(STATE_DATE, binding.dateInput.text?.toString())
        outState.putString(STATE_TIME, binding.timeInput.text?.toString())
        outState.putString(STATE_CLINIC, binding.clinicInput.text?.toString())
        outState.putString(STATE_NOTES, binding.notesInput.text?.toString())
    }

    private fun bind(draft: AppointmentDraft) {
        binding.importProgressText.visibility = if (drafts.size > 1) View.VISIBLE else View.GONE
        binding.importProgressText.text = getString(R.string.import_progress, draftIndex + 1, drafts.size)
        binding.continueButton.setText(if (draftIndex == drafts.lastIndex)
            R.string.import_continue else R.string.import_next)
        binding.titleInput.setText(draft.title)
        binding.dateInput.setText(null)
        binding.timeInput.setText(null)
        draft.dateEpochDay?.let { day ->
            binding.dateInput.setText(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date(day * DAY)))
        }
        draft.minutesOfDay?.let {
            binding.timeInput.setText(String.format(Locale.ROOT, "%02d:%02d", it / 60, it % 60))
        }
        binding.clinicInput.setText(draft.clinic)
        binding.notesInput.setText(draft.notes)
        val recognized = mutableListOf<String>()
        val missing = mutableListOf<String>()
        fun field(found: Boolean, label: Int) {
            (if (found) recognized else missing).add(getString(label))
        }
        field(draft.title.isNotBlank(), R.string.import_field_title)
        field(draft.dateEpochDay != null, R.string.import_field_date)
        field(draft.minutesOfDay != null, R.string.import_field_time)
        field(!draft.clinic.isNullOrBlank(), R.string.import_field_clinic)
        binding.recognizedText.text = getString(R.string.import_recognized,
            recognized.joinToString(getString(R.string.import_field_list_separator)))
        binding.missingText.text = getString(R.string.import_missing,
            missing.joinToString(getString(R.string.import_field_list_separator)))
        binding.missingText.visibility = if (missing.isEmpty()) View.GONE else View.VISIBLE
        binding.continueButton.isEnabled = true
    }

    private fun continueToTask() {
        val title = binding.titleInput.text?.toString()?.trim().orEmpty()
        binding.titleLayout.error = if (title.isBlank()) getString(R.string.import_error_title) else null
        val rawDate = binding.dateInput.text?.toString()?.trim().orEmpty()
        val date = if (rawDate.isBlank()) null else runCatching {
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(rawDate)?.time?.div(DAY)
        }.getOrNull()
        binding.dateLayout.error = if (rawDate.isNotBlank() && date == null)
            getString(R.string.import_error_date) else null
        val rawTime = binding.timeInput.text?.toString()?.trim().orEmpty()
        val match = Regex("^([01]?\\d|2[0-3]):([0-5]\\d)$").matchEntire(rawTime)
        val minutes = match?.let { it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }
        binding.timeLayout.error = if (rawTime.isNotBlank() && minutes == null)
            getString(R.string.import_error_time) else null
        if (binding.titleLayout.error != null || binding.dateLayout.error != null ||
            binding.timeLayout.error != null) return
        val clinic = binding.clinicInput.text?.toString()?.trim().orEmpty()
        val notes = binding.notesInput.text?.toString()?.trim().orEmpty()
        val reviewedNotes = if (clinic.isBlank()) notes else listOf(
            getString(R.string.import_clinic_note, clinic), notes
        ).filter(String::isNotBlank).joinToString("\n")
        reviewed.add(Bundle().apply {
            putString("importedTitle", title)
            putLong("importedDateEpochDay", date ?: 0L)
            putInt("importedTimeMinutes", minutes ?: -1)
            putString("importedNotes", reviewedNotes)
            putString("importedClinic", clinic)
        })
        if (draftIndex < drafts.lastIndex) {
            draftIndex++
            bind(drafts[draftIndex])
            return
        }
        startActivity(Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_REVIEWED_IMPORT
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putParcelableArrayListExtra(MainActivity.EXTRA_IMPORT_BATCH, reviewed)
        })
        finish()
    }

    private fun readSharedContent(incoming: Intent): String? {
        @Suppress("DEPRECATION")
        val stream = incoming.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        val uri = stream ?: incoming.data
        // A clinic can share a calendar attachment alongside an email body. Read the
        // attachment first so all VEVENTs reach the review screen.
        val attachmentIsCalendar = uri != null &&
            isCalendarType(runCatching { contentResolver.getType(uri) }.getOrNull())
        if ((isCalendarType(incoming.type) || attachmentIsCalendar) && uri != null)
            readUri(uri)?.let { return it }
        incoming.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            ?.takeIf(String::isNotBlank)?.let { return it.take(MAX_CHARS) }
        return uri?.let(::readUri)
    }

    private fun isCalendarType(type: String?): Boolean = type == "text/calendar" ||
        type == "application/ics" || type == "text/x-vcalendar"

    private fun readUri(uri: Uri): String? {
        return runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val chunk = ByteArray(4096)
                while (output.size() < MAX_CHARS) {
                    val read = input.read(chunk, 0, minOf(chunk.size, MAX_CHARS - output.size()))
                    if (read < 0) break
                    output.write(chunk, 0, read)
                }
                output.toString(Charsets.UTF_8.name())
            }
        }.getOrNull()
    }

    private companion object {
        const val MAX_CHARS = 128 * 1024
        const val DAY = 86_400_000L
        const val STATE_CONTENT = "sharedContent"
        const val STATE_INDEX = "draftIndex"
        const val STATE_REVIEWED = "reviewedEvents"
        const val STATE_TITLE = "reviewTitle"
        const val STATE_DATE = "reviewDate"
        const val STATE_TIME = "reviewTime"
        const val STATE_CLINIC = "reviewClinic"
        const val STATE_NOTES = "reviewNotes"
    }
}
