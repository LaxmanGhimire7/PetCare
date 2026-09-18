package com.example.petcare.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.petcare.R
import com.example.petcare.data.local.AuthPreferences
import com.example.petcare.data.local.PetCareDatabase
import com.example.petcare.data.local.care.CareTaskRepository
import com.example.petcare.data.local.care.CareTaskSummary
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.data.local.pet.PetRepository
import com.example.petcare.databinding.FragmentHomeDashboardBinding
import com.example.petcare.databinding.ItemCareTaskBinding
import com.example.petcare.databinding.ItemPetProfileBinding
import com.example.petcare.reminders.CareReminderScheduler
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import coil.load
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

class HomeDashboardFragment : Fragment() {

    private var _binding: FragmentHomeDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val authPreferences = AuthPreferences(requireContext())
        val name = authPreferences.userName().orEmpty().ifBlank {
            getString(R.string.default_pet_parent_name)
        }
        binding.greetingText.text = getString(R.string.dashboard_greeting, name)

        val database = PetCareDatabase.getInstance(requireContext())
        val petRepository = PetRepository(database.petDao())
        val careTaskRepository = CareTaskRepository(database.careTaskDao())
        val reminderScheduler = CareReminderScheduler(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                petRepository.observePets().collect { pets ->
                    renderPets(pets, petRepository, careTaskRepository, reminderScheduler)
                    binding.addCareTaskButton.isEnabled = pets.isNotEmpty()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                careTaskRepository.observeUpcoming().collect { careTasks ->
                    renderCareTasks(careTasks, careTaskRepository, reminderScheduler)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                careTaskRepository.observeCompleted().collect { careTasks ->
                    renderCompletedCareTasks(careTasks, careTaskRepository, reminderScheduler)
                }
            }
        }

        binding.addPetButton.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_add_pet)
        }

        binding.addCareTaskButton.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_add_care_task)
        }

        binding.signOutButton.setOnClickListener {
            authPreferences.signOut()
            findNavController().navigate(R.id.action_home_to_login)
        }
    }

    private fun renderPets(
        pets: List<PetEntity>,
        petRepository: PetRepository,
        careTaskRepository: CareTaskRepository,
        reminderScheduler: CareReminderScheduler
    ) {
        binding.petSummaryText.text = resources.getQuantityString(
            R.plurals.pet_profile_count,
            pets.size,
            pets.size
        )
        binding.petListContainer.removeAllViews()

        pets.forEach { pet ->
            val petBinding = ItemPetProfileBinding.inflate(
                layoutInflater,
                binding.petListContainer,
                false
            )
            petBinding.petNameText.text = pet.name
            petBinding.petSpeciesText.text = pet.species
            petBinding.petHealthNotesText.text = pet.healthNotes
            petBinding.petHealthNotesText.visibility = if (pet.healthNotes.isBlank()) {
                View.GONE
            } else {
                View.VISIBLE
            }
            petBinding.petPhotoImage.visibility = if (pet.photoUri.isNullOrBlank()) {
                View.GONE
            } else {
                petBinding.petPhotoImage.load(pet.photoUri)
                View.VISIBLE
            }
            petBinding.editPetButton.setOnClickListener {
                findNavController().navigate(
                    R.id.action_home_to_edit_pet,
                    Bundle().apply { putLong(PET_ID_ARGUMENT, pet.id) }
                )
            }
            petBinding.removePetButton.setOnClickListener {
                confirmPetRemoval(pet, petRepository, careTaskRepository, reminderScheduler)
            }
            binding.petListContainer.addView(petBinding.root)
        }
    }

    private fun confirmPetRemoval(
        pet: PetEntity,
        petRepository: PetRepository,
        careTaskRepository: CareTaskRepository,
        reminderScheduler: CareReminderScheduler
    ) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.remove_pet_title)
            .setMessage(getString(R.string.remove_pet_message, pet.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.remove) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    careTaskRepository.getTaskIdsForPet(pet.id).forEach(reminderScheduler::cancel)
                    petRepository.deletePet(pet.id)
                }
            }
            .show()
    }

    private fun renderCareTasks(
        careTasks: List<CareTaskSummary>,
        careTaskRepository: CareTaskRepository,
        reminderScheduler: CareReminderScheduler
    ) {
        binding.upcomingCareEmptyText.visibility = if (careTasks.isEmpty()) View.VISIBLE else View.GONE
        binding.careTaskListContainer.removeAllViews()

        careTasks.forEach { careTask ->
            val careTaskBinding = ItemCareTaskBinding.inflate(
                layoutInflater,
                binding.careTaskListContainer,
                false
            )
            careTaskBinding.careTaskTitleText.text = careTask.title
            careTaskBinding.careTaskDetailText.text = getString(
                R.string.care_task_detail,
                careTask.petName,
                formatDate(careTask.dueDateEpochDay)
            )
            careTaskBinding.markCareTaskDoneButton.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    careTaskRepository.markCompleted(careTask.id)
                    reminderScheduler.cancel(careTask.id)
                }
            }
            careTaskBinding.editCareTaskButton.setOnClickListener {
                openCareTaskEditor(careTask.id)
            }
            careTaskBinding.deleteCareTaskButton.setOnClickListener {
                confirmCareTaskDeletion(careTask, careTaskRepository, reminderScheduler)
            }
            binding.careTaskListContainer.addView(careTaskBinding.root)
        }
    }

    private fun renderCompletedCareTasks(
        careTasks: List<CareTaskSummary>,
        careTaskRepository: CareTaskRepository,
        reminderScheduler: CareReminderScheduler
    ) {
        binding.completedCareTitle.visibility = if (careTasks.isEmpty()) View.GONE else View.VISIBLE
        binding.completedCareListContainer.removeAllViews()

        careTasks.forEach { careTask ->
            val careTaskBinding = ItemCareTaskBinding.inflate(
                layoutInflater,
                binding.completedCareListContainer,
                false
            )
            careTaskBinding.careTaskTitleText.text = careTask.title
            careTaskBinding.careTaskDetailText.text = getString(
                R.string.completed_care_task_detail,
                careTask.petName,
                formatDate(careTask.dueDateEpochDay)
            )
            careTaskBinding.markCareTaskDoneButton.visibility = View.GONE
            careTaskBinding.editCareTaskButton.setOnClickListener {
                openCareTaskEditor(careTask.id)
            }
            careTaskBinding.deleteCareTaskButton.setOnClickListener {
                confirmCareTaskDeletion(careTask, careTaskRepository, reminderScheduler)
            }
            binding.completedCareListContainer.addView(careTaskBinding.root)
        }
    }

    private fun confirmCareTaskDeletion(
        careTask: CareTaskSummary,
        careTaskRepository: CareTaskRepository,
        reminderScheduler: CareReminderScheduler
    ) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_care_task_title)
            .setMessage(getString(R.string.delete_care_task_message, careTask.title))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    careTaskRepository.deleteTask(careTask.id)
                    reminderScheduler.cancel(careTask.id)
                }
            }
            .show()
    }

    private fun openCareTaskEditor(careTaskId: Long) {
        findNavController().navigate(
            R.id.action_home_to_edit_care_task,
            Bundle().apply { putLong(CARE_TASK_ID_ARGUMENT, careTaskId) }
        )
    }

    private fun formatDate(epochDay: Long): String {
        return DateFormat.getDateInstance(DateFormat.MEDIUM).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(epochDay * MILLIS_PER_DAY))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
        const val CARE_TASK_ID_ARGUMENT = "careTaskId"
        const val PET_ID_ARGUMENT = "petId"
    }
}
