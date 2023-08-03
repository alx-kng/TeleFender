package com.telefender.phone.gui.adapters

import android.content.Context
import android.provider.CallLog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.telefender.phone.R
import com.telefender.phone.data.tele_database.entities.CallDetail
import com.telefender.phone.gui.adapters.recycler_view_items.*
import com.telefender.phone.misc_helpers.TeleHelpers
import java.text.SimpleDateFormat
import java.util.*


class ChangeContactAdapter (
    private val context: Context,
) : ListAdapter<ChangeContactItem, RecyclerView.ViewHolder>(CombinedComparator()) {

    val SECTION_HEADER_VIEW_TYPE = 1
    val BLANK_EDIT_VIEW_TYPE = 2
    val CONTACT_DATA_VIEW_TYPE = 3

    class CombinedComparator : DiffUtil.ItemCallback<ChangeContactItem>() {

        override fun areItemsTheSame(oldItem: ChangeContactItem, newItem: ChangeContactItem): Boolean {
            return oldItem === newItem
        }

        override fun areContentsTheSame(oldItem: ChangeContactItem, newItem: ChangeContactItem): Boolean {
            return oldItem.longUUID == newItem.longUUID
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

    class HeaderViewHolder(
        view: View
    ) : RecyclerView.ViewHolder(view)

    class EditViewHolder(
        view: View
    ) : RecyclerView.ViewHolder(view) {

        val icon: ImageView = view.findViewById(R.id.icon)
    }

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
            is SectionHeader -> SECTION_HEADER_VIEW_TYPE
            is BlankEdit -> BLANK_EDIT_VIEW_TYPE
            is ContactData -> CONTACT_DATA_VIEW_TYPE
        }
    }


    /**
     * Create new views (invoked by the layout manager)
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            SECTION_HEADER_VIEW_TYPE -> {
                val adapterLayout = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_change_contact_header, parent, false)
                HeaderViewHolder(adapterLayout)
            }
            BLANK_EDIT_VIEW_TYPE, CONTACT_DATA_VIEW_TYPE-> {
                val adapterLayout = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_change_contact_edit, parent, false)
                EditViewHolder(adapterLayout)
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

//                holder.number.text = if(selectNumber.isNotEmpty()) selectNumber else "Unknown"

//                holder.date.text = selectTime

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

    private fun getDirectionString(direction: Int, rawNumber: String): String {
        val trueDirection = TeleHelpers.getTrueDirection(context, direction, rawNumber)

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