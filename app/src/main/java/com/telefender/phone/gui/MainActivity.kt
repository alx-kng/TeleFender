package com.telefender.phone.gui

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
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
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import com.telefender.phone.App
import com.telefender.phone.R
import com.telefender.phone.databinding.ActivityMainBinding
import com.telefender.phone.gui.fragments.dialogs.PrivacyDialogFragment
import com.telefender.phone.gui.fragments.dialogs.SettingsDialogFragment
import com.telefender.phone.gui.model.*
import com.telefender.phone.misc_helpers.*
import com.telefender.phone.permissions.PermissionRequestType
import com.telefender.phone.permissions.Permissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    private val verificationViewModel : VerificationViewModel by viewModels {
        VerificationViewModelFactory(this.application)
    }

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

        Permissions.notificationPermission(this, this)
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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)

        handleTeleDeepLinkIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        setStartDestination()

        setSupportActionBar(binding.topAppBarMain)

        if (SharedPreferenceHelpers.getUserReady(this)) {
            userReadyOnCreateSetup()
        }

        dialerViewModel.setFromInCall(fromInCall = false)
    }

    /**
     * Sometimes, the bottom navigation highlights the incorrect item when reentering the app.
     * This ensures that the bottom navigation highlights the correct item before the activity
     * shows.
     */
    override fun onStart() {
        super.onStart()

        if (SharedPreferenceHelpers.getUserReady(this)) {
            userReadyOnStartSetup()
        }
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
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        when(navController.currentDestination?.id) {
            R.id.viewContactFragment -> {
                /*
                Clear data lists when coming back from ViewContactFragment (prevents
                blinking update when pressing on another contact).
                 */
                contactsViewModel.clearDataLists()
            }
            R.id.callHistoryFragment -> {
                /*
                Clear CallHistory data lists when coming back from CallHistoryFragment (prevents
                blinking update when pressing on another call log).
                 */
                recentsViewModel.clearCallHistoryLists()
                contactsViewModel.clearDataLists()
            }


        }

        val navigationHandled = navController.navigateUp() || super.onSupportNavigateUp()
        updateBottomHighlight()
        return navigationHandled
    }

    /**
     * Makes the activity start in the right fragment depending on whether the beginning user setup
     * (non-server setup) has already been done.
     */
    private fun setStartDestination() {
        // Manually inflate the NavGraph and set the startDestination
        val navGraph = navController.navInflater.inflate(R.navigation.nav_graph)
        navGraph.setStartDestination(
            if (SharedPreferenceHelpers.getUserReady(this))
                R.id.dialerFragment
            else
                R.id.initialFragment
        )
        navController.graph = navGraph
    }

    fun userReadyOnCreateSetup() {
        // TODO: why was this here?
//        displayMoreMenu(false)

        requestDefaultDialer()

        notificationChannelCreator()

        setupObservers()

        setupBottomNavigation()

        setupBackPress()

        handleTeleDeepLinkIntent(intent)
    }

    fun userReadyOnStartSetup() {
        updateBottomHighlight()

        initializeDatabase()

        /**
         * Registers UI Call log observer if not registered before (in case permissions weren't
         * granted before).
         */
        setupObservers(onCreate = false)
    }

    /**
     * TODO: Should we allow navigation from other Activities (e.g., InCallActivity /
     *  IncomingActivity)? -> Not super sure what we're doing currently.
     */
    private fun handleTeleDeepLinkIntent(intent: Intent?) {
        val data: Uri? = intent?.data

        Timber.e("$DBL: Data = $data")

        CoroutineScope(Dispatchers.Default).launch {
            // Extract the phone number from the deep link
            if (TeleHelpers.hasValidStatus(
                    context = this@MainActivity,
                    initializedRequired = true,
                    setupRequired = false,
                    phoneStateRequired = true
                )
                && isTeleDeepLinkIntent(intent)
                && data != null
                && "tel" == data.scheme
            ) {
                val currentDestination = navController.currentDestination?.id
                if (currentDestination == R.id.dialerFragment) {
                    val phoneNumber = data.schemeSpecificPart
                    dialerViewModel.setDialNumber(phoneNumber)

                } else if (currentDestination != R.id.dialerFragment && currentDestination != null) {
                    val phoneNumber = data.schemeSpecificPart
                    dialerViewModel.setDialNumber(phoneNumber)
                    navController.popBackStack()
                    navController.navigate(R.id.dialerFragment)
                }
            }
        }
    }

    private fun isTeleDeepLinkIntent(intent: Intent?) : Boolean {
        return intent != null
            && Intent.ACTION_VIEW == intent.action
            && intent.data != null
            && "tel" == intent.data!!.scheme
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

    /**
     * TODO: Get rid of this
     */
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
                callLogObserverUI = CallLogObserverUI(
                    handler = Handler(Looper.getMainLooper()),
                    recentsViewModel = recentsViewModel
                )

                this.applicationContext.contentResolver.registerContentObserver(
                    android.provider.CallLog.Calls.CONTENT_URI,
                    false,
                    callLogObserverUI!!
                )
            }

            // Registers Contacts observer (defined and unregistered in MainActivity)
            if (onCreate || contactObserver == null) {
                contactObserver = ContactsObserver(
                    handler = Handler(Looper.getMainLooper()),
                    contactsViewModel = contactsViewModel,
                    recentsViewModel = recentsViewModel
                )

                this.applicationContext.contentResolver.registerContentObserver(
                    ContactsContract.Contacts.CONTENT_URI,
                    true,
                    contactObserver!!
                )
            }
        }
    }

    /**
     * We use this instead of setUpWithNavController() because this allows us to use global actions
     * to each of the core fragments (e.g., Recents, Contacts, Dialer). As a result, touching the
     * bottom navigation buttons always navigates to corresponding screen. The popBackStack() makes
     * sure that there is only one core fragment in the back stack. We also check if the fragment
     * is already in view before trying to navigate to it again, as we don't want to unnecessarily
     * recreate it (other problems arise as well).
     */
    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.dialerFragment

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.recentsFragment -> {
                    if (navController.currentDestination?.id != R.id.recentsFragment) {
                        navController.popBackStack()
                        navController.navigate(R.id.action_global_recentsFragment)
                    } else {
                        Timber.i("$DBL: setupBottomNavigation() - Already in recents!")
                    }
                }
                R.id.contactsFragment -> {
                    if (navController.currentDestination?.id != R.id.contactsFragment) {
                        navController.popBackStack()
                        navController.navigate(R.id.action_global_contactsFragment)
                    } else {
                        Timber.i("$DBL: setupBottomNavigation() - Already in contacts!")
                    }
                }
                R.id.dialerFragment -> {
                    if (navController.currentDestination?.id != R.id.dialerFragment) {
                        navController.popBackStack()
                        navController.navigate(R.id.action_global_dialerFragment)
                    } else {
                        Timber.i("$DBL: setupBottomNavigation() - Already in dialer!")
                    }
                }
            }
            true
        }
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

                when(navController.currentDestination?.id) {
                    R.id.viewContactFragment -> {
                        /*
                        Clear data lists when coming back from ViewContactFragment (prevents
                        blinking update when pressing on another contact).
                         */
                        contactsViewModel.clearDataLists()
                    }
                    R.id.callHistoryFragment -> {
                        /*
                        Clear CallHistory data lists when coming back from CallHistoryFragment (prevents
                        blinking update when pressing on another call log).
                         */
                        recentsViewModel.clearCallHistoryLists()
                        contactsViewModel.clearDataLists()
                    }
                    R.id.changeContactFragment -> {
                        /*
                        Clear data lists if not going back to ViewContactFragment (happens when
                        cancelling a new contact) and not going back to CallHistoryFragment. This
                        prevents a blinking update when pressing on another contact or re-entering
                        the edit screen (in the case of CallHistoryFragment). Don't clear lists
                        when going back to ViewContactFragment, cause it still needs the lists.
                         */
                        val previousDestination = navController.previousBackStackEntry?.destination?.id
                        if (previousDestination != R.id.viewContactFragment
                            && previousDestination != R.id.callHistoryFragment
                        ) {
                            contactsViewModel.clearDataLists()
                        }
                    }
                }

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

    /**
     * Controls the display of the app bar text buttons.
     *
     * NOTE: TextButton1 should not be shown along with the app bar title, as we changes the
     * start inset specifically for TextButton1.
     */
    fun displayAppBarTextButton(
        show1: Boolean = false,
        show2: Boolean = false,
        text1: String = "",
        text2: String = ""
    ) {
        val pixelInset = this.dpToPx(16)

        if (show1) {
            // Gets rid of the extra space on the left of the button when there is no title.
            binding.topAppBarMain.setContentInsetsRelative(0, pixelInset)
            binding.appBarTextButton1.visibility = View.VISIBLE
            binding.appBarTextButton1.text = text1
        } else {
            binding.topAppBarMain.setContentInsetsRelative(pixelInset, pixelInset)
            binding.appBarTextButton1.visibility = View.GONE
        }

        if (show2) {
            binding.appBarTextButton2.visibility = View.VISIBLE
            binding.appBarTextButton2.text = text2
        } else {
            binding.appBarTextButton2.visibility = View.GONE
        }
    }

    fun setEnabledAppBarTextButton(
        enabled1: Boolean = true,
        enabled2: Boolean = true
    ) {
        binding.appBarTextButton1.isEnabled = enabled1
        binding.appBarTextButton1.setTextColor(
            if (enabled1) {
                ContextCompat.getColor(this, R.color.white)
            } else {
                ContextCompat.getColor(this, R.color.disabled_grey)
            }

        )

        binding.appBarTextButton2.isEnabled = enabled2
        binding.appBarTextButton2.setTextColor(
            if (enabled2) {
                ContextCompat.getColor(this, R.color.white)
            } else {
                ContextCompat.getColor(this, R.color.disabled_grey)
            }

        )
    }

    fun setAppBarTextButtonOnClickListener(
        onClickListener1: (View)->Unit = {},
        onClickListener2: (View)->Unit = {}
    ) {
        binding.appBarTextButton1.setOnClickListener(onClickListener1)
        binding.appBarTextButton2.setOnClickListener(onClickListener2)
    }

    fun setMoreButtonOnClickListener(
        onClickListener: ((View)->Unit)? = null ,
    ) {
        if (onClickListener == null) {
            binding.appBarMoreButton.setOnClickListener {
                SettingsDialogFragment().show(supportFragmentManager, "settingsDialog")
            }
        } else {
            binding.appBarMoreButton.setOnClickListener(onClickListener)
        }
    }


    fun displayMoreMenu(show: Boolean) {
        binding.appBarMoreButton.visibility = if (show) View.VISIBLE else View.GONE
//        binding.topAppBarMain.menu.findItem(R.id.more).isVisible = show

    }

    fun displayUpButton(show: Boolean) {
        supportActionBar?.setDisplayHomeAsUpEnabled(show)
    }

    fun setTitle(appBarTitle: String) {
        binding.topAppBarMain.title = appBarTitle
    }

    fun displayAppBar(show: Boolean) {
        if (show) {
            if (binding.topAppBarMain.visibility != View.VISIBLE) {
                binding.topAppBarMain.visibility = View.VISIBLE
            }
        } else {
            if (binding.topAppBarMain.visibility != View.GONE) {
                binding.topAppBarMain.visibility = View.GONE
            }
        }
    }

    fun displayBottomNavigation(show: Boolean) {
        if (show) {
            if (binding.bottomNavigation.visibility != View.VISIBLE) {
                binding.bottomNavigation.visibility = View.VISIBLE
            }
        } else {
            if (binding.bottomNavigation.visibility != View.GONE) {
                binding.bottomNavigation.visibility = View.GONE
            }
        }
    }

    /**
     * TODO: It's not necessary to use this function in every fragment.
     *
     * Basically reverts the app bar to the original UI for each base fragment (e.g., Recents).
     * Be very careful if you call this in a child fragment (e.g., EditContact) to revert the app
     * bar. Specifically, if you call this after super.onDestroyView(), revertAppBar() might be
     * called AFTER the setupAppBar() of the previous fragment, which will cause the app bar to
     * not contain the expected views.
     *
     * NOTE: For now revertAppBar() should be used before the setupAppBar() in each fragment. This
     * way, we avoid the onDestroyView() timing problem and simply clean the app bar before we use
     * it.
     */
    fun revertAppBar() {
        setTitle("")
        displayUpButton(false)
        displayMoreMenu(true)
        displayAppBarTextButton() // Hides all text buttons
        setEnabledAppBarTextButton() // Re-enables all text buttons
        setAppBarTextButtonOnClickListener() // Resets onClick listener to do nothing.
        setMoreButtonOnClickListener() // Resets onClick listener to bring up settings dialog.
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

            recentsViewModel.onCallLogUpdate()
        }
    }

    /**
     * Observes changes to default contacts and updates the UI accordingly.
     */
    class ContactsObserver(
        handler: Handler,
        private val contactsViewModel: ContactsViewModel,
        private val recentsViewModel: RecentsViewModel
    ) : ContentObserver(handler) {

        override fun deliverSelfNotifications(): Boolean {
            return true
        }

        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            Timber.i("$DBL: ContactsFragment - New Change!")

            contactsViewModel.onContactsUpdate()
            recentsViewModel.onContactsUpdate()
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
