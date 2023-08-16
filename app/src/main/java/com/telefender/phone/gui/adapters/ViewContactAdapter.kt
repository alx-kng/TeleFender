package com.telefender.phone.gui.adapters

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.telefender.phone.R
import com.telefender.phone.gui.adapters.recycler_view_items.ViewContactData
import com.telefender.phone.gui.adapters.recycler_view_items.ViewContactFooter
import com.telefender.phone.gui.adapters.recycler_view_items.ViewContactHeader
import com.telefender.phone.gui.adapters.recycler_view_items.ViewContactItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers


class ViewContactAdapter (
    private val applicationContext: Context,
    private val photoOnClickListener: (ViewContactItem) -> Unit,
    private val callOnClickListener: (ViewContactItem) -> Unit,
    private val messageOnClickListener: (ViewContactItem) -> Unit,
    private val emailOnClickListener: (ViewContactItem) -> Unit,
    private val dataOnClickListener: (ViewContactItem) -> Unit,
) : ListAdapter<ViewContactItem, RecyclerView.ViewHolder>(CombinedComparator()) {

    val scope = CoroutineScope(Dispatchers.Default)

    val HEADER_VIEW_TYPE = 1
    val DATA_VIEW_TYPE = 2
    val FOOTER_VIEW_TYPE = 3

    class CombinedComparator : DiffUtil.ItemCallback<ViewContactItem>() {

        override fun areItemsTheSame(oldItem: ViewContactItem, newItem: ViewContactItem): Boolean {
            return oldItem.longUUID == newItem.longUUID
        }

        override fun areContentsTheSame(oldItem: ViewContactItem, newItem: ViewContactItem): Boolean {
            return when (oldItem) {
                is ViewContactData -> newItem is ViewContactData && oldItem.contactData == newItem.contactData
                is ViewContactHeader -> newItem is ViewContactHeader
                is ViewContactFooter -> newItem is ViewContactFooter
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

        val photoButton: MaterialButton = view.findViewById(R.id.item_view_contact_photo)
        val nameTextView: TextView = view.findViewById(R.id.item_view_contact_display_name)
        val callButton: MaterialButton = view.findViewById(R.id.item_view_contact_call_button)
        val messageButton: MaterialButton = view.findViewById(R.id.item_view_contact_message_button)
        val emailButton: MaterialButton = view.findViewById(R.id.item_view_contact_email_button)

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

    class DataViewHolder(
        view: View,
        dataCLickAtPosition: (Int) -> Unit
    ) : RecyclerView.ViewHolder(view) {

        val dataCard: MaterialCardView = view.findViewById(R.id.item_view_contact_card)
        val dataType: TextView = view.findViewById(R.id.item_view_contact_type)
        val dataValue: TextView = view.findViewById(R.id.item_view_contact_value)

        init {
            dataCard.setOnClickListener {
                dataCLickAtPosition(adapterPosition)
            }
        }
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
            is ViewContactHeader -> HEADER_VIEW_TYPE
            is ViewContactData -> DATA_VIEW_TYPE
            is ViewContactFooter -> FOOTER_VIEW_TYPE
        }
    }

    /**
     * Create new views (invoked by the layout manager)
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            HEADER_VIEW_TYPE -> {
                val adapterLayout = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_view_contact_header, parent, false)
                HeaderViewHolder(
                    view = adapterLayout,
                    photoClickAtPosition = { pos -> photoOnClickListener(getItem(pos)) },
                    callClickAtPosition = { pos -> callOnClickListener(getItem(pos)) },
                    messageClickAtPosition = { pos -> messageOnClickListener(getItem(pos)) },
                    emailClickAtPosition = { pos -> emailOnClickListener(getItem(pos)) },
                )
            }
            DATA_VIEW_TYPE-> {
                val adapterLayout = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_view_contact_data, parent, false)
                DataViewHolder(adapterLayout) { pos ->
                    dataOnClickListener(getItem(pos))
                }
            }
            FOOTER_VIEW_TYPE -> {
                val adapterLayout = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_view_contact_footer, parent, false)
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
                val current = getItem(position) as ViewContactHeader

                /*
                Only show the name TextView if there is a display name associated the contact.
                Technically, there should always be a display name, even if there is no actual
                name mime type data, as some other data (e.g., phone number) will fill the display
                name's value.
                 */
                if (current.displayName == null || current.displayName.trim() == "") {
                    holder.nameTextView.visibility = View.GONE
                } else {
                    holder.nameTextView.text = current.displayName
                    holder.nameTextView.visibility = View.VISIBLE
                }

                /*
                If there is no number associated with the contact, then disable the call and
                message buttons.
                 */
                if (current.primaryNumber == null) {
                    holder.callButton.isClickable = false
                    holder.callButton.isFocusable = false
                    holder.callButton.iconTint = ColorStateList.valueOf(
                        ContextCompat.getColor(applicationContext, R.color.disabled_grey)
                    )

                    holder.messageButton.isClickable = false
                    holder.messageButton.isFocusable = false
                    holder.messageButton.iconTint = ColorStateList.valueOf(
                        ContextCompat.getColor(applicationContext, R.color.disabled_grey)
                    )
                } else {
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
                }

                /*
                Similarly, if there are no emails associated with the contact, then disable the
                email button.
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
            is DataViewHolder -> {
                val current = getItem(position) as ViewContactData

                /*
                Only show the type TextView if the type exists. Currently, the type should always
                exist, as we don't include name Data (which have no type) as ViewContactData.
                 */
                val typeString = current.contactData.primaryCompactInfo()?.valueType?.displayString
                if (typeString == null) {
                    holder.dataType.visibility = View.GONE
                } else {
                    holder.dataType.text = typeString
                    holder.dataType.visibility = View.VISIBLE
                }

                holder.dataValue.text = current.contactData.value
            }
            is FooterViewHolder -> {}
            else -> {
                throw Exception("Bad Holder / View Type")
            }
        }
    }
}