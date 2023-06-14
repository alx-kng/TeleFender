package com.telefender.phone.gui

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import com.telefender.phone.App
import com.telefender.phone.R
import com.telefender.phone.databinding.ActivityMainBinding
import com.telefender.phone.gui.model.*
import com.telefender.phone.misc_helpers.DBL
import com.telefender.phone.misc_helpers.TeleHelpers
import com.telefender.phone.permissions.PermissionRequestType
import com.telefender.phone.permissions.Permissions
import kotlinx.coroutines.*
import timber.log.Timber


/**
 *
 * TODO: YOU LISTEN TO ME!!!! THERE IS A LEAK IN THE CONTACTS OBSERVER!!!
 *  FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF
 *
 * TODO make sure the app opens up to last used fragment.
 *
 * TODO: Probably need to request notifications permission in case default dialer is rejected.
 *  That way, Firebase push notifications still work.
 *
 * TODO opening and reopening app clearly has some bugs that causes splash screen to not show on first
 *  open and causes reentering the app through Recents (android) screen to look glitchy. Actually,
 *  now it doesn't seem to happen anymore. Maybe the OS had to finish compiling some extra stuff for
 *  the app. Keep an eye on this.
 *
 * TODO: IMPORTANT!!! Make known the dangers of pressing "Don't ask again" on the default dialer
 *  dialog to the user.
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

    private var callLogObserverUI: CallLogObserverUI? = null
    private var contactObserver: ContactsObserver? = null

    /**
     * TODO: Should request regular call log / phone permissions if user denies default dialer
     *  permissions (to stop app from crashing in recents and contacts). Find better user flow.
     *   -> basic coreAltPermissions() called, but we probably need to do more.
     *   -> Maybe we shouldn't immediately request all and only request permissions on screens
     *   that need it (e.g., if user tries to enter contacts screen, then first request contact
     *   permissions).
     *
     * Result for default dialer request made in [requestDefaultDialer]. If the default dialer is
     * accepted, we then ask for phone state permissions (read comment in code below).
     */
    private val startForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult())
    { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            /*
            Due to a bug that prevents the default dialer from receiving the READ_PHONE_NUMBERS
            permission (SDK > 29 = Android 10) automatically, we need to request the permission
            separately. Requesting the permission separately should grant the permission to the
            default dialer without bringing up a dialog to the user.
             */
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                Permissions.phoneStatePermissions(this)
            } else {
                Permissions.doNotDisturbPermission(this)
            }
        } else {
            Permissions.coreAltPermissions(this)
        }
    }

    /**
     * TODO: Fully make use of onRequestPermissionsResult()
     * TODO: Put the Do Not Disturb permission request somewhere else to make the user flow
     *  smoother.
     *
     * Basically, when any permission request's result is returned, onRequestPermissionsResult()
     * is called.
     *
     * @param [requestCode] is the requestCode you passed into the permission request and
     * can be used to differentiate between different permission requests.
     *
     * @param [permissions] is the array of permissions you requested
     *
     * @param [grantResults] is an array of same size of [permissions], where each index either
     * stores a [PackageManager.PERMISSION_GRANTED] or [PackageManager.PERMISSION_DENIED] to
     * indicate whether the corresponding permission by index in [permissions] was granted.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            PermissionRequestType.CORE_ALT.requestCode -> {
                Timber.i("$DBL: CORE_ALT Permissions result!")

                // Makes sure all permissions in CORE_ALT were granted.
                for (result in grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) return
                }

                Permissions.doNotDisturbPermission(this)
            }
            PermissionRequestType.PHONE_STATE.requestCode -> {
                Timber.i("$DBL: PHONE_STATE Permissions result!")

                if (grantResults.first() == PackageManager.PERMISSION_GRANTED) {
                    Permissions.doNotDisturbPermission(this)
                }
            }
            PermissionRequestType.NOTIFICATIONS.requestCode -> {
                Timber.i("$DBL: NOTIFICATIONS Permissions result!")
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

        requestDefaultDialer()

        notificationChannelCreator()

        setupObservers()

        setupBottomNavigation()

        setupBackPress()
    }

    /**
     * Sometimes, the bottom navigation highlights the incorrect item when reentering the app.
     * This ensures that the bottom navigation highlights the correct item before the activity
     * shows.
     */
    override fun onStart() {
        super.onStart()

        updateBottomHighlight()

        initializeDatabase()

        /**
         * Registers UI Call log observer if not registered before (in case permissions weren't
         * granted before).
         */
        setupObservers(onCreate = false)
    }

    override fun onDestroy() {
        unregisterObservers()

        /*
        Seems like it needs to be after other code so that MainActivity isn't destroyed too early.
        IncomingActivity's onDestroy() doesn't seem to have this issue, but it could be because
        when MainActivity closes, the entire app is closed??
         */
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.top_app_bar, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.more -> {
                Timber.i("$DBL: MainActivity: More menu button pressed!")
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    /**
     * TODO: We request default dialer if the app doesn't have phone state permissions, even if
     *  Permissions supposedly say that app is default dialer for Android 8 and 9 (<Q=10), since,
     *  at least on one instance of Android 9, on reinstalling the app, the OS "says" that the app
     *  is the default dialer, but the permissions that come along with it are not given.
     *  -
     *  Unfortunately, even though we request the default dialer, the default dialer prompt won't
     *  actually show, as the OS thinks we're already the default dialer, but at least
     *  coreAltPermissions() will be called.
     *  -
     *  Note that this issue doesn't occur on the very first installation of the app.
     *  -
     *  See if we can find a better solution.
     *
     * TODO Make dialog to explain reason for Do not disturb access.
     *
     * Offers to replace default dialer, automatically makes requesting permissions
     * separately unnecessary.
     */
    private fun requestDefaultDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (!Permissions.isDefaultDialer(this)) {
                val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                    .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)

                startForResult.launch(intent)
            }
        } else {
            if (!Permissions.isDefaultDialer(this)
                || !Permissions.hasPhoneStatePermissions(this)
            ) {
                val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                    .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)

                startForResult.launch(intent)
            }
        }
    }

    private fun notificationChannelCreator() {
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

    /**
     * TODO: USE PAGING
     *
     * Sets up the call log and contact observers, which drive the UI updates for RecentsFragment
     * and ContactsFragment. Also, we make the fragments more smooth by preloading call logs and
     * contacts in the MainActivity. If this is still too slow, you can consider querying only a
     * portion of the call logs.
     */
    private fun setupObservers(onCreate: Boolean = true) {
        if (Permissions.hasPermissions(this, arrayOf(
                android.Manifest.permission.READ_CALL_LOG,
                android.Manifest.permission.READ_CONTACTS))
        ) {
            /*
             *Dummy methods are called so that the ViewModels are initialized. Preloading of call
             *logs and contacts occurs within the init{} blocks of the ViewModels.
             */
            recentsViewModel.activateDummy()
            contactsViewModel.activateDummy()

            // Registers UI Call log observer (defined and unregistered in MainActivity)
            if (onCreate || callLogObserverUI == null) {
                callLogObserverUI = CallLogObserverUI(Handler(Looper.getMainLooper()), recentsViewModel)
                this.applicationContext.contentResolver.registerContentObserver(
                    android.provider.CallLog.Calls.CONTENT_URI,
                    false,
                    callLogObserverUI!!
                )
            }

            // Registers Contacts observer (defined and unregistered in MainActivity)
            if (onCreate || contactObserver == null) {
                contactObserver = ContactsObserver(Handler(Looper.getMainLooper()), contactsViewModel)
                this.applicationContext.contentResolver.registerContentObserver(
                    ContactsContract.Contacts.CONTENT_URI,
                    true,
                    contactObserver!!
                )
            }
        }
    }

    /**
     * We use this instead of setUpWithNavController() because this allows us to use global
     * actions to each of the core fragments (e.g., Recents, Contacts, Dialer). As a result,
     * touching the bottom navigation buttons always navigates to corresponding screen. The
     * popBackStack() makes sure that there is only one core fragment in the back stack.
     */
    private fun setupBottomNavigation() {
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
     * TODO: Back button breaks.
     *
     * Needed to override onBackPressed() so that we could change the bottom navigation
     * selected item to the shown fragment. Otherwise, when you press back button, the
     * fragment shown changes, but the bottom selected item doesn't.
     *
     * NOTE: The isEnabled pattern prevents onBackPressed() from invoking the current callback,
     * which causes an infinite loop (more in Android - General Notes).
     */
    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Timber.i("$DBL: Back pressed in MainActivity!")

                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                updateBottomHighlight()
                isEnabled= true
            }
        })
    }

    /**
     * Repository / database needs to call a query first in order to initialize database,
     * in which the ClientDatabase getDatabase is called
     */
    private fun initializeDatabase() {
        val app = (application as App)

        app.applicationScope.launch {
            app.repository.dummyQuery()
        }
    }

    /**
     * Unregisters call log and contact observers for UI. Might be null depending on whether
     * permissions were granted.
     */
    private fun unregisterObservers() {
        callLogObserverUI?.let {
            this.applicationContext.contentResolver.unregisterContentObserver(it)
        }

        contactObserver?.let {
            this.applicationContext.contentResolver.unregisterContentObserver(it)
        }
    }

    private fun updateBottomHighlight() {
        when (navController.currentDestination!!.id) {
            R.id.recentsFragment -> binding.bottomNavigation.selectedItemId = R.id.recentsFragment
            R.id.contactsFragment -> binding.bottomNavigation.selectedItemId = R.id.contactsFragment
            R.id.dialerFragment -> binding.bottomNavigation.selectedItemId = R.id.dialerFragment
        }
    }

    fun displayAppBarTextButton(show: Boolean, text: String) {
        if (show) {
            binding.appBarTextButton.visibility = View.VISIBLE
        } else {
            binding.appBarTextButton.visibility = View.GONE
            return
        }
        binding.appBarTextButton.text = text
    }

    fun setEditOrAddOnClickListener(onClickListener: (View)->Unit) {
        binding.appBarTextButton.setOnClickListener(onClickListener)
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

    /**
     * Basically reverts the app bar to the original UI for each base fragment (e.g., Recents).
     * Be very careful if you call this in a child fragment (e.g., EditContact) to revert the app
     * bar. Specifically, if you call this after super.onDestroyView(), revertAppBar() might be
     * called AFTER the setupAppBar() of the previous fragment, which will cause the app bar to
     * not contain the expected views.
     *
     * NOTE: It's not necessary to use this function in every fragment.
     */
    fun revertAppbar() {
        displayUpButton(false)
        displayMoreMenu(true)
        displayAppBarTextButton(show = false, text = "")
        setEditOrAddOnClickListener {  } // Resets onClick listener to do nothing.
    }

    /**
     * TODO: See why observer is called multiple times after a single call ends. Doesn't seem to
     *  affect the UI at the moment but we should look out for it. I think the reason is that since
     *  we are observing Calls.CONTENT_URI (might not have any other option), every change to
     *  Default call logs (like number, direction, location, etc.) notifies the observer.
     *
     * TODO: Observer isn't being unregistered correctly!!!!!
     *
     * TODO: Remove id from CallLogObserverUI. Currently used to check if observer is correctly
     *  unregistered or not.
     *
     * This CallLogObserver observes call logs purely for UI changes in RecentsFragments. The
     * observer is registered and unregistered along with MainActivity's lifecycle.
     */
    class CallLogObserverUI(
        handler: Handler,
        private val recentsViewModel: RecentsViewModel
    ) : ContentObserver(handler) {

        private var id = (0..10000).random()

        override fun deliverSelfNotifications(): Boolean {
            return false
        }

        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            Timber.i("$DBL: OBSERVED NEW CALL LOG - UI - id = $id")

            recentsViewModel.updateCallLogs()
        }
    }

    /**
     * Observes changes to default contacts and updates the UI accordingly.
     */
    class ContactsObserver(
        handler: Handler,
        private val contactsViewModel: ContactsViewModel
    ) : ContentObserver(handler) {

        override fun deliverSelfNotifications(): Boolean {
            return true
        }

        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            Timber.i("$DBL: ContactsFragment - New Contact!")

            contactsViewModel.updateContacts()
        }
    }
    
    companion object {
        fun start(context: Context) {
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .let(context::startActivity)
        }
    }
}
