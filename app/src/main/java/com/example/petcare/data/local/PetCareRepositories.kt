package com.example.petcare.data.local

import android.content.Context
import com.example.petcare.data.local.care.CareTaskRepository
import com.example.petcare.data.local.expense.ExpenseRepository
import com.example.petcare.data.local.pet.PetDeletionRepository
import com.example.petcare.data.local.pet.PetRepository
import com.example.petcare.data.local.provider.ProviderRepository

/** Creates account-scoped repositories; UI code never needs a DAO or database instance. */
class PetCareRepositories(context: Context) {
    private val appContext = context.applicationContext
    private val database = PetCareDatabase.getInstance(appContext)
    val ownerId: Long = AuthPreferences(appContext).ownerId()

    val pets: PetRepository by lazy { PetRepository(database.petDao(), ownerId) }
    val tasks: CareTaskRepository by lazy { CareTaskRepository(database.careTaskDao(), ownerId) }
    val expenses: ExpenseRepository by lazy { ExpenseRepository(database.expenseDao(), ownerId) }
    val places: ProviderRepository by lazy { ProviderRepository(database.providerDao(), ownerId) }
    val petDeletion: PetDeletionRepository by lazy { PetDeletionRepository(database, ownerId) }
    val accountData: AccountDataRepository by lazy { AccountDataRepository(database, ownerId) }
}
