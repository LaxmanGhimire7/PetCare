package com.example.petcare.ui.integration

import com.example.petcare.ui.UiSnackbar
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ShareCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.petcare.R
import com.example.petcare.data.local.CaregiverPreferences
import com.example.petcare.data.local.PetCareRepositories
import com.example.petcare.design.DelegationLog
import com.example.petcare.design.applySystemBarPaddingWithKeyboard
import com.example.petcare.databinding.FragmentDelegationPreviewBinding
import com.example.petcare.databinding.ItemDelegationPetPhotoBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lets a caregiver checklist be edited before opening SMS or the Android share sheet. */
class DelegationPreviewFragment : Fragment() {
    private var _binding: FragmentDelegationPreviewBinding? = null
    private val binding get() = _binding!!
    private var photos = emptyList<PetPhotoAttachment>()
    private val petIds by lazy {
        requireArguments().getLongArray(PET_IDS)?.toList().orEmpty()
    }
    private val contactPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@registerForActivityResult
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        // ACTION_PICK on Phone.CONTENT_URI grants access to this chosen phone row only.
        try {
            requireContext().contentResolver.query(uri, arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    binding.caregiverNameInput.setText(cursor.getString(0).orEmpty())
                    binding.caregiverPhoneInput.setText(cursor.getString(1).orEmpty())
                } else showContactError()
            } ?: showContactError()
        } catch (_: SecurityException) { showContactError() }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        FragmentDelegationPreviewBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, state: Bundle?) {
        view.applySystemBarPaddingWithKeyboard()
        val careDetails = requireArguments().getString(MESSAGE).orEmpty().trim()
        binding.messageInput.setText(if (careDetails.isBlank()) "" else
            getString(R.string.delegation_message_template, careDetails))
        petIds.firstOrNull()?.let { id ->
            CaregiverPreferences(requireContext()).get(id)?.let { (name, number) ->
                binding.caregiverNameInput.setText(name)
                binding.caregiverPhoneInput.setText(number)
            }
        }
        loadPetPhotos()
        binding.pickContactButton.setOnClickListener {
            try {
                contactPicker.launch(Intent(Intent.ACTION_PICK,
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
            } catch (_: ActivityNotFoundException) { showContactError() }
        }
        binding.sendSmsButton.setOnClickListener { sendSms() }
        binding.shareButton.setOnClickListener { share() }
    }

    private fun loadPetPhotos() {
        val appContext = requireContext().applicationContext
        binding.shareButton.isEnabled = false
        binding.petPhotosEmpty.setText(R.string.delegation_loading_pet_photos)
        viewLifecycleOwner.lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val repository = PetCareRepositories(appContext).pets
                    petIds.distinct().mapNotNull { id ->
                        repository.getPet(id)?.let { PetPhotoAttachmentFactory.prepare(appContext, it) }
                    }
                }.getOrDefault(emptyList())
            }
            if (_binding == null) return@launch
            photos = loaded
            binding.petPhotosContainer.removeAllViews()
            loaded.forEach { photo ->
                val item = ItemDelegationPetPhotoBinding.inflate(layoutInflater,
                    binding.petPhotosContainer, false)
                item.petPhotoPreview.contentDescription =
                    getString(R.string.delegation_pet_photo_name, photo.petName)
                item.petPhotoPreview.load(photo.uri)
                item.petPhotoName.text = photo.petName
                binding.petPhotosContainer.addView(item.root)
            }
            binding.petPhotosSection.isVisible = loaded.isNotEmpty()
            binding.petPhotosEmpty.isVisible = loaded.isEmpty()
            binding.petPhotosEmpty.setText(R.string.delegation_no_pet_photo)
            binding.shareButton.isEnabled = true
        }
    }

    private fun rememberCaregiver() {
        val name = binding.caregiverNameInput.text?.toString()?.trim().orEmpty()
        val number = binding.caregiverPhoneInput.text?.toString()?.trim().orEmpty()
        if (name.isNotBlank() && number.isNotBlank())
            CaregiverPreferences(requireContext()).put(petIds, name, number)
    }

    private fun sendSms() {
        rememberCaregiver()
        val number = binding.caregiverPhoneInput.text?.toString()?.trim().orEmpty()
        val message = binding.messageInput.text?.toString().orEmpty()
        try {
            startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))
                .putExtra("sms_body", message))
            val caregiver = binding.caregiverNameInput.text?.toString()?.trim().orEmpty()
                .ifBlank { getString(R.string.pc_someone) }
            DelegationLog.record(requireContext(), caregiver)
        } catch (_: ActivityNotFoundException) { shareText() }
    }

    private fun share() {
        rememberCaregiver()
        if (photos.isNotEmpty()) {
            val message = binding.messageInput.text?.toString().orEmpty()
            try {
                startActivity(Intent.createChooser(photoIntent(message),
                    getString(R.string.share_checklist_title)))
            } catch (_: ActivityNotFoundException) {
                UiSnackbar.make(binding.root, R.string.delegation_photo_unavailable,
                    Snackbar.LENGTH_LONG).show()
            }
            return
        }
        shareText()
    }

    private fun shareText() {
        ShareCompat.IntentBuilder(requireActivity())
            .setType("text/plain")
            .setText(binding.messageInput.text?.toString().orEmpty())
            .setChooserTitle(R.string.share_checklist_title)
            .startChooser()
    }

    private fun photoIntent(message: String): Intent {
        val multiple = photos.size > 1
        val intent = Intent(if (multiple) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_TEXT, message)
        if (multiple) intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM,
            ArrayList(photos.map(PetPhotoAttachment::uri)))
        else intent.putExtra(Intent.EXTRA_STREAM, photos.first().uri)
        intent.clipData = ClipData.newUri(requireContext().contentResolver,
            getString(R.string.delegation_pet_photos), photos.first().uri).apply {
            photos.drop(1).forEach { addItem(ClipData.Item(it.uri)) }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return intent
    }

    private fun showContactError() {
        UiSnackbar.make(binding.root, R.string.contact_unavailable, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() { photos = emptyList(); _binding = null; super.onDestroyView() }

    companion object {
        const val MESSAGE = "checklistMessage"
        const val PET_IDS = "checklistPetIds"
    }
}
