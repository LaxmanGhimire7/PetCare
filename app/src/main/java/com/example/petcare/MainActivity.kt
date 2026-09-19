package com.example.petcare

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.example.petcare.ui.MotionPrefs
import com.example.petcare.data.local.AuthPreferences
import com.example.petcare.ui.BrandFonts
import com.example.petcare.ui.MotionTransitions
import com.example.petcare.ui.home.HomeDashboardFragment
import com.example.petcare.ui.pets.PetListFragment
import com.example.petcare.ui.expenses.ExpenseListFragment
import com.example.petcare.ui.providers.ProviderListFragment
import com.example.petcare.ui.settings.SettingsFragment
import com.example.petcare.ui.pets.AddPetFragment
import com.example.petcare.ui.pets.EditPetFragment
import com.example.petcare.ui.care.AddCareTaskFragment
import com.example.petcare.ui.care.EditCareTaskFragment
import com.example.petcare.ui.expenses.AddExpenseFragment
import com.example.petcare.ui.providers.AddProviderFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigationrail.NavigationRailView
import java.util.ArrayDeque

/** Hosts the navigation graph and applies system/keyboard insets to its content. */
class MainActivity : AppCompatActivity() {
    private var pendingImport: Bundle? = null
    private val pendingImportQueue = ArrayDeque<Bundle>()
    private var pendingDeepLinkUri: Uri? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        pendingDeepLinkUri = savedInstanceState?.getString(STATE_DEEP_LINK)?.let(Uri::parse)
        if (intent.action == Intent.ACTION_VIEW && intent.data?.scheme == DEEP_LINK_SCHEME &&
            !AuthPreferences(this).isSignedIn()) {
            // Navigation would otherwise display private pet data before the login route runs.
            pendingDeepLinkUri = intent.data
            setIntent(Intent(this, MainActivity::class.java).apply { action = Intent.ACTION_MAIN })
        }
        val brandFonts = BrandFonts(this)
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentPreCreated(
                    fm: FragmentManager,
                    fragment: Fragment,
                    savedInstanceState: Bundle?
                ) {
                    when (fragment) {
                        is HomeDashboardFragment,
                        is PetListFragment,
                        is ExpenseListFragment,
                        is ProviderListFragment,
                        is SettingsFragment -> MotionTransitions.sibling(fragment)
                        is AddPetFragment,
                        is EditPetFragment,
                        is AddCareTaskFragment,
                        is EditCareTaskFragment,
                        is AddExpenseFragment,
                        is AddProviderFragment -> MotionTransitions.forward(fragment)
                    }
                }
                override fun onFragmentViewCreated(
                    fm: FragmentManager,
                    fragment: Fragment,
                    view: android.view.View,
                    savedInstanceState: Bundle?
                ) {
                    brandFonts.applyTo(view)
                }
            },
            true
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        val navController = (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment)
            .navController
        val primaryDestinations = setOf(
            R.id.homeDashboardFragment,
            R.id.petListFragment,
            R.id.expenseListFragment,
            R.id.providerListFragment,
            R.id.settingsFragment
        )
        if (savedInstanceState != null) {
            pendingImport = savedInstanceState.getBundle(STATE_IMPORT)
            @Suppress("DEPRECATION")
            pendingImportQueue.addAll(savedInstanceState.getParcelableArrayList<Bundle>(STATE_IMPORT_QUEUE).orEmpty())
        } else {
            val incoming = importArgsList(intent)
            pendingImport = incoming.firstOrNull()
            pendingImportQueue.addAll(incoming.drop(1))
        }
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id in primaryDestinations) {
                openNextImport(navController)
                pendingDeepLinkUri?.let { uri ->
                    pendingDeepLinkUri = null
                    navController.handleDeepLink(Intent(Intent.ACTION_VIEW, uri))
                }
            }
        }
        if (navController.currentDestination?.id in primaryDestinations) {
            openNextImport(navController)
        }
        findViewById<BottomNavigationView>(R.id.bottom_navigation)?.let { bottomBar ->
            NavigationUI.setupWithNavController(bottomBar, navController)
            navController.addOnDestinationChangedListener { _, destination, _ ->
                bottomBar.visibility =
                    if (destination.id in primaryDestinations) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
        findViewById<NavigationRailView>(R.id.navigation_rail)?.let { rail ->
            NavigationUI.setupWithNavController(rail, navController)
            navController.addOnDestinationChangedListener { _, destination, _ ->
                rail.visibility =
                    if (destination.id in primaryDestinations) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
        brandFonts.load()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                maxOf(systemBars.bottom, keyboard.bottom)
            )
            insets
        }
        splash.setOnExitAnimationListener { provider ->
            if (!MotionPrefs.animationsEnabled(this)) {
                provider.remove()
            } else {
                provider.iconView.animate()
                    .scaleX(1.12f).scaleY(1.12f).alpha(0f)
                    .setDuration(300L)
                    .setInterpolator(OvershootInterpolator(0.7f))
                    .withEndAction { provider.remove() }
                    .start()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val incoming = importArgsList(intent)
        pendingImport = incoming.firstOrNull()
        pendingImportQueue.clear()
        pendingImportQueue.addAll(incoming.drop(1))
        val controller = (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment)
            .navController
        if (controller.currentDestination?.id in setOf(R.id.homeDashboardFragment, R.id.petListFragment,
                R.id.expenseListFragment, R.id.providerListFragment, R.id.settingsFragment)) {
            openNextImport(controller)
        }
        if (intent.action == Intent.ACTION_VIEW && intent.data?.scheme == DEEP_LINK_SCHEME) {
            if (AuthPreferences(this).isSignedIn()) controller.handleDeepLink(intent)
            else pendingDeepLinkUri = intent.data
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBundle(STATE_IMPORT, pendingImport)
        outState.putParcelableArrayList(STATE_IMPORT_QUEUE, ArrayList(pendingImportQueue))
        outState.putString(STATE_DEEP_LINK, pendingDeepLinkUri?.toString())
        super.onSaveInstanceState(outState)
    }

    private fun openNextImport(controller: androidx.navigation.NavController) {
        val next = pendingImport ?: if (pendingImportQueue.isEmpty()) null
            else pendingImportQueue.removeFirst()
        pendingImport = null
        if (next != null) controller.navigate(R.id.addCareTaskFragment, next)
    }

    private fun importArgsList(source: Intent): List<Bundle> {
        if (source.action != ACTION_REVIEWED_IMPORT) return emptyList()
        @Suppress("DEPRECATION")
        val batch = source.getParcelableArrayListExtra<Bundle>(EXTRA_IMPORT_BATCH)
        if (!batch.isNullOrEmpty()) return batch
        return listOf(Bundle().apply {
            putString("importedTitle", source.getStringExtra(EXTRA_IMPORT_TITLE).orEmpty())
            putLong("importedDateEpochDay", source.getLongExtra(EXTRA_IMPORT_DATE, 0L))
            putInt("importedTimeMinutes", source.getIntExtra(EXTRA_IMPORT_TIME, -1))
            putString("importedNotes", source.getStringExtra(EXTRA_IMPORT_NOTES).orEmpty())
            putString("importedClinic", source.getStringExtra(EXTRA_IMPORT_CLINIC).orEmpty())
        })
    }

    companion object {
        const val ACTION_REVIEWED_IMPORT = "com.example.petcare.action.REVIEWED_IMPORT"
        const val EXTRA_IMPORT_TITLE = "com.example.petcare.extra.IMPORT_TITLE"
        const val EXTRA_IMPORT_DATE = "com.example.petcare.extra.IMPORT_DATE"
        const val EXTRA_IMPORT_TIME = "com.example.petcare.extra.IMPORT_TIME"
        const val EXTRA_IMPORT_NOTES = "com.example.petcare.extra.IMPORT_NOTES"
        const val EXTRA_IMPORT_CLINIC = "com.example.petcare.extra.IMPORT_CLINIC"
        const val EXTRA_IMPORT_BATCH = "com.example.petcare.extra.IMPORT_BATCH"
        private const val STATE_IMPORT = "pending_import"
        private const val STATE_IMPORT_QUEUE = "pending_import_queue"
        private const val STATE_DEEP_LINK = "pending_deep_link"
        private const val DEEP_LINK_SCHEME = "petcare"
    }
}
