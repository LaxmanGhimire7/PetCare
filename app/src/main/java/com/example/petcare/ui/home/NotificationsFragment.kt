package com.example.petcare.ui.home

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.petcare.R
import com.example.petcare.design.InboxItem
import com.example.petcare.design.NotificationActions
import com.example.petcare.design.NotificationsScreenBinder

/** Shows the app's notification centre and handles its item actions. */
class NotificationsFragment : Fragment(R.layout.pc_fragment_notifications), NotificationActions {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        NotificationsScreenBinder(view, viewLifecycleOwner, this)
    }

    override fun onOpen(item: InboxItem) {
        item.taskId?.let { taskId ->
            TaskDetailSheet.newInstance(taskId).show(childFragmentManager, "task_detail")
        }
    }

    override fun onBack() {
        findNavController().navigateUp()
    }
}
