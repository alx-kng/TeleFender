package com.telefender.phone.gui.adapters

import android.content.Context
import android.telecom.Call
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.telefender.phone.R
import com.telefender.phone.call_related.CallManager
import com.telefender.phone.call_related.connectTime
import com.telefender.phone.call_related.number
import com.telefender.phone.misc_helpers.TeleHelpers


class ConferenceAdapter(
    private val context: Context
) : ListAdapter<Call, ConferenceAdapter.ConferenceViewHolder>(ConferenceComparator()) {

    class ConferenceViewHolder(
        view: View,
        hangupAtPosition: (Int) -> Unit
    ) : RecyclerView.ViewHolder(view) {

        val numberOrContact: TextView = view.findViewById(R.id.number_or_contact)
        val smallNumber: TextView = view.findViewById(R.id.small_number)
        val hangup: Button = view.findViewById(R.id.hangup_item)

        init {
            hangup.setOnClickListener {
                hangupAtPosition(adapterPosition)
            }
        }
    }

    class ConferenceComparator : DiffUtil.ItemCallback<Call>() {
        override fun areItemsTheSame(oldItem: Call, newItem: Call): Boolean {
            return oldItem === newItem
        }

        override fun areContentsTheSame(oldItem: Call, newItem: Call): Boolean {
            return oldItem.connectTime() == newItem.connectTime()
        }
    }

    // To disable recycler view blinking
    override fun getItemId(position: Int): Long {
        return currentList[position].connectTime()
    }

    /**
     * Create new views (invoked by the layout manager)
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConferenceViewHolder {
        // create a new view
        val adapterLayout = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conference, parent, false)

        return ConferenceViewHolder(adapterLayout) {pos -> CallManager.hangupArg(getItem(pos)) }
    }

    /**
     * Replace the contents of a view (invoked by the layout manager)
     */
    override fun onBindViewHolder(holder: ConferenceViewHolder, position: Int) {
        val current = getItem(position)

        holder.numberOrContact.text = getNumber(current)
    }

    fun getNumber(call: Call?): String {
        val temp: String? = call.number()
        val number = if (!temp.isNullOrEmpty()) {
            TeleHelpers.getContactName(context, temp)
                ?: TeleHelpers.normalizedNumber(temp)
                ?: temp
        } else {
            if (temp == null) {
                throw Exception("Shouldn't have null number in single display.")
            } else {
                "Unknown"
            }
        }

        return number

    }
}