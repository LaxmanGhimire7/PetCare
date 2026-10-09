package com.example.petcare.ui.integration

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.example.petcare.data.local.pet.PetEntity
import java.io.File

/** Copies a saved pet photo to a shareable cache URI owned by PetCare. */
internal data class PetPhotoAttachment(val petName: String, val uri: Uri)

internal object PetPhotoAttachmentFactory {
    fun prepare(context: Context, pet: PetEntity): PetPhotoAttachment? {
        val savedUri = pet.photos().firstOrNull()?.let(Uri::parse) ?: return null
        return runCatching {
            val mime = context.contentResolver.getType(savedUri) ?: "image/jpeg"
            if (!mime.startsWith("image/")) return null
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                ?.takeIf { it.matches(Regex("[a-zA-Z0-9]+")) } ?: "jpg"
            val directory = File(context.cacheDir, "shared_pet_photos").apply { mkdirs() }
            val copy = File(directory, "pet_${pet.id}.$extension")
            context.contentResolver.openInputStream(savedUri)?.use { input ->
                copy.outputStream().use(input::copyTo)
            } ?: return null
            PetPhotoAttachment(pet.name, FileProvider.getUriForFile(context,
                "${context.packageName}.fileprovider", copy))
        }.getOrNull()
    }
}
