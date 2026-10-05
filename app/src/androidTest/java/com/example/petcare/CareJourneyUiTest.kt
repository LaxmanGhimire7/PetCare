package com.example.petcare

import android.Manifest
import android.os.Build
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.util.TreeIterables
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcare.data.local.AuthPreferences
import com.example.petcare.data.local.PetCareDatabase
import com.example.petcare.data.local.PetCareRepositories
import com.example.petcare.ui.GestureCoachPrefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

/** Exercises account, pet, and task navigation through the actual XML UI. */
@RunWith(AndroidJUnit4::class)
class CareJourneyUiTest {
    @Test
    fun registerLoginAddPetAddTaskAndCompleteIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        AuthPreferences(context).signOut()
        GestureCoachPrefs(context).markSeen()
        val email = "journey${System.currentTimeMillis()}@petcare.test"
        val password = "Distant!Cedar7River"
        val petName = "Journey pet"
        val taskTitle = "Journey feeding"

        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor(withId(R.id.pcWelcomeCreate))
            onView(withId(R.id.pcWelcomeCreate)).perform(scrollTo(), click())
            onView(withId(R.id.pcRegisterName)).perform(scrollTo(), replaceText("Journey user"))
            onView(withId(R.id.pcRegisterEmail)).perform(scrollTo(), replaceText(email))
            onView(withId(R.id.pcRegisterPassword)).perform(scrollTo(), replaceText(password))
            onView(withId(R.id.pcRegisterConfirm)).perform(scrollTo(),
                replaceText(password), closeSoftKeyboard())
            onView(withId(R.id.pcRegisterQuestion)).perform(scrollTo(), click())
            onView(withText(context.resources.getStringArray(R.array.pc_security_questions)[0])).perform(click())
            onView(withId(R.id.pcRegisterAnswer)).perform(scrollTo(), replaceText("Mochi"), closeSoftKeyboard())
            onView(withId(R.id.pcRegisterSubmit)).perform(scrollTo(), click())
            waitFor(withId(R.id.settingsFragment))
            onView(withId(R.id.settingsFragment)).perform(click())
            onView(withId(R.id.sign_out_button)).perform(scrollTo(), click())

            waitFor(withId(R.id.pcWelcomeLogIn))
            onView(withId(R.id.pcWelcomeLogIn)).perform(scrollTo(), click())
            onView(withId(R.id.pcLoginEmail)).perform(scrollTo(), replaceText(email))
            onView(withId(R.id.pcLoginPassword)).perform(scrollTo(),
                replaceText(password), closeSoftKeyboard())
            onView(withId(R.id.pcLoginSubmit)).perform(scrollTo(), click())
            waitFor(withId(R.id.pcTodayEmptyAddPet))
            onView(withId(R.id.pcTodayEmptyAddPet)).perform(scrollTo(), click())

            onView(withId(R.id.pet_name_input)).perform(scrollTo(), replaceText(petName))
            onView(withId(R.id.pet_species_input)).perform(scrollTo(),
                replaceText("Cat"), closeSoftKeyboard())
            onView(withId(R.id.save_pet_button)).perform(scrollTo(), click())
            waitFor(withId(R.id.pcTodayFab))
            onView(withId(R.id.pcTodayFab)).perform(click())

            onView(withId(R.id.pcEditorName)).perform(scrollTo(),
                replaceText(taskTitle), closeSoftKeyboard())
            onView(withText(context.resources.getStringArray(R.array.care_categories)[0])).perform(scrollTo(), click())
            onView(withId(R.id.pcEditorDateRow)).perform(scrollTo(), click())
            onView(withId(com.google.android.material.R.id.confirm_button)).perform(click())
            onView(withId(R.id.pcEditorSave)).perform(scrollTo(), click())

            waitFor(withText(taskTitle))
            val ownerId = AuthPreferences(context).ownerId()
            val dao = PetCareDatabase.getInstance(context).careTaskDao()
            val taskId = runBlocking { dao.observeUpcoming(ownerId).first()
                .first { it.title == taskTitle }.id }
            onView(withId(R.id.pcTimelineNode)).perform(scrollTo(), click())
            val completedInRoom = (0 until 30).any {
                if (runBlocking { dao.getById(taskId, ownerId)?.isCompleted } == true) true
                else { Thread.sleep(100); false }
            }
            assertTrue("Completion tap did not update the saved task", completedInRoom)
            onView(withText(taskTitle)).check(matches(
                withEffectiveVisibility(androidx.test.espresso.matcher.ViewMatchers.Visibility.VISIBLE)))

            // A future appointment must not leak into the selected day's timeline.
            val original = runBlocking { dao.getById(taskId, ownerId)!! }
            val futureTitle = "Journey future appointment"
            runBlocking {
                PetCareRepositories(context).tasks.addTask(original.petId, futureTitle,
                    original.dueDateEpochDay + 7, 10 * 60)
            }
            onView(withText(futureTitle)).check(doesNotExist())
            onView(withId(R.id.pcTodayEmpty)).check(matches(withEffectiveVisibility(
                androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE)))
        }
    }

    /** Waits for Room and navigation callbacks while letting the main thread make progress. */
    private fun waitFor(matcher: Matcher<View>, timeoutMs: Long = 10_000) {
        onView(isRoot()).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isRoot()
            override fun getDescription() = "wait for ${matcher}"
            override fun perform(controller: androidx.test.espresso.UiController, root: View) {
                val deadline = System.currentTimeMillis() + timeoutMs
                do {
                    if (TreeIterables.breadthFirstViewTraversal(root).any { matcher.matches(it) &&
                            it.visibility == View.VISIBLE }) return
                    controller.loopMainThreadForAtLeast(50)
                } while (System.currentTimeMillis() < deadline)
                throw AssertionError("View did not appear: $matcher")
            }
        })
    }
}
