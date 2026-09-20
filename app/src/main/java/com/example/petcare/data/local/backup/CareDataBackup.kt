package com.example.petcare.data.local.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.room.withTransaction
import com.example.petcare.data.local.AuthPreferences
import com.example.petcare.data.local.PetCareDatabase
import com.example.petcare.data.local.care.CareTaskEntity
import com.example.petcare.data.local.expense.ExpenseEntity
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.data.local.provider.ProviderEntity
import com.example.petcare.reminders.CareReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.Calendar
import java.util.TimeZone

/** Portable backup of one account's care data, including pet photo bytes. Credentials are excluded. */
class CareDataBackup(private val context: Context) {
    private val app = context.applicationContext
    private val database = PetCareDatabase.getInstance(app)
    private val ownerId = AuthPreferences(app).ownerId()

    suspend fun export(): String = withContext(Dispatchers.IO) {
        require(ownerId > 0)
        val pets = database.petDao().observeAll(ownerId).first()
        val places = database.providerDao().observeAll(ownerId).first()
        val tasks = pets.flatMap { database.careTaskDao().getForPet(it.id, ownerId) }
        val expenses = pets.flatMap { database.expenseDao().getForPet(it.id, ownerId) }
        JSONObject().apply {
            put("formatVersion", 1)
            put("pets", JSONArray().apply { pets.forEach { put(petJson(it)) } })
            put("places", JSONArray().apply { places.forEach { put(placeJson(it)) } })
            put("tasks", JSONArray().apply { tasks.forEach { put(taskJson(it)) } })
            put("expenses", JSONArray().apply { expenses.forEach { put(expenseJson(it)) } })
        }.toString(2)
    }

    /** Validates the whole document before inserting; Room rolls back relational changes on failure. */
    suspend fun restore(input: InputStream): Int = withContext(Dispatchers.IO) {
        require(ownerId > 0)
        val root = JSONObject(input.bufferedReader(Charsets.UTF_8).use { it.readText() })
        require(root.getInt("formatVersion") == 1) { "Unsupported backup version" }
        val pets = root.getJSONArray("pets")
        val places = root.getJSONArray("places")
        val tasks = root.getJSONArray("tasks")
        val expenses = root.getJSONArray("expenses")
        val createdPhotos = mutableListOf<File>()
        val createdTasks = mutableListOf<CareTaskEntity>()
        try {
            database.withTransaction {
                val placeIds = mutableMapOf<Long, Long>()
                repeat(places.length()) { index ->
                    val row = places.getJSONObject(index)
                    val oldId = row.getLong("id")
                    placeIds[oldId] = database.providerDao().insert(placeFromJson(row))
                }
                val petIds = mutableMapOf<Long, Long>()
                repeat(pets.length()) { index ->
                    val row = pets.getJSONObject(index)
                    val oldId = row.getLong("id")
                    petIds[oldId] = database.petDao().insert(petFromJson(row, createdPhotos))
                }
                val taskIds = mutableMapOf<Long, Long>()
                val importedTasks = mutableListOf<Pair<CareTaskEntity, Long?>>()
                var order = database.careTaskDao().nextSortOrder(ownerId)
                repeat(tasks.length()) { index ->
                    val row = tasks.getJSONObject(index)
                    val oldId = row.getLong("id")
                    val source = taskFromJson(row, petIds, placeIds).copy(sortOrder = order++)
                    val newId = database.careTaskDao().insert(source)
                    taskIds[oldId] = newId
                    createdTasks += source.copy(id = newId)
                    importedTasks += source.copy(id = newId) to row.optNullableLong("generatedFromId")
                }
                importedTasks.forEach { (task, oldOrigin) ->
                    oldOrigin?.let { origin ->
                        database.careTaskDao().update(task.copy(generatedFromId = taskIds[origin]))
                    }
                }
                repeat(expenses.length()) { index ->
                    database.expenseDao().insert(expenseFromJson(expenses.getJSONObject(index), petIds))
                }
            }
        } catch (failure: Exception) {
            createdPhotos.forEach { it.delete() }
            throw failure
        }
        val local = Calendar.getInstance()
        val today = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis / 86_400_000L
        runCatching {
            val scheduler = CareReminderScheduler(app)
            createdTasks.filter { !it.isCompleted && it.dueDateEpochDay >= today }
                .forEach(scheduler::schedule)
        }
        pets.length() + places.length() + tasks.length() + expenses.length()
    }

