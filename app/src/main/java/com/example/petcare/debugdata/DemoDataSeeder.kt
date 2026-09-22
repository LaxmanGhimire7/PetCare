package com.example.petcare.debugdata

import android.content.Context
import com.example.petcare.R
import com.example.petcare.data.local.PetCareRepositories
import com.example.petcare.data.local.care.CARE_FREQUENCY_DAILY
import com.example.petcare.data.local.care.CARE_FREQUENCY_MONTHLY
import com.example.petcare.data.local.care.CARE_FREQUENCY_ONE_TIME
import com.example.petcare.data.local.care.CARE_FREQUENCY_WEEKLY
import com.example.petcare.data.local.user.AuthRepository
import com.example.petcare.data.local.provider.ProviderEntity
import com.example.petcare.ui.home.LocalDayClock
import java.util.Calendar

/** Creates the isolated Max and Luna account used for debug screenshots and demonstrations. */
class DemoDataSeeder(private val context: Context) {
    suspend fun load() {
        val auth = AuthRepository(context)
        val email = context.getString(R.string.demo_email)
        val password = context.getString(R.string.demo_password)
        if (!auth.register(context.getString(R.string.demo_owner), email, password, true)) {
            check(auth.signIn(email, password, true))
        }
        val repositories = PetCareRepositories(context)
        repositories.accountData.clear()
        val max = repositories.pets.addPet(
            context.getString(R.string.demo_max), context.getString(R.string.demo_dog),
            context.getString(R.string.demo_golden_retriever), context.getString(R.string.demo_max_age),
            context.getString(R.string.demo_max_weight), "", "", "", "", "", "", "", emptyList(), 0
        )
        val luna = repositories.pets.addPet(
            context.getString(R.string.demo_luna), context.getString(R.string.demo_cat),
            context.getString(R.string.demo_shorthair), context.getString(R.string.demo_luna_age),
            context.getString(R.string.demo_luna_weight), "", "", "", "", "", "", "", emptyList(), 1
        )
        val vetId = repositories.places.add(ProviderEntity(
            ownerId = repositories.ownerId,
            name = context.getString(R.string.demo_vet), type = context.getString(R.string.category_clinic),
            address = context.getString(R.string.demo_vet_address), latitude = 27.7172,
            longitude = 85.3240, openingHours = context.getString(R.string.demo_opening_hours),
            phone = context.getString(R.string.demo_phone)
        ))
        repositories.places.add(ProviderEntity(ownerId = repositories.ownerId,
            name = context.getString(R.string.demo_groomer), type = context.getString(R.string.category_grooming),
            address = context.getString(R.string.demo_groomer_address), latitude = 27.7104, longitude = 85.3188))
        repositories.places.add(ProviderEntity(ownerId = repositories.ownerId,
            name = context.getString(R.string.demo_park), type = context.getString(R.string.category_park),
            address = context.getString(R.string.demo_park_address), latitude = 27.7240, longitude = 85.3216))
        repositories.places.add(ProviderEntity(ownerId = repositories.ownerId,
            name = context.getString(R.string.demo_store), type = context.getString(R.string.category_supply),
            address = context.getString(R.string.demo_store_address), latitude = 27.7055, longitude = 85.3297))

        val today = LocalDayClock.todayEpochDay()
        val now = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
        val times = listOf(
            (now - 240).coerceAtLeast(60), (now - 180).coerceAtLeast(90),
            (now - 120).coerceAtLeast(120), (now - 45).coerceAtLeast(150),
            (now + 60).coerceAtMost(1_380), (now + 180).coerceAtMost(1_410)
        )
        val completed = listOf(
            repositories.tasks.addTask(max.id, context.getString(R.string.demo_morning_feed), today,
                times[0], context.getString(R.string.category_feeding), CARE_FREQUENCY_ONE_TIME),
            repositories.tasks.addTask(luna.id, context.getString(R.string.demo_litter_box), today,
                times[1], context.getString(R.string.category_general), CARE_FREQUENCY_ONE_TIME),
            repositories.tasks.addTask(luna.id, context.getString(R.string.demo_breakfast), today,
                times[2], context.getString(R.string.category_feeding), CARE_FREQUENCY_ONE_TIME)
        )
        completed.forEach { repositories.tasks.completeTask(it.id) }
        repositories.tasks.addTask(max.id, context.getString(R.string.demo_flea_treatment), today,
            times[3], context.getString(R.string.category_healthcare), CARE_FREQUENCY_MONTHLY)
        repositories.tasks.addTask(luna.id, context.getString(R.string.demo_brushing), today,
            times[4], context.getString(R.string.category_grooming), CARE_FREQUENCY_WEEKLY)
        repositories.tasks.addTask(max.id, context.getString(R.string.demo_evening_walk), today,
            times[5], context.getString(R.string.category_exercise), CARE_FREQUENCY_DAILY)
        repositories.tasks.addTask(luna.id, context.getString(R.string.demo_vaccination), today + 2,
            10 * 60, context.getString(R.string.category_healthcare), CARE_FREQUENCY_ONE_TIME,
            notes = context.getString(R.string.demo_vaccination_note), latitude = 27.7172,
            longitude = 85.3240, placeId = vetId)
        repositories.tasks.addTask(max.id, context.getString(R.string.demo_grooming), today + 3,
            11 * 60, context.getString(R.string.category_grooming), CARE_FREQUENCY_ONE_TIME)

        listOf(
            Triple(max.id, context.getString(R.string.expense_category_food), 3_800L),
            Triple(luna.id, context.getString(R.string.expense_category_vet), 5_500L),
            Triple(max.id, context.getString(R.string.expense_category_grooming), 2_350L),
            Triple(luna.id, context.getString(R.string.expense_category_supplies), 1_900L)
        ).forEachIndexed { index, (petId, category, amount) ->
            repositories.expenses.add(petId, category, amount, today - index * 12L,
                context.getString(R.string.demo_expense_note))
        }
    }
}
