package com.example.petcare.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.petcare.R
import com.example.petcare.data.local.AuthPreferences
import com.example.petcare.databinding.FragmentSettingsBinding

/** Primary Settings destination, reached from both phone and tablet navigation. */
class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        val auth = AuthPreferences(requireContext())
        binding.accountText.text = getString(
            R.string.settings_account,
            auth.userName().orEmpty()
        )
        binding.reminderSettingsButton.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_reminder_settings)
        }
        binding.signOutButton.setOnClickListener {
            auth.signOut()
            findNavController().navigate(R.id.action_settings_to_login)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