    private fun petJson(pet: PetEntity) = JSONObject().apply {
        put("id", pet.id); put("name", pet.name); put("species", pet.species)
        put("colorIndex", pet.colorIndex); put("breed", pet.breed); put("age", pet.age)
        put("weight", pet.weight); put("dietaryPreferences", pet.dietaryPreferences)
        put("vaccinationHistory", pet.vaccinationHistory); put("allergies", pet.allergies)
        put("favoriteToys", pet.favoriteToys); put("medicalRecords", pet.medicalRecords)
        put("groomingRoutine", pet.groomingRoutine); put("healthNotes", pet.healthNotes)
        put("photos", JSONArray().apply { pet.photos().forEach { uri ->
            val bytes = app.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
                ?: error("A pet photo could not be read")
            put(Base64.encodeToString(bytes, Base64.NO_WRAP))
        } })
    }

    private fun petFromJson(row: JSONObject, created: MutableList<File>): PetEntity {
        val photos = row.getJSONArray("photos")
        val uris = mutableListOf<String>()
        repeat(photos.length()) { index ->
            val file = File(File(app.filesDir, "pet_photos").apply { mkdirs() },
                "${UUID.randomUUID()}.jpg")
            created += file
            file.writeBytes(Base64.decode(photos.getString(index), Base64.DEFAULT))
            uris += Uri.fromFile(file).toString()
        }
        return PetEntity(ownerId = ownerId, name = row.getString("name"),
            species = row.getString("species"), colorIndex = row.getInt("colorIndex"),
            breed = row.getString("breed"), age = row.getString("age"),
            weight = row.getString("weight"), dietaryPreferences = row.getString("dietaryPreferences"),
            vaccinationHistory = row.getString("vaccinationHistory"), allergies = row.getString("allergies"),
            favoriteToys = row.getString("favoriteToys"), medicalRecords = row.getString("medicalRecords"),
            groomingRoutine = row.getString("groomingRoutine"), healthNotes = row.getString("healthNotes"),
            photoUri = uris.firstOrNull(), photoUris = uris.joinToString(PetEntity.PHOTO_SEPARATOR))
    }

    private fun placeJson(place: ProviderEntity) = JSONObject().apply {
        put("id", place.id); put("name", place.name); put("type", place.type)
        put("address", place.address); put("latitude", place.latitude)
        put("longitude", place.longitude); put("openingHours", place.openingHours)
        put("phone", place.phone); put("bookingUrl", place.bookingUrl)
    }

    private fun placeFromJson(row: JSONObject) = ProviderEntity(ownerId = ownerId,
        name = row.getString("name"), type = row.getString("type"), address = row.getString("address"),
        latitude = row.optNullableDouble("latitude"), longitude = row.optNullableDouble("longitude"),
        openingHours = row.getString("openingHours"), phone = row.getString("phone"),
        bookingUrl = row.getString("bookingUrl"))

    private fun taskJson(task: CareTaskEntity) = JSONObject().apply {
        put("id", task.id); put("petId", task.petId); put("title", task.title)
        put("dueDateEpochDay", task.dueDateEpochDay)
        put("reminderMinutesOfDay", task.reminderMinutesOfDay)
        put("category", task.category); put("frequency", task.frequency)
        put("requiredSupplies", task.requiredSupplies); put("notes", task.notes)
        put("isCompleted", task.isCompleted); put("latitude", task.latitude)
        put("longitude", task.longitude); put("placeId", task.placeId)
        put("sortOrder", task.sortOrder); put("generatedFromId", task.generatedFromId)
    }

    private fun taskFromJson(row: JSONObject, pets: Map<Long, Long>, places: Map<Long, Long>) =
        CareTaskEntity(ownerId = ownerId,
            petId = pets[row.getLong("petId")] ?: error("Task has no pet"),
            title = row.getString("title"), dueDateEpochDay = row.getLong("dueDateEpochDay"),
            reminderMinutesOfDay = row.getInt("reminderMinutesOfDay"),
            category = row.getString("category"), frequency = row.getString("frequency"),
            requiredSupplies = row.getString("requiredSupplies"), notes = row.getString("notes"),
            isCompleted = row.getBoolean("isCompleted"),
            latitude = row.optNullableDouble("latitude"), longitude = row.optNullableDouble("longitude"),
            placeId = row.optNullableLong("placeId")?.let(places::get))

    private fun expenseJson(expense: ExpenseEntity) = JSONObject().apply {
        put("id", expense.id); put("petId", expense.petId); put("category", expense.category)
        put("amountCents", expense.amountCents); put("dateEpochDay", expense.dateEpochDay)
        put("note", expense.note)
    }

    private fun expenseFromJson(row: JSONObject, pets: Map<Long, Long>) = ExpenseEntity(
        ownerId = ownerId, petId = pets[row.getLong("petId")] ?: error("Expense has no pet"),
        category = row.getString("category"), amountCents = row.getLong("amountCents"),
        dateEpochDay = row.getLong("dateEpochDay"), note = row.getString("note"))

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (isNull(key) || !has(key)) null else getLong(key)
    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (isNull(key) || !has(key)) null else getDouble(key)
}
