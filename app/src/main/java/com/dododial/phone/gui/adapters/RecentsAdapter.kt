package com.dododial.phone.gui.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dododial.phone.R
import com.dododial.phone.data.default_database.GroupedCallDetail
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*


class RecentsAdapter(
    private val context: Context,
    private val viewClickListener: (String) -> Unit,
    private val infoClickListener: (String, Long) -> Unit
) : ListAdapter<GroupedCallDetail, RecentsAdapter.RecentsViewHolder>(RecentsComparator()) {

    class RecentsViewHolder(
        view: View,
        viewClickAtPosition: (Int) -> Unit,
        infoClickAtPosition: (Int) -> Unit
    ) : RecyclerView.ViewHolder(view) {

        val number: TextView = view.findViewById(R.id.item_number)
        val location: TextView = view.findViewById(R.id.item_location)
        val date: TextView = view.findViewById(R.id.item_call_time)
        val direction: ImageView = view.findViewById(R.id.item_direction)
        val info: MaterialButton = view.findViewById(R.id.item_call_info)

        init {
            view.setOnClickListener {
                viewClickAtPosition(adapterPosition)
            }

            info.setOnClickListener {
                infoClickAtPosition(adapterPosition)
            }
        }
    }

    class RecentsComparator : DiffUtil.ItemCallback<GroupedCallDetail>() {
        override fun areItemsTheSame(oldItem: GroupedCallDetail, newItem: GroupedCallDetail): Boolean {
            return oldItem === newItem
        }

        override fun areContentsTheSame(oldItem: GroupedCallDetail, newItem: GroupedCallDetail): Boolean {
            return oldItem.callEpochDate == newItem.callEpochDate && oldItem.amount == newItem.amount
        }
    }

    // To disable recycler view blinking
    override fun getItemId(position: Int): Long {
        return currentList[position].firstEpochID
    }

    /**
     * Create new views (invoked by the layout manager)
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentsViewHolder {
        // create a new view
        val adapterLayout = LayoutInflater.from(parent.context)
            .inflate(R.layout.recents_item, parent, false)

        val viewHolder = RecentsViewHolder(adapterLayout,
            { pos ->
                viewClickListener(getItem(pos).number)
            },
            { pos ->
                val log = getItem(pos)
                infoClickListener(log.number, log.callEpochDate)
            }
        )

        return viewHolder
    }

    /**
     * Replace the contents of a view (invoked by the layout manager)
     */
    override fun onBindViewHolder(holder: RecentsViewHolder, position: Int) {
        val current = getItem(position)
        holder.number.text = getFormattedNumber(current.number, current.amount)
        holder.number.setTextColor(ContextCompat.getColor(context, R.color.icon_white))

        if (holder.number.currentTextColor != R.color.icon_white) {
            holder.number.setTextColor(ContextCompat.getColor(context, R.color.icon_white))
        }
        if (isRed(current.callDirection)) {
            holder.number.setTextColor(ContextCompat.getColor(context, R.color.missed_red))
        }

        holder.location.text = current.callLocation ?: "Unknown location"

        holder.date.text = getDate(current.callEpochDate)

        val icon = getDirectionIcon(current.callDirection, current.number)
        holder.direction.setImageResource(icon)
    }

    private fun getFormattedNumber(number: String, amount: Int): String {

        return if (number.isEmpty()) {
            "Unknown"
        } else if (amount == 1) {
            number
        } else {
            "$number ($amount)"
        }
    }

    private fun getDate(date: Long): String {
        val currentTime = LocalDateTime.now()
        val milliInDay = 24 * 60 * 60 * 1000
        val today = currentTime
            .toLocalDate()
            .atStartOfDay()
            .toInstant(ZoneId.systemDefault().rules.getOffset(currentTime))
            .toEpochMilli()
        val yesterday = today - milliInDay
        val weekBefore = today - 7 * milliInDay

        if (date >= today) {
            val simpleDate = SimpleDateFormat("hh:mm a")
            return simpleDate.format(Date(date))
        } else if (date >= yesterday) {
            return "Yesterday"
        } else if (date >= weekBefore) {
            val curDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(date), ZoneId.systemDefault())
            val upperCase = curDate.dayOfWeek.toString().lowercase(Locale.getDefault())
            val day = upperCase.replaceFirst(upperCase[0], upperCase[0].uppercaseChar())
            return day
        } else {
            val simpleDate = SimpleDateFormat("MM/dd/yy")
            return simpleDate.format(Date(date))
        }
    }

    private fun isRed(direction: String?): Boolean {
        return when (direction) {
            "MISSED" -> true
            else -> false
        }
    }

    private fun getDirectionIcon(direction: String?, number: String): Int {
        var icon = when (direction) {
            "INCOMING" -> R.drawable.ic_baseline_call_received_24
            "OUTGOING" -> R.drawable.ic_baseline_call_made_24
            "MISSED" -> R.drawable.ic_baseline_call_missed_24
            "VOICEMAIL" -> R.drawable.ic_baseline_voicemail_24
            "REJECTED" -> R.drawable.ic_baseline_block_24
            "BLOCKED" -> R.drawable.ic_baseline_block_24
            else -> R.drawable.ic_baseline_circle_24
        }

        //TODO Voicemail call log refine
        /**
         * Duct tape way to check if call log is a voicemail or not. Uses the idea that
         * voicemail call logs always have a '+' in front of the number and are incoming.
         * Don't know how this changes between carriers.
         */
        if (number.isNotEmpty() && number[0] == '+' && direction == "INCOMING") {
            icon = R.drawable.ic_baseline_voicemail_24
        }

        return icon
    }
}