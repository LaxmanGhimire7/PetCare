package com.example.petcare.design

import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.petcare.R

/** One pet's share of this month's spending. [amountLabel] already formatted in local currency. */
data class MonthShare(val petName: String, val petColor: PetColor, val amount: Double, val amountLabel: String)

/** [totalLabel] already formatted in local currency. Pass null to MonthSummary for "no spending yet". */
data class MonthSummary(val totalLabel: String, val shares: List<MonthShare>)

data class PetsUiState(
    val pets: List<PetCardItem>,
    val comingUp: List<ComingUpItem>,
    val month: MonthSummary?,
)

interface PetsActions {
    fun onOpenPet(petId: Long, sharedView: View)
    fun onPetMenu(petId: Long, anchor: View)
    fun onAddPet()
    fun onOpenComingUp(taskId: Long)
    fun onOpenMoney()
    fun onAddExpense()
}

/** Binds pc_fragment_pets.xml. Create once in onViewCreated, call [render] on every state change. */
class PetsScreenBinder(
    root: View,
    private val actions: PetsActions,
    loadPhoto: (ImageView, String) -> Unit,
) {
    private val context = root.context
    private val grid: RecyclerView = root.findViewById(R.id.pcPetGrid)
    private val comingUpTitle: View = root.findViewById(R.id.pcComingUpTitle)
    private val comingUp: RecyclerView = root.findViewById(R.id.pcComingUp)
    private val comingUpEmpty: View = root.findViewById(R.id.pcComingUpEmpty)
    private val monthTitle: View = root.findViewById(R.id.pcMonthTitle)
    private val month: View = root.findViewById(R.id.pcMonth)
    private val monthFilled: View = root.findViewById(R.id.pcMonthFilled)
    private val monthEmpty: View = root.findViewById(R.id.pcMonthEmpty)
    private val monthTotal: TextView = root.findViewById(R.id.pcMonthTotal)
    private val monthBar: SegmentedBarView = root.findViewById(R.id.pcMonthBar)
    private val monthLegend: LinearLayout = root.findViewById(R.id.pcMonthLegend)

    private val gridAdapter = PetGridAdapter(
        loadPhoto = loadPhoto,
        onOpen = { pet, view -> actions.onOpenPet(pet.id, view) },
        onMenu = { pet, view -> actions.onPetMenu(pet.id, view) },
        onAdd = actions::onAddPet,
    )
    private val comingAdapter = ComingUpAdapter { actions.onOpenComingUp(it) }

    init {
        root.applySystemBarTopPadding()

        grid.layoutManager = GridLayoutManager(context, 2).also { it.spanSizeLookup = gridAdapter.spanSizeLookup }
        grid.adapter = gridAdapter

        comingUp.layoutManager = LinearLayoutManager(context)
        comingUp.adapter = comingAdapter
        comingUp.addItemDecoration(InsetDividerDecoration(context, insetStartPx = 0))
        comingUp.clipToOutline = true

        root.findViewById<View>(R.id.pcPetsAdd).setOnClickListener { actions.onAddPet() }
        month.setOnClickListener { actions.onOpenMoney() }
        root.findViewById<View>(R.id.pcMonthAddExpense).setOnClickListener { actions.onAddExpense() }
    }

    fun render(state: PetsUiState) {
        gridAdapter.submitList(PetGridBuilder.build(state.pets))

        val hasPets = state.pets.isNotEmpty()
        comingUpTitle.visibility = if (hasPets) View.VISIBLE else View.GONE
        monthTitle.visibility = if (hasPets) View.VISIBLE else View.GONE
        month.visibility = if (hasPets) View.VISIBLE else View.GONE

        comingUp.visibility = if (hasPets && state.comingUp.isNotEmpty()) View.VISIBLE else View.GONE
        comingUpEmpty.visibility = if (hasPets && state.comingUp.isEmpty()) View.VISIBLE else View.GONE
        comingAdapter.submitList(state.comingUp)

        val summary = state.month
        if (summary == null || summary.shares.isEmpty()) {
            monthFilled.visibility = View.GONE
            monthEmpty.visibility = View.VISIBLE
        } else {
            monthFilled.visibility = View.VISIBLE
            monthEmpty.visibility = View.GONE
            monthTotal.text = summary.totalLabel
            monthBar.setSegments(
                summary.shares.map { SegmentedBarView.Segment(it.amount.toFloat(), it.petColor.main(context)) },
                animate = false,
            )
            bindLegend(summary.shares)
        }
    }

    /** "Max £88.20" at the left, "Luna £54.30" at the right, spread across the card. */
    private fun bindLegend(shares: List<MonthShare>) {
        while (monthLegend.childCount > shares.size) {
            monthLegend.removeViewAt(monthLegend.childCount - 1)
        }
        while (monthLegend.childCount < shares.size) {
            val entry = TextView(context)
            entry.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            TextViewCompat.setTextAppearance(entry, R.style.TextAppearance_PC_Meta)
            monthLegend.addView(entry)
        }
        shares.forEachIndexed { index, share ->
            val entry = monthLegend.getChildAt(index) as TextView
            entry.text = "${share.petName} ${share.amountLabel}"
            entry.gravity = when (index) {
                0 -> Gravity.START
                shares.lastIndex -> Gravity.END
                else -> Gravity.CENTER_HORIZONTAL
            }
        }
    }
}
