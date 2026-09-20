package com.example.petcare.ui.pets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.load
import com.example.petcare.R
import com.example.petcare.databinding.FragmentPhotoViewerBinding
import com.example.petcare.databinding.ItemZoomPhotoBinding
import com.example.petcare.ui.GestureHaptics

/** Full-screen gallery for every saved photo of the selected pet. */
class PhotoViewerFragment : Fragment() {
    private var _binding: FragmentPhotoViewerBinding? = null
    private val binding get() = _binding!!
    private var pageCallback: ViewPager2.OnPageChangeCallback? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentPhotoViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        val photos = arguments?.getStringArrayList(PHOTO_URIS).orEmpty()
        if (photos.isEmpty()) {
            findNavController().navigateUp()
            return
        }
        binding.photoCloseButton.setOnClickListener { findNavController().navigateUp() }
        binding.photoPager.adapter = PhotoAdapter(photos) {
            findNavController().navigateUp()
        }
        pageCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position > 0) GestureHaptics.confirm(binding.photoPager)
                binding.photoPageCount.text = getString(R.string.photo_page_count,
                    position + 1, photos.size)
            }
        }.also(binding.photoPager::registerOnPageChangeCallback)
        binding.photoPager.setCurrentItem(arguments?.getInt(START_INDEX, 0)
            ?.coerceIn(photos.indices) ?: 0, false)
    }

    override fun onDestroyView() {
        pageCallback?.let(binding.photoPager::unregisterOnPageChangeCallback)
        pageCallback = null
        binding.photoPager.adapter = null
        _binding = null
        super.onDestroyView()
    }

    /** Loads one URI and gives the zoom view authority over its own touch gestures. */
    private class PhotoAdapter(
        private val photos: List<String>, private val dismiss: () -> Unit
    ) : RecyclerView.Adapter<PhotoAdapter.Holder>() {
        override fun getItemCount() = photos.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
            ItemZoomPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.binding.zoomPhoto.load(photos[position])
            holder.binding.zoomPhoto.onDismiss = dismiss
        }
        class Holder(val binding: ItemZoomPhotoBinding) : RecyclerView.ViewHolder(binding.root)
    }

    companion object {
        const val PHOTO_URIS = "photoUris"
        const val START_INDEX = "startIndex"
    }
}
