package com.telefender.phone.gui.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.telefender.phone.R
import com.telefender.phone.gui.adapters.recycler_view_items.*


class ContactsAdapter(
    private val applicationContext: Context,
    private val contactClickListener: (BaseContactItem) -> Unit
) : ListAdapter<BaseContactItem, RecyclerView.ViewHolder>(CombinedComparator()) {

    private val TYPE_CONTACT = 0
    private val TYPE_DIVIDER = 1
    private val TYPE_FOOTER = 2

    private val FOOTER_UUID = -1L

    class CombinedComparator : DiffUtil.ItemCallback<BaseContactItem>() {

        override fun areItemsTheSame(oldItem: BaseContactItem, newItem: BaseContactItem): Boolean {
            return oldItem.hashCode() == newItem.hashCode()
        }

        override fun areContentsTheSame(oldItem: BaseContactItem, newItem: BaseContactItem): Boolean {
            return when (oldItem) {
                is AggregateContact -> newItem is AggregateContact && oldItem == newItem
                is BaseDivider -> newItem is BaseDivider && oldItem == newItem
                is BaseFooter -> newItem is BaseFooter
            }
        }
    }

    class ContactViewHolder(
        view: View,
        contactClickAtPosition: (Int) -> Unit
    ) : RecyclerView.ViewHolder(view) {

        val name: TextView = view.findViewById(R.id.item_contact_aggregate_name)
        val parentLayout: MaterialCardView = view.findViewById(R.id.item_contact_aggregate_parent)

        init {
            parentLayout.setOnClickListener {
                contactClickAtPosition(adapterPosition)
            }
        }
    }

    class DividerViewHolder(
        view: View
    ) : RecyclerView.ViewHolder(view) {

        val letter : TextView = view.findViewById(R.id.letter)
    }

    class FooterViewHolder(
        view: View
    ) : RecyclerView.ViewHolder(view)

    override fun getItemId(position: Int): Long {
        val current = getItem(position)
        return when (current) {
            is AggregateContact -> current.hashCode().toLong()
            is BaseDivider -> current.hashCode().toLong()
            is BaseFooter -> FOOTER_UUID
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is AggregateContact -> TYPE_CONTACT
            is BaseDivider -> TYPE_DIVIDER
            is BaseFooter -> TYPE_FOOTER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_CONTACT -> {
                val adapterLayout = LayoutInflater.from(parent.context).inflate(R.layout.item_contact_aggregate,
                    parent, false)
                ContactViewHolder(adapterLayout) { pos ->
                    contactClickListener(getItem(pos))
                }
            }
            TYPE_DIVIDER -> {
                val adapterLayout = LayoutInflater.from(parent.context).inflate(R.layout.item_contact_divider,
                    parent, false)
                DividerViewHolder(adapterLayout)
            }
            TYPE_FOOTER -> {
                val adapterLayout = LayoutInflater.from(parent.context).inflate(R.layout.item_contact_footer,
                    parent, false)
                ChangeContactAdapter.FooterViewHolder(adapterLayout)
            }
            else -> {
                throw Exception("Bad View Type")
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ContactViewHolder -> {
                val current = getItem(position) as AggregateContact
                holder.name.text = current.name
            }
            is DividerViewHolder -> {
                val current = getItem(position) as BaseDivider
                holder.letter.text = current.text
            }
            is ChangeContactAdapter.FooterViewHolder -> {}
            else -> throw IllegalArgumentException("Invalid View Type!")
        }
    }
}