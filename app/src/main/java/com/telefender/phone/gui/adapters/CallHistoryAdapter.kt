package com.telefender.phone.gui.adapters

import android.content.Context
import android.provider.CallLog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.telefender.phone.R
import com.telefender.phone.data.tele_database.entities.CallDetail
import com.telefender.phone.data.tele_database.entities.CallDetailItem
import com.telefender.phone.data.tele_database.entities.CallHistoryFooter
import com.telefender.phone.data.tele_database.entities.CallHistoryHeader
import com.telefender.phone.helpers.MiscHelpers
import java.text.SimpleDateFormat
import java.util.*

class CallHistoryAdapter (
    private val context: Context,
    private val selectNumber: String,
    private val selectTime : String,
) : ListAdapter<CallDetailItem, RecyclerView.ViewHolder>(CombinedComparator()) {

    val INFO_VIEW_TYPE = 1
    val CALL_VIEW_TYPE = 2
    val FOOTER_VIEW_TYPE = 3

    val INFO_UUID = -1L
    val FOOTER_UUID = -2L

    class CombinedComparator : DiffUtil.ItemCallback<CallDetailItem>() {

       override fun areItemsTheSame(oldItem: CallDetailItem, newItem: CallDetailItem): Boolean {
            return oldItem === newItem
        }

        override fun areContentsTheSame(oldItem: CallDetailItem, newItem: CallDetailItem): Boolean {
            return if (oldItem is CallDetail && newItem is CallDetail) {
                oldItem.callEpochDate == newItem.callEpochDate
            } else {
                oldItem == newItem
            }
        }
    }

    class InfoHistoryViewHolder(
        view: View
    ) : RecyclerView.ViewHolder(view) {
        val number : TextView = view.findViewById(R.id.call_history_number)
        val date: TextView = view.findViewById(R.id.call_history_date)
        val blockButton: MaterialButton = view.findViewById(R.id.block_button)
        val businessButton: MaterialButton = view.findViewById(R.id.safe_button)
        val infoImage: MaterialButton = view.findViewById(R.id.info_image)
    }

    class CallHistoryViewHolder(
        view: View
    ) : RecyclerView.ViewHolder(view) {

        val date: TextView = view.findViewById(R.id.item_call_time)
        val direction: TextView = view.findViewById(R.id.item_direction)
        val duration : TextView = view.findViewById(R.id.item_call_duration)
        val parent : ConstraintLayout = view.findViewById(R.id.call_history_item_parent)
    }

    class FooterHistoryViewHolder(
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
        val current = getItem(position)
        return when (current) {
            is CallDetail -> current.callEpochDate
            is CallHistoryHeader -> INFO_UUID
            is CallHistoryFooter -> FOOTER_UUID
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is CallDetail -> CALL_VIEW_TYPE
            is CallHistoryHeader -> INFO_VIEW_TYPE
            is CallHistoryFooter -> FOOTER_VIEW_TYPE
        }
    }


    /**
     * Create new views (invoked by the layout manager)
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        return when (viewType) {
            INFO_VIEW_TYPE -> {
                val adapterLayout = LayoutInflater.from(parent.context)
                    .inflate(R.layout.info_history_item, parent, false)
                InfoHistoryViewHolder(adapterLayout)
            }
            CALL_VIEW_TYPE -> {
                val adapterLayout = LayoutInflater.from(parent.context)
                    .inflate(R.layout.call_history_item, parent, false)
                CallHistoryViewHolder(adapterLayout)
            }
            FOOTER_VIEW_TYPE -> {
                val adapterLayout = LayoutInflater.from(parent.context)
                    .inflate(R.layout.footer_history_item, parent, false)
                FooterHistoryViewHolder(adapterLayout)
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
            is CallHistoryViewHolder -> {
                val current = getItem(position)!! as CallDetail

                val simpleDate = SimpleDateFormat("hh:mm a")
                val date = Date(current.callEpochDate)
                holder.date.text = simpleDate.format(date)

                holder.direction.text = getDirectionString(current.callDirection, current.rawNumber)

                holder.duration.text = current.callDuration.toString() + " seconds"

                if (position == 1) {
                    holder.parent.background = context.getDrawable(R.drawable.grey_top_rounded)
                } else if (position == currentList.size - 2) {
                    holder.parent.background = context.getDrawable(R.drawable.grey_bottom_rounded)
                } else {
                    holder.parent.background = context.getDrawable(R.drawable.grey_background)
                }
            }
            is InfoHistoryViewHolder -> {

                holder.number.text = if(selectNumber.isNotEmpty()) selectNumber else "Unknown"

                holder.date.text = selectTime

                /**
                 * Handle block button
                 */
                holder.blockButton.setOnClickListener { view ->
                    val button = view as MaterialButton
                    if (button.text.toString().lowercase() == "block") {
                        button.text = "Unblock"
                        if (holder.businessButton.text.toString().lowercase() == "unmark organization") {
                            holder.businessButton.text = "Mark Organization"
                            holder.businessButton.setTextColor(ContextCompat.getColor(context, R.color.business_green))
                        }
                        button.setTextColor(ContextCompat.getColor(context, R.color.business_blue))

                        // Set info image outline to show block status
                        holder.infoImage.setStrokeColorResource(R.color.block_red)
                    } else {
                        button.text = "Block"
                        button.setTextColor(ContextCompat.getColor(context, R.color.block_red))

                        // Set info image outline to show unblocked status
                        holder.infoImage.setStrokeColorResource(R.color.grey)
                    }
                }

                /**
                 * Handle mark as business button
                 */
                holder.businessButton.setOnClickListener { view ->
                    val button = view as MaterialButton
                    if (button.text.toString().lowercase() == "safe") {
                        button.text = "Unsafe"
                        if (holder.blockButton.text.toString().lowercase() == "unblock") {
                            holder.blockButton.text = "Block"
                            holder.blockButton.setTextColor(ContextCompat.getColor(context, R.color.block_red))
                        }
                        button.setTextColor(ContextCompat.getColor(context, R.color.business_blue))

                        // Set info image outline to show organization status
                        holder.infoImage.setStrokeColorResource(R.color.business_blue)
                    } else {
                        button.text = "Safe"
                        button.setTextColor(ContextCompat.getColor(context, R.color.business_blue))

                        // Set info image outline to show not organization status
                        holder.infoImage.setStrokeColorResource(R.color.grey)
                    }
                }
            }
            is FooterHistoryViewHolder -> {}
            else -> {
                throw Exception("Bad Holder / View Type")
            }
        }
    }

    private fun getDirectionString(direction: Int?, rawNumber: String): String {
        val trueDirection = MiscHelpers.getTrueDirection(direction, rawNumber)

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