package com.telefender.phone.gui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import com.telefender.phone.App
import com.telefender.phone.R
import com.telefender.phone.data.tele_database.ClientRepository
import com.telefender.phone.databinding.ActivityMainBinding
import com.telefender.phone.gui.model.*
import com.telefender.phone.helpers.MiscHelpers
import com.telefender.phone.permissions.PermissionRequester
import kotlinx.coroutines.launch
import timber.log.Timber

//

/**
 * TODO make sure the app opens up to last used fragment.
 *
 * TODO opening and reopening app clearly has some bugs that causes splash screen to not show on first
 *  open and causes reentering the app through Recents (android) screen to look glitchy. Actually,
 *  now it doesn't seem to happen anymore. Maybe the OS had to finish compiling some extra stuff for
 *  the app. Keep an eye on this.
 */
class MainActivity : AppCompatActivity() {

    private val CHANNEL_ID = "alxkng5737"

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navController: NavController
    private lateinit var binding: ActivityMainBinding

    private val recentsViewModel: RecentsViewModel by viewModels {
        RecentsViewModelFactory(this.application)
    }

    private val contactsViewModel : ContactsViewModel by viewModels {
        ContactsViewModelFactory(this.application)
    }

    private val dialerViewModel : DialerViewModel by viewModels()

    /**
     * TODO: Should request regular call log / phone permissions if user denies default dialer
     *  permissions (to stop app from crashing in recents and contacts). Find better user flow.
     *
     * Used to request default dialer permissions. If the default dialer is accepted, it then
     * asks for Do Not Disturb permissions to allow app to silence calls.
     */
    private val startForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult())
    { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            val notificationManager = applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (!notificationManager.isNotificationPolicyAccessGranted) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                startActivityForResult(intent, 120)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        setSupportActionBar(binding.topAppBar)
        displayMoreMenu(false)

        /**
         * TODO Make dialog to explain reason for Do not disturb access.
         *
         * Offers to replace default dialer, automatically makes requesting permissions
         * separately unnecessary
         */
        offerReplacingDefaultDialer()

        notificationChannelCreator()

        /**
         * Makes RecentsFragment more smooth on first enter by preloading call logs in MainActivity.
         * If this is still too slow, you can consider querying only a portion of the call logs.
         */
        if (PermissionRequester.hasPermissions(this, arrayOf(
                android.Manifest.permission.READ_CALL_LOG,
                android.Manifest.permission.READ_CONTACTS))
        ) {
            /*
             *Dummy methods are called so that the ViewModels are initialized. Preloading of call
             *logs and contacts occurs within the init{} blocks of the ViewModels.
             */
            recentsViewModel.activateDummy()
            contactsViewModel.activateDummy()
        }

        /**
         * We use this instead of setUpWithNavController() because this allows us to use global
         * actions to each of the core fragments (e.g., Recents, Contacts, Dialer). As a result,
         * touching the bottom navigation buttons always navigates to corresponding screen. The
         * popBackStack() makes sure that there is only one core fragment in the back stack.
         */
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.recentsFragment -> {
                    navController.popBackStack()
                    navController.navigate(R.id.action_global_recentsFragment)
                }
                R.id.contactsFragment -> {
                    navController.popBackStack()
                    navController.navigate(R.id.action_global_contactsFragment)
                }
                R.id.dialerFragment -> {
                    navController.popBackStack()
                    navController.navigate(R.id.action_global_dialerFragment)
                }
            }
            true
        }
        binding.bottomNavigation.selectedItemId = R.id.dialerFragment
    }

    /**
     * Sometimes, the bottom navigation highlights the incorrect item when reentering the app.
     * This ensures that the bottom navigation highlights the correct item before the activity
     * shows.
     */
    override fun onStart() {
        super.onStart()
        updateBottomHighlight()

        /**
         * Repository / database needs to call a query first in order to initialize database,
         * in which the ClientDatabase getDatabase is called
         */
        val repository: ClientRepository = (application as App).repository

        val job = (application as App).applicationScope.launch {
            repository.dummyQuery()
        }
    }

    /**
     * Needed to override onBackPressed() so that we could change the bottom
     * navigation selected item to the shown fragment. Otherwise, when you press back button, the
     * fragment shown changes, but the bottom selected item doesn't.
     */
    override fun onBackPressed() {
        super.onBackPressed()
        updateBottomHighlight()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.top_app_bar, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.more -> {
                Log.i("MainActivity","More menu button pressed!")
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    private fun offerReplacingDefaultDialer() {
        if (getSystemService(TelecomManager::class.java).defaultDialerPackage != packageName) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager : RoleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
                val intent : Intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                    .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                startForResult.launch(intent)
            } else {
                val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                    .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                startForResult.launch(intent)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun makeCallNoParam() {
        try {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val number = dialerViewModel.dialNumber.value
            val uri = "tel:${number}".toUri()
            telecomManager.placeCall(uri, null)
            Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: OUTGOING CALL TO $number")
        } catch (e: Exception) {
            Timber.e("${MiscHelpers.DEBUG_LOG_TAG}: OUTGOING CALL FAILED!")
        }
    }

    @SuppressLint("MissingPermission")
    fun makeCallParam(number: String) {
        try {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val uri = "tel:${number}".toUri()
            telecomManager.placeCall(uri, null)
            Timber.i("${MiscHelpers.DEBUG_LOG_TAG}: OUTGOING CALL TO $number")
        } catch (e: Exception) {
            Timber.e("${MiscHelpers.DEBUG_LOG_TAG}: OUTGOING CALL FAILED!")
        }
    }

    private fun updateBottomHighlight() {
        when (navController.currentDestination!!.id) {
            R.id.recentsFragment -> binding.bottomNavigation.selectedItemId = R.id.recentsFragment
            R.id.contactsFragment -> binding.bottomNavigation.selectedItemId = R.id.contactsFragment
            R.id.dialerFragment -> binding.bottomNavigation.selectedItemId = R.id.dialerFragment
        }
    }

    fun displayEditOrAdd(show: Boolean, isContact: Boolean) {
        if (show) {
            binding.editOrAdd.visibility = View.VISIBLE
        } else {
            binding.editOrAdd.visibility = View.GONE
            return
        }
        binding.editOrAdd.text = if (isContact) "Edit" else "Add"
    }

    fun displayMoreMenu(show: Boolean) {
        binding.topAppBar.menu.findItem(R.id.more).isVisible = show
    }

    fun displayUpButton(show: Boolean) {
        supportActionBar?.setDisplayHomeAsUpEnabled(show)
    }

    fun setTitle(appBarTitle: String) {
        binding.topAppBar.title = appBarTitle
    }

    fun displayAppBar(show: Boolean) {
        if (show) {
            if (binding.topAppBar.visibility != View.VISIBLE) {
                binding.topAppBar.visibility = View.VISIBLE
            }
        } else {
            if (binding.topAppBar.visibility != View.GONE) {
                binding.topAppBar.visibility = View.GONE
            }
        }
    }

    private fun notificationChannelCreator() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val mChannel = NotificationChannel(CHANNEL_ID, name, importance)
            mChannel.description = descriptionText
            mChannel.setSound(null, null)
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(mChannel)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    companion object {
        fun start(context: Context) {
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .let(context::startActivity)
        }
    }
}
