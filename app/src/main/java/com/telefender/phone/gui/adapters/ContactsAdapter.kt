package com.telefender.phone.gui.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.telefender.phone.R
import com.telefender.phone.gui.adapters.recycler_view_items.AggregateContact
import com.telefender.phone.gui.adapters.recycler_view_items.ContactFooter
import com.telefender.phone.gui.adapters.recycler_view_items.BaseContactItem
import com.telefender.phone.gui.adapters.recycler_view_items.Divider


class ContactsAdapter(
    private val context: Context)
    : ListAdapter<BaseContactItem, RecyclerView.ViewHolder>(ContactComparator()) {

    private val TYPE_CONTACT = 0
    private val TYPE_DIVIDER = 1
    private val TYPE_FOOTER = 2

    private val FOOTER_UUID = -1L

    class ContactViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name : TextView = view.findViewById(R.id.contact_name)
    }

    class DividerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val letter : TextView = view.findViewById(R.id.letter)
    }

    class FooterViewHolder(view: View) : RecyclerView.ViewHolder(view)

    class ContactComparator : DiffUtil.ItemCallback<BaseContactItem>() {
        override fun areItemsTheSame(oldItem: BaseContactItem, newItem: BaseContactItem): Boolean {
            return oldItem === newItem
        }

        override fun areContentsTheSame(oldItem: BaseContactItem, newItem: BaseContactItem): Boolean {
            return oldItem == newItem
        }
    }

    override fun getItemId(position: Int): Long {
        val current = getItem(position)
        return when (current) {
            is AggregateContact -> current.hashCode().toLong()
            is Divider -> current.hashCode().toLong()
            is ContactFooter -> FOOTER_UUID
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is AggregateContact -> TYPE_CONTACT
            is Divider -> TYPE_DIVIDER
            is ContactFooter -> TYPE_FOOTER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_CONTACT -> {
                val adapterLayout = LayoutInflater.from(parent.context).inflate(R.layout.contact_item,
                    parent, false)
                ContactViewHolder(adapterLayout)
            }
            TYPE_DIVIDER -> {
                val adapterLayout = LayoutInflater.from(parent.context).inflate(R.layout.divider_contact_item,
                    parent, false)
                DividerViewHolder(adapterLayout)
            }
            TYPE_FOOTER -> {
                val adapterLayout = LayoutInflater.from(parent.context).inflate(R.layout.footer_contact_item,
                    parent, false)
                FooterViewHolder(adapterLayout)
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
                val current = getItem(position) as Divider
                holder.letter.text = current.letter
            }
            is FooterViewHolder -> {}
            else -> throw IllegalArgumentException("Invalid View Type!")
        }
    }
}