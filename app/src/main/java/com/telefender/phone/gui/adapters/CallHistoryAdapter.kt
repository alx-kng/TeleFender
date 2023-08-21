package com.telefender.phone.gui.adapters

import android.content.Context
import android.content.res.ColorStateList
import android.provider.CallLog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.telefender.phone.R
import com.telefender.phone.data.tele_database.entities.CallDetail
import com.telefender.phone.gui.adapters.recycler_view_items.*
import com.telefender.phone.gui.adapters.recycler_view_items.common_types.SafetyStatus
import com.telefender.phone.misc_helpers.DBL
import com.telefender.phone.misc_helpers.TeleHelpers
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*


class CallHistoryAdapter (
    private val applicationContext: Context,
    private val photoOnClickListener: (CallHistoryItem) -> Unit,
    private val callOnClickListener: (CallHistoryItem) -> Unit,
    private val messageOnClickListener: (CallHistoryItem) -> Unit,
    private val emailOnClickListener: (CallHistoryItem) -> Unit,
    private val blockOnClickListener: (CallHistoryItem) -> Unit,
    private val spamOnClickListener: (CallHistoryItem) -> Unit,
    private val defaultOnClickListener: (CallHistoryItem) -> Unit,
    private val safeOnClickListener: (CallHistoryItem) -> Unit,
) : ListAdapter<CallHistoryItem, RecyclerView.ViewHolder>(CombinedComparator()) {

    val HEADER_VIEW_TYPE = 1
    val SELECT_TIME_VIEW_TYPE = 2
    val BLOCKED_STATUS_VIEW_TYPE = 3
    val SAFETY_STATUS_VIEW_TYPE = 4
    val DATA_VIEW_TYPE = 5
    val FOOTER_VIEW_TYPE = 6

    class CombinedComparator : DiffUtil.ItemCallback<CallHistoryItem>() {

       override fun areItemsTheSame(oldItem: CallHistoryItem, newItem: CallHistoryItem): Boolean {
            return oldItem === newItem
        }

       override fun areContentsTheSame(oldItem: CallHistoryItem, newItem: CallHistoryItem): Boolean {
           return when (oldItem) {
               is CallHistoryData -> newItem is CallHistoryData && oldItem == newItem
               is CallHistoryHeader -> newItem is CallHistoryHeader && oldItem == newItem
               is CallHistorySelectTime -> newItem is CallHistorySelectTime && oldItem == newItem
               is CallHistoryBlockedStatus -> newItem is CallHistoryBlockedStatus && oldItem == newItem
               is CallHistorySafetyStatus -> newItem is CallHistorySafetyStatus && oldItem == newItem
               is CallHistoryFooter -> newItem is CallHistoryFooter
           }
       }
    }

    class HeaderViewHolder(
        view: View,
        photoClickAtPosition: (Int) -> Unit,
        callClickAtPosition: (Int) -> Unit,
        messageClickAtPosition: (Int) -> Unit,
        emailClickAtPosition: (Int) -> Unit,
    ) : RecyclerView.ViewHolder(view) {

        val photoButton: MaterialButton = view.findViewById(R.id.item_detail_photo)
        val nameTextView: TextView = view.findViewById(R.id.item_detail_display_name)
        val callButton: MaterialButton = view.findViewById(R.id.item_detail_call_button)
        val messageButton: MaterialButton = view.findViewById(R.id.item_detail_message_button)
        val emailButton: MaterialButton = view.findViewById(R.id.item_detail_email_button)

        init {
            photoButton.setOnClickListener {
                photoClickAtPosition(adapterPosition)
            }

            callButton.setOnClickListener {
                callClickAtPosition(adapterPosition)
            }

            messageButton.setOnClickListener {
                messageClickAtPosition(adapterPosition)
            }

            emailButton.setOnClickListener {
                emailClickAtPosition(adapterPosition)
            }
        }
    }

    class SelectTimeViewHolder(
        view: View
    ) : RecyclerView.ViewHolder(view) {

        val date: TextView = view.findViewById(R.id.item_call_history_date)
    }

    class BlockedStatusViewHolder(
        view: View,
        blockClickAtPosition: (Int) -> Unit
    ) : RecyclerView.ViewHolder(view) {

        val parentView: LinearLayout = view.findViewById(R.id.item_view_contact_buttons)
        val blockButton: MaterialButton = view.findViewById(R.id.item_view_contact_block_button)

        init {
            blockButton.setOnClickListener {
                blockClickAtPosition(adapterPosition)
            }
        }
    }

    class SafetyStatusViewHolder(
        view: View,
        spamClickAtPosition: (Int) -> Unit,
        defaultClickAtPosition: (Int) -> Unit,
        safeClickAtPosition: (Int) -> Unit,
    ) : RecyclerView.ViewHolder(view) {

        val parentView: LinearLayout = view.findViewById(R.id.item_call_history_buttons)
        val spamButton: MaterialButton = view.findViewById(R.id.item_call_history_spam_button)
        val defaultButton: MaterialButton = view.findViewById(R.id.item_call_history_default_button)
        val safeButton: MaterialButton = view.findViewById(R.id.item_call_history_safe_button)

        init {
            spamButton.setOnClickListener {
                spamClickAtPosition(adapterPosition)
            }

            defaultButton.setOnClickListener {
                defaultClickAtPosition(adapterPosition)
            }

            safeButton.setOnClickListener {
                safeClickAtPosition(adapterPosition)
            }
        }
    }

    class DataViewHolder(
        view: View
    ) : RecyclerView.ViewHolder(view) {

        val date: TextView = view.findViewById(R.id.item_call_time)
        val direction: TextView = view.findViewById(R.id.item_direction)
        val duration : TextView = view.findViewById(R.id.item_call_duration)
        val parent : ConstraintLayout = view.findViewById(R.id.call_history_item_parent)
    }

    class FooterViewHolder(
        view: View
    ) : RecyclerView.ViewHolder(view)

    /**
     * To disable recycler view blinking. Needs to provide unique id for each data list item so
     * that recycler view knows which items it needs to update (otherwise all of the items update,
     * which leads to the blinking effect). Since we store nulls in the data list (for the header
     * and footer), we need to assign unique ids for that case. For regular data, the callEpochDate
     * should suffice as a unique ID.
     */
    override fun getItemId(position: Int): Long {
        return getItem(position).longUUID
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is CallHistoryHeader -> HEADER_VIEW_TYPE
            is CallHistorySelectTime -> SELECT_TIME_VIEW_TYPE
            is CallHistoryBlockedStatus -> BLOCKED_STATUS_VIEW_TYPE
            is CallHistorySafetyStatus -> SAFETY_STATUS_VIEW_TYPE
            is CallHistoryData -> DATA_VIEW_TYPE
            is CallHistoryFooter -> FOOTER_VIEW_TYPE
            else -> throw Exception("Bad Item Type")
        }
    }


    /**
     * Create new views (invoked by the layout manager)
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            HEADER_VIEW_TYPE -> {
                val adapterLayout = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_detail_header, parent, false)
                HeaderViewHolder(
                    view = adapterLayout,
                    photoClickAtPosition = { pos -> photoOnClickListener(getItem(pos)) },
                    callClickAtPosition = { pos -> callOnClickListener(getItem(pos)) },
                    messageClickAtPosition = { pos -> messageOnClickListener(getItem(pos)) },
                    emailClickAtPosition = { pos -> emailOnClickListener(getItem(pos)) },
                )
            }
            SELECT_TIME_VIEW_TYPE -> {
                val adapterLayout = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_call_history_time, parent, false)
                SelectTimeViewHolder(adapterLayout)
            }
            DATA_VIEW_TYPE -> {
                val adapterLayout = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_call_history_call, parent, false)
                DataViewHolder(adapterLayout)
            }
            BLOCKED_STATUS_VIEW_TYPE -> {
                val adapterLayout = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_detail_blocked, parent, false)
                BlockedStatusViewHolder(
                    view = adapterLayout,
                    blockClickAtPosition = { pos -> blockOnClickListener(getItem(pos)) }
                )
            }
            SAFETY_STATUS_VIEW_TYPE -> {
                val adapterLayout = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_call_history_safety, parent, false)
                SafetyStatusViewHolder(
                    view = adapterLayout,
                    spamClickAtPosition = { pos -> spamOnClickListener(getItem(pos)) },
                    defaultClickAtPosition = { pos -> defaultOnClickListener(getItem(pos)) },
                    safeClickAtPosition = { pos -> safeOnClickListener(getItem(pos)) },
                )
            }
            FOOTER_VIEW_TYPE -> {
                val adapterLayout = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_call_history_footer, parent, false)
                FooterViewHolder(adapterLayout)
            }
            else -> {
                throw Exception("Bad View Type")
            }
        }
    }

    /**
     * Replace the contents of a view (invoked by the layout manager)
     */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> {
                val current = getItem(position) as CallHistoryHeader

                Timber.e("$DBL: onBindViewHolder() - CallHistoryHeader = $current")

                /*
                Only show the name TextView if there is a display name associated the contact.
                Technically, there should always be a display name, even if there is no actual
                name mime type data, as some other data (e.g., phone number) will fill the display
                name's value.
                 */
                if (current.displayName == null || current.displayName.trim() == "") {
                    holder.nameTextView.text = current.associatedNumber.ifEmpty { "Unknown" }
                } else {
                    holder.nameTextView.text = current.displayName
                }

                holder.callButton.isClickable = true
                holder.callButton.isFocusable = true
                holder.callButton.iconTint = ColorStateList.valueOf(
                    ContextCompat.getColor(applicationContext, R.color.purple_200)
                )

                holder.messageButton.isClickable = true
                holder.messageButton.isFocusable = true
                holder.messageButton.iconTint = ColorStateList.valueOf(
                    ContextCompat.getColor(applicationContext, R.color.purple_200)
                )

                /*
                If there are no emails associated with the contact, then disable the email button.
                 */
                if (current.primaryEmail == null) {
                    holder.emailButton.isClickable = false
                    holder.emailButton.isFocusable = false
                    holder.emailButton.iconTint = ColorStateList.valueOf(
                        ContextCompat.getColor(applicationContext, R.color.disabled_grey)
                    )
                } else {
                    holder.emailButton.isClickable = true
                    holder.emailButton.isFocusable = true
                    holder.emailButton.iconTint = ColorStateList.valueOf(
                        ContextCompat.getColor(applicationContext, R.color.purple_200)
                    )
                }
            }
            is SelectTimeViewHolder -> {
                val current = getItem(position) as CallHistorySelectTime

                holder.date.text = current.date
            }
            is BlockedStatusViewHolder -> {
                val current = getItem(position) as CallHistoryBlockedStatus

                /*
                Sets the block button text and UI coloring according to the blocked status.
                 */
                if (current.isBlocked) {
                    holder.blockButton.text = applicationContext.getString(R.string.item_detail_unblock)
                    holder.blockButton.setTextColor(ContextCompat.getColor(applicationContext, R.color.icon_white))
                    holder.blockButton.backgroundTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(applicationContext, R.color.purple_200)
                    )
                } else {
                    holder.blockButton.text = applicationContext.getString(R.string.item_detail_block)
                    holder.blockButton.setTextColor(ContextCompat.getColor(applicationContext, R.color.purple_200))
                    holder.blockButton.backgroundTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(applicationContext, R.color.grey)
                    )
                }
            }
            is SafetyStatusViewHolder -> {
                val current = getItem(position) as CallHistorySafetyStatus

                /*
                Sets the safety buttons UI coloring according to the safety status.
                 */
                when (current.safetyStatus) {
                    SafetyStatus.SPAM -> {
                        setButtonSelected(button = holder.spamButton)
                        setButtonUnselected(button = holder.defaultButton)
                        setButtonUnselected(button = holder.safeButton)
                    }
                    SafetyStatus.DEFAULT -> {
                        setButtonUnselected(button = holder.spamButton)
                        setButtonSelected(button = holder.defaultButton)
                        setButtonUnselected(button = holder.safeButton)
                    }
                    SafetyStatus.SAFE -> {
                        setButtonUnselected(button = holder.spamButton)
                        setButtonUnselected(button = holder.defaultButton)
                        setButtonSelected(button = holder.safeButton)
                    }
                }
            }
            is DataViewHolder -> {
                val prev = getItem(position - 1)
                val next = getItem(position + 1)

                val current = getItem(position) as CallHistoryData
                val currentData = current.callDetail

                val locale = Locale.getDefault()
                val simpleDate = SimpleDateFormat("hh:mm a", locale)
                val date = Date(currentData.callEpochDate)
                holder.date.text = simpleDate.format(date)

                holder.direction.text = getDirectionString(currentData.callDirection, currentData.rawNumber)

                holder.duration.text = applicationContext.getString(R.string.duration_format, currentData.callDuration)

                holder.parent.background = if (prev is CallHistorySelectTime && next is CallHistoryFooter) {
                    AppCompatResources.getDrawable(applicationContext, R.drawable.grey_rounded)
                } else if(position == 3) {
                    AppCompatResources.getDrawable(applicationContext, R.drawable.grey_top_rounded)
                } else if (position == currentList.size - 2) {
                    AppCompatResources.getDrawable(applicationContext, R.drawable.grey_bottom_rounded)
                } else {
                    AppCompatResources.getDrawable(applicationContext, R.drawable.grey_background)
                }
            }
            is FooterViewHolder -> {}
            else -> {
                throw Exception("Bad Holder / View Type")
            }
        }
    }

    private fun setButtonSelected(button: MaterialButton) {
        button.setTextColor(ContextCompat.getColor(applicationContext, R.color.icon_white))
        button.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(applicationContext, R.color.purple_200)
        )
    }

    private fun setButtonUnselected(button: MaterialButton) {
        button.setTextColor(ContextCompat.getColor(applicationContext, R.color.purple_200))
        button.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(applicationContext, R.color.grey)
        )
    }

    private fun getDirectionString(direction: Int, rawNumber: String): String {
        val trueDirection = TeleHelpers.getTrueDirection(applicationContext, direction, rawNumber)

        val directionString = when (trueDirection) {
            CallLog.Calls.INCOMING_TYPE  -> "Incoming Call"
            CallLog.Calls.OUTGOING_TYPE  -> "Outgoing Call"
            CallLog.Calls.MISSED_TYPE  -> "Missed Call"
            CallLog.Calls.VOICEMAIL_TYPE  -> "Voicemail"
            CallLog.Calls.REJECTED_TYPE  -> "Declined Call"
            CallLog.Calls.BLOCKED_TYPE -> "Blocked Call"
            else -> "Don't know what happened Call"
        }

        return directionString
    }
}