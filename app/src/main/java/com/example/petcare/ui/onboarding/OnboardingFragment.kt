package com.example.petcare.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.petcare.R
import com.example.petcare.databinding.FragmentOnboardingBinding
import com.example.petcare.databinding.ItemOnboardingBinding
import com.example.petcare.ui.MotionPrefs
import com.google.android.material.snackbar.Snackbar

/** Guides a new account through three useful steps and remains safe to skip. */
class OnboardingFragment : Fragment() {
    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) { granted ->
        Snackbar.make(binding.root, if (granted) R.string.onboarding_notifications_ready
            else R.string.onboarding_notifications_later, Snackbar.LENGTH_SHORT).show()
    }

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, state: Bundle?): View =
        FragmentOnboardingBinding.inflate(inflater, parent, false).also { _binding = it }.root

    override fun onViewCreated(view: View, state: Bundle?) {
        binding.onboardingPager.adapter = PageAdapter()
        binding.onboardingPager.setCurrentItem(state?.getInt(STATE_PAGE) ?: 0, false)
        binding.onboardingPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) { updateActions(position) }
        })
        updateActions(binding.onboardingPager.currentItem)
        // ViewPager2 may restore its page after the initial synchronous currentItem read.
        binding.onboardingPager.post {
            if (_binding != null) updateActions(binding.onboardingPager.currentItem)
        }
        binding.onboardingSkip.setOnClickListener { finish() }
        binding.onboardingNext.setOnClickListener {
            if (binding.onboardingPager.currentItem == 2) finish()
            else binding.onboardingPager.setCurrentItem(binding.onboardingPager.currentItem + 1,
                MotionPrefs.animationsEnabled(requireContext()))
        }
        binding.onboardingExtra.setOnClickListener {
            when (binding.onboardingPager.currentItem) {
                1 -> findNavController().navigate(R.id.action_onboarding_to_add_pet)
                2 -> if (Build.VERSION.SDK_INT >= 33 && requireContext().checkSelfPermission(
                        Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else -> Unit
            }
        }
    }

    private fun updateActions(page: Int) {
        binding.onboardingNext.setText(if (page == 2) R.string.onboarding_finish
            else R.string.onboarding_next)
        binding.onboardingExtra.visibility = if (page == 0) View.GONE else View.VISIBLE
        binding.onboardingExtra.setText(if (page == 1) R.string.add_pet
            else R.string.onboarding_allow_notifications)
        if (page == 2) binding.onboardingExtra.isEnabled = Build.VERSION.SDK_INT >= 33 &&
            requireContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
    }

    private fun finish() {
        OnboardingPrefs(requireContext()).markComplete()
        findNavController().navigate(R.id.action_onboarding_to_home)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_PAGE, binding.onboardingPager.currentItem)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        binding.onboardingPager.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private inner class PageAdapter : RecyclerView.Adapter<PageAdapter.Holder>() {
        private val titles = intArrayOf(R.string.onboarding_care_title,
            R.string.onboarding_pet_title, R.string.onboarding_notification_title)
        private val bodies = intArrayOf(R.string.onboarding_care_body,
            R.string.onboarding_pet_body, R.string.onboarding_notification_body)
        private val icons = intArrayOf(R.drawable.ic_pets, R.drawable.ic_add, R.drawable.ic_alarm)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemOnboardingBinding.inflate(layoutInflater, parent, false))
        override fun getItemCount() = 3
        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.binding.onboardingTitle.setText(titles[position])
            holder.binding.onboardingDescription.setText(bodies[position])
            holder.binding.onboardingImage.setImageResource(icons[position])
        }
        inner class Holder(val binding: ItemOnboardingBinding) : RecyclerView.ViewHolder(binding.root)
    }

    private companion object { const val STATE_PAGE = "onboarding_page" }
}
