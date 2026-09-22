package com.example.petcare

import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAction
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.util.TreeIterables
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matcher
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Confirms clinic messages and calendar exports enter the editable import flow. */
@RunWith(AndroidJUnit4::class)
class ClinicShareUiTest {
    @Test fun clinicMessageOpensReviewWithRecognisedFields() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            addCategory(Intent.CATEGORY_DEFAULT)
            setPackage(context.packageName)
            putExtra(Intent.EXTRA_TEXT,
                "Luna's vaccination is booked for 12/03/2026 at 5:30pm at Happy Paws Clinic.")
        }
        ActivityScenario.launch<ImportActivity>(share).use {
            waitForText("Vaccination")
            onView(withId(R.id.title_input)).check(matches(withText("Vaccination")))
            onView(withId(R.id.date_input)).check(matches(withText("12/03/2026")))
            onView(withId(R.id.time_input)).check(matches(withText("17:30")))
            onView(withId(R.id.clinic_input)).check(matches(withText("Happy Paws Clinic")))
        }
    }

    @Test fun exportedClinicCalendarCanOpenInPetCare() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("content://clinic.example/appointments.ics"), "application/ics")
            addCategory(Intent.CATEGORY_DEFAULT)
            setPackage(context.packageName)
        }
        val resolved = context.packageManager.resolveActivity(intent, 0)
        assertEquals(ImportActivity::class.java.name, resolved?.activityInfo?.name)
    }

    private fun waitForText(expected: String) {
        onView(isRoot()).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isRoot()
            override fun getDescription() = "wait for clinic import review"
            override fun perform(controller: androidx.test.espresso.UiController, root: View) {
                val deadline = System.currentTimeMillis() + 5_000L
                do {
                    if (TreeIterables.breadthFirstViewTraversal(root).any {
                            withText(expected).matches(it) && it.visibility == View.VISIBLE
                        }) return
                    controller.loopMainThreadForAtLeast(50)
                } while (System.currentTimeMillis() < deadline)
                throw AssertionError("Import review did not appear")
            }
        })
    }
}
