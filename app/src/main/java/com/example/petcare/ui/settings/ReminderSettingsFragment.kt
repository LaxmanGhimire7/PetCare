package com.example.petcare.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.petcare.R
import com.example.petcare.data.local.ReminderPreferences
import com.example.petcare.databinding.FragmentReminderSettingsBinding
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.text.DateFormat
import java.util.Calendar

/** Changes the reminder time prefilled on new care tasks. */
class ReminderSettingsFragment : Fragment() {

    private var _binding: FragmentReminderSettingsBinding? = null
    private val binding get() = _binding!!
    private var selectedMinutesOfDay = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReminderSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        selectedMinutesOfDay = ReminderPreferences(requireContext()).defaultReminderMinutesOfDay()
        binding.defaultReminderTimeInput.setText(formatTime(selectedMinutesOfDay))
        binding.defaultReminderTimeInput.setOnClickListener { showTimePicker() }
        binding.saveReminderSettingsButton.setOnClickListener {
            ReminderPreferences(requireContext()).setDefaultReminderMinutesOfDay(selectedMinutesOfDay)
            Toast.makeText(requireContext(), R.string.reminder_settings_saved, Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }
        binding.cancelButton.setOnClickListener { findNavController().navigateUp() }
    }

    private fun showTimePicker() {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(
                if (android.text.format.DateFormat.is24HourFormat(requireContext())) {
                    TimeFormat.CLOCK_24H
                } else {
                    TimeFormat.CLOCK_12H
                }
            )
            .setHour(selectedMinutesOfDay / MINUTES_PER_HOUR)
            .setMinute(selectedMinutesOfDay % MINUTES_PER_HOUR)
            .setTitleText(R.string.select_reminder_time)
            .build()

        picker.addOnPositiveButtonClickListener {
            selectedMinutesOfDay = picker.hour * MINUTES_PER_HOUR + picker.minute
            binding.defaultReminderTimeInput.setText(formatTime(selectedMinutesOfDay))
        }
        picker.show(parentFragmentManager, TIME_PICKER_TAG)
    }

    private fun formatTime(minutesOfDay: Int): String = DateFormat.getTimeInstance(
        DateFormat.SHORT
    ).format(
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutesOfDay / MINUTES_PER_HOUR)
            set(Calendar.MINUTE, minutesOfDay % MINUTES_PER_HOUR)
        }.time
    )

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val MINUTES_PER_HOUR = 60
        const val TIME_PICKER_TAG = "default_reminder_time_picker"
    }
}
