package com.dododial.phone.call_related

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.dododial.phone.DialerActivity
import com.dododial.phone.R
import com.dododial.phone.databinding.ActivityInCallBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import timber.log.Timber

class InCallActivity : AppCompatActivity() {

    private val CHANNEL_ID = "alxkng5737"
    private val NOTIFICATION_ID = 12345678

    private lateinit var binding: ActivityInCallBinding
    private val inCallViewModel: InCallViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityInCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        /**
         * Updates call duration for display.
         */
        binding.lifecycleOwner = this
        binding.viewModel = inCallViewModel

        // Lets IncomingCallActivity know that InCallActivity is already running.
        running = true

        inCallOverLockScreen()

        /**
         * Observes and updates UI based off current focusedConnection
         */
        CallManager.focusedConnection.observe(this) { connection ->
            if (connection == null) {
                finish()
                Timber.i("DODODEBUG: IN CALL FINISHED!")
            } else {
                updateScreen()
            }
        }

        prepareAudio()

        binding.hangupActive.setOnClickListener {
            CallManager.hangup()
        }

        binding.addActive.setOnClickListener {
            DialerActivity.start(this)
        }

        binding.swapActive.setOnClickListener {
            CallManager.swap()
        }

        binding.mergeActive.setOnClickListener {
            CallManager.merge()
        }

        binding.speakerActive.setOnClickListener {
            AudioHelpers.toggleSpeaker(this)
        }

        binding.muteActive.setOnClickListener {
            AudioHelpers.toggleMute(this)
        }

        binding.keypadActive.setOnClickListener {
            Timber.i("DODODEBUG: Keypad button pressed!")
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Lets IncomingCallActivity know that InCallActivity is already running.
        running = false
    }

    fun inCallOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            )
        }
    }

    private fun prepareAudio() {
        /**
         * Reset speaker and mute states.
         */
        AudioHelpers.setSpeaker(this, false)
        AudioHelpers.setMute(this, false)

        /**
         * Set mute UI based on mute state.
         */
        AudioHelpers.muteStatus.observe(this) { mute ->
            val color = if (mute) {
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.clicked_blue))
            } else {
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.icon_white))
            }

            binding.muteActive.iconTint = color
        }

        /**
         * Set speaker UI based on speaker state.
         */
        AudioHelpers.speakerStatus.observe(this) { speaker ->
            val color = if (speaker) {
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.clicked_blue))
            } else {
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.icon_white))
            }

            binding.speakerActive.iconTint = color
        }
    }

    private fun updateScreen() {

        /**
         * Can only add calls if there is exactly one connection.
         */
        val addClickable = CallManager.isStableState() && CallManager.connections.size == 1
        binding.addActive.isClickable = addClickable

        if (addClickable) {
            binding.addActive.iconTint = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.icon_white))
            binding.addText.setTextColor(ContextCompat.getColor(this, R.color.icon_white))
        } else {
            binding.addActive.iconTint = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.disabled_grey))
            binding.addText.setTextColor(ContextCompat.getColor(this, R.color.disabled_grey))
        }

        /**
         * Can only swap calls if there are exactly two connections (holding, active).
         */
        val swapClickable = CallManager.isActiveStableState() && CallManager.connections.size == 2
        binding.swapActive.isClickable = swapClickable

        if (swapClickable) {
            binding.swapActive.iconTint = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.icon_white))
            binding.swapText.setTextColor(ContextCompat.getColor(this, R.color.icon_white))
        } else {
            binding.swapActive.iconTint = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.disabled_grey))
            binding.swapText.setTextColor(ContextCompat.getColor(this, R.color.disabled_grey))
        }

        /**
         * Can only merge calls if there are exactly two connections (holding, active) and the
         * call is conferenceable.
         */
        val mergeClickable = CallManager.isActiveStableState() && CallManager.canMerge()
        binding.mergeActive.isClickable = mergeClickable

        if (mergeClickable) {
            binding.mergeActive.iconTint = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.icon_white))
            binding.mergeText.setTextColor(ContextCompat.getColor(this, R.color.icon_white))
        } else {
            binding.mergeActive.iconTint = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.disabled_grey))
            binding.mergeText.setTextColor(ContextCompat.getColor(this, R.color.disabled_grey))
        }

        /**
         * Display only number if contact doesn't exist. Otherwise display both number and contact.
         */
        val temp: String? = CallManager.focusedCall.number()
        val number = if (!temp.isNullOrEmpty()) {
            temp
        } else {
            if (temp == null) "Conference" else "Unknown"
        }

        val contactExists = false
        if (contactExists) {

        } else {
            binding.smallNumber.visibility = View.GONE
            binding.numberOrContact.text = number
        }

    }

    companion object {
        var running = false

        fun start(context: Context) {
            Intent(context, InCallActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .let(context::startActivity)
        }
    }
}
