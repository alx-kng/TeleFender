package com.telefender.phone.gui.adapters

import android.content.Context
import android.provider.ContactsContract
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.telefender.phone.R
import com.telefender.phone.gui.adapters.custom_views.CustomTextInputEditText
import com.telefender.phone.gui.adapters.recycler_view_items.*
import com.telefender.phone.misc_helpers.DBL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import timber.log.Timber

/**
 * TODO: Later on when displaying value type, make sure to use default value type for mimetype if
 *  the value type in the ContactData is null.
 *
 * TODO: Clean up code.
 *
 * TODO: Auto change focus to new field when ChangeContactAdder is pressed! -> Think it's done.
 *
 * TODO: EditText should still stay in focus even after scrolling away from edit text. -> Think
 *  it's fixed, but double check. -> occasionally doesn't work when scrolling fast or slow -> prob
 *  not a big deal for first version but try to fix one day. -> If it's really still a problem,
 *  we could try requesting focus again in the ViewAttached callback. It's may be partially
 *  redundant, but it could fix those edge cases.
 *
 * TODO: InputType of soft input changes when scrolling away from EditText. Solution will be tricky,
 *  as the keypad is no longer linked to the same view once it's destroyed. One possible idea is
 *  to hide a gone edit view (with the same input type) somewhere on the screen of the RecyclerView
 *  and switch focus once the original edit view is no longer visible. If the user types, then we
 *  could either record the changes in the temp gone view and transfer the changes to the original
 *  ContactData, or we could try to scroll back up to the original view.
 *  ->
 *  Kinda fixed, but can probably improve on current solution.
 *  ->
 *  Solution is decent now, so maybe (instead of probably) can improve.
 *
 * TODO: Implement click away to get rid of blinking edit line. -> Maybe not? I mean, default app
 *  doesn't get rid of blinking edit line.
 */
class ChangeContactAdapter (
    private val applicationContext: Context,
    var lastNonContactDataList: List<ChangeContactItem>,
    private val adderClickListener: (ChangeContactItem) -> Unit,
    private val onTextChangedLambda: (ChangeContactItem, String) -> Unit,
    private val removeClickListener: (ChangeContactItem) -> Unit,
    private val deleterClickListener: () -> Unit
) : ListAdapter<ChangeContactItem, RecyclerView.ViewHolder>(CombinedComparator()) {

    val scope = CoroutineScope(Dispatchers.Default)

    var currentFocusedItemID: Long? = null
    val textWatcherMap: MutableMap<Long, TextWatcher> = mutableMapOf()

    val SECTION_HEADER_VIEW_TYPE = 1
    val ITEM_ADDER_VIEW_TYPE = 2
    val CONTACT_DATA_VIEW_TYPE = 3
    val FOOTER_VIEW_TYPE = 4
    val DELETER_VIEW_TYPE = 5

    class CombinedComparator : DiffUtil.ItemCallback<ChangeContactItem>() {

        override fun areItemsTheSame(oldItem: ChangeContactItem, newItem: ChangeContactItem): Boolean {
            return oldItem.longUUID == newItem.longUUID
        }

        override fun areContentsTheSame(oldItem: ChangeContactItem, newItem: ChangeContactItem): Boolean {
            return when (oldItem) {
                is ContactData -> newItem is ContactData && oldItem == newItem
                is ChangeContactAdder -> newItem is ChangeContactAdder && oldItem == newItem
                is ChangeContactHeader -> newItem is ChangeContactHeader && oldItem == newItem
                is ChangeContactFooter -> newItem is ChangeContactFooter
                is ChangeContactDeleter -> newItem is ChangeContactDeleter
            }
        }
    }

    class HeaderViewHolder(
        view: View
    ) : RecyclerView.ViewHolder(view)

    class AdderViewHolder(
        view: View,
        adderClickAtPosition: (Int) -> Unit
    ) : RecyclerView.ViewHolder(view) {

        val adderView: View = view.findViewById(R.id.item_change_contact_adder_view)
        val adderText: TextView = view.findViewById(R.id.item_change_contact_adder_text)

        init {
            adderView.setOnClickListener {
                adderClickAtPosition(adapterPosition)
            }
        }
    }

    inner class EditViewHolder(
        view: View,
        removeClickAtPosition: (Int) -> Unit
    ) : RecyclerView.ViewHolder(view) {

        val icon: ImageView = view.findViewById(R.id.item_change_contact_icon)
        val editLayout: TextInputLayout = view.findViewById(R.id.item_change_contact_text_layout)
        val editText: CustomTextInputEditText = view.findViewById(R.id.item_change_contact_edit)
        val removeButton: MaterialButton = view.findViewById(R.id.item_change_contact_remove)

        init {
            editText.setOnClickListener {
                Timber.e("$DBL: EditText onClick!")

                // adapterPosition is unreliable long-term, so we need to store here.
                val storedPosition = adapterPosition

                // Makes sure adapter position is still valid.
                if (storedPosition == RecyclerView.NO_POSITION) return@setOnClickListener

                if (currentFocusedItemID != getItemId(storedPosition)) {
                    Timber.e("$DBL: New User Focus - ItemID: ${getItemId(storedPosition)}, %s",
                        "InputType = ${getInputType(getItem(storedPosition).mimeType)}")
                }

                // Update the itemID of the current focused item.
                currentFocusedItemID = getItemId(storedPosition)
            }

            removeButton.setOnClickListener {
                val position = adapterPosition

                if (position != RecyclerView.NO_POSITION) {
                    val current = getItem(position)
                    val textWatcher = textWatcherMap[current.longUUID]

                    // Remove TextWatcher from both View and map.
                    editText.removeTextChangedListener(textWatcher)
                    textWatcherMap.remove(current.longUUID)

                    removeClickAtPosition(position)
                }
            }
        }
    }

    class FooterViewHolder(
        view: View
    ) : RecyclerView.ViewHolder(view)

    class DeleterViewHolder(
        view: View,
        deleterClickListener: () -> Unit
    ) : RecyclerView.ViewHolder(view) {

        val deleterView: View = view.findViewById(R.id.item_change_contact_deleter_subview)
        val deleterText: TextView = view.findViewById(R.id.item_change_contact_deleter_text)

        init {
            deleterView.setOnClickListener {
                deleterClickListener()
            }
        }
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
            is ChangeContactHeader -> SECTION_HEADER_VIEW_TYPE
            is ChangeContactAdder -> ITEM_ADDER_VIEW_TYPE
            is ContactData -> CONTACT_DATA_VIEW_TYPE
            is ChangeContactFooter -> FOOTER_VIEW_TYPE
            is ChangeContactDeleter -> DELETER_VIEW_TYPE
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
            ITEM_ADDER_VIEW_TYPE -> {
                val adapterLayout = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_change_contact_adder, parent, false)
                AdderViewHolder(adapterLayout) { pos ->
                    adderClickListener(getItem(pos))
                }
            }
            CONTACT_DATA_VIEW_TYPE-> {
                val adapterLayout = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_change_contact_edit, parent, false)
                EditViewHolder(adapterLayout) { pos ->
                    removeClickListener(getItem(pos))
                }
            }
            FOOTER_VIEW_TYPE -> {
                val adapterLayout = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_change_contact_footer, parent, false)
                FooterViewHolder(adapterLayout)
            }
            DELETER_VIEW_TYPE -> {
                val adapterLayout = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_change_contact_deleter, parent, false)
                DeleterViewHolder(adapterLayout, deleterClickListener)
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
            is HeaderViewHolder -> {}
            is AdderViewHolder -> {
                val current = getItem(position)

                holder.adderText.text = when (current.mimeType) {
                    ContactDataMimeType.PHONE -> "Add Phone"
                    ContactDataMimeType.EMAIL -> "Add Email"
                    ContactDataMimeType.ADDRESS -> "Add Address"
                    else -> "Shouldn't be here!"
                }
            }
            is EditViewHolder -> {
                val current = getItem(position) as ContactData
                val last = getItem(position - 1)

                val icon = when (current.mimeType) {
                    ContactDataMimeType.NAME -> R.drawable.ic_baseline_person_outline_24
                    ContactDataMimeType.PHONE -> R.drawable.ic_baseline_call_24_white
                    ContactDataMimeType.EMAIL -> R.drawable.ic_baseline_email_24
                    ContactDataMimeType.ADDRESS -> R.drawable.ic_baseline_location_on_24
                }
                holder.icon.setImageResource(icon)

                /*
                If the previous item is a header item, then we know that the EditViewHolder must
                hold the first BlankEdit / ContactData, which means we show the icon.
                 */
                holder.icon.visibility = if (lastNonContactDataList.any { it === last }) {
                    View.VISIBLE
                } else {
                    View.INVISIBLE
                }

                // Hide the remove button for the Name data, as there is only one non-removable row.
                if (current.mimeType == ContactDataMimeType.NAME) {
                    holder.removeButton.visibility = View.INVISIBLE
                    holder.removeButton.isEnabled = false
                    holder.removeButton.isClickable = false
                } else {
                    holder.removeButton.visibility = View.VISIBLE
                    holder.removeButton.isEnabled = true
                    holder.removeButton.isClickable = true
                }

                holder.editLayout.hint = getEditHint(current.mimeType, current.columnInfo?.second)
                holder.editText.inputType = getInputType(current.mimeType)
                holder.editText.setText(current.value)

                Timber.i("$DBL: onBindViewHolder() - currentFocusedItemID = $currentFocusedItemID, itemID = ${current.longUUID}")

                if (current.longUUID == currentFocusedItemID) {
                    Timber.e("$DBL: requesting focus - ${current.longUUID}")
                    holder.editText.requestFocus()
                } else {
                    holder.editText.clearFocus()
                }
            }
            is FooterViewHolder -> {}
            is DeleterViewHolder -> {}
            else -> {
                throw Exception("Bad Holder / View Type")
            }
        }
    }

    /**
     * TODO: Could the following situation exist when scrolling fast?
     *  textChange -> view detach -> view attach -> ui map add key (from attach)
     *  -> ui map remove key (from detach) -> textChange
     *  -
     *  If it's not a big deal, then maybe we don't need to put in safety measure.
     */
    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)

        // This is only for EditViewHolders
        if (holder !is EditViewHolder) return

        val position = holder.adapterPosition
        val current = getItem(position) as ContactData

        /*
        This is specifically for the ViewHolders that were detached but not fully destroyed and
        came back (e.g., ViewHolders hidden by the soft input keypad). This way, the value gets set
        back to the original data value (remember, if you edit a focused item that's off the screen
        the system automatically starts editing the top visible item).
         */
        holder.editText.setText(current.value)

        // Add TextWatcher
        textWatcherMap[current.longUUID] = holder.editText.doAfterTextChanged {
            onTextChangedLambda(
                current,
                it.toString()
            )
        }
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        // This is only for EditViewHolders
        if (holder !is EditViewHolder) return

        val position = holder.adapterPosition

        // The main case where position would be NO_POSITION is when we remove a ContactData.
        if (position != RecyclerView.NO_POSITION) {
            val current = getItem(position)
            val textWatcher = textWatcherMap[current.longUUID]

            // Remove TextWatcher from both View and map.
            holder.editText.removeTextChangedListener(textWatcher)
            textWatcherMap.remove(current.longUUID)
        }
    }

    companion object {
        fun getEditHint(mimeType: ContactDataMimeType, column: String?) : String {
            return when (mimeType) {
                ContactDataMimeType.NAME -> when (column) {
                    ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME -> "First Name"
                    ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME -> "Last Name"
                    else -> "Bad Name"
                }
                ContactDataMimeType.PHONE -> "Phone Number"
                ContactDataMimeType.EMAIL -> "Email"
                ContactDataMimeType.ADDRESS -> "Address"
            }
        }

        fun getInputType(mimeType: ContactDataMimeType) : Int {
            return when (mimeType) {
                ContactDataMimeType.NAME -> InputType.TYPE_CLASS_TEXT
                ContactDataMimeType.PHONE -> InputType.TYPE_CLASS_PHONE
                ContactDataMimeType.EMAIL -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                ContactDataMimeType.ADDRESS -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS
            }
        }

        /**
         * Returns whether the given [text] is valid for the given [inputType].
         *
         * NOTE: This is mostly used for the non-focused item edit, so the email regex doesn't have
         * to be perfect.
         */
        fun isValidForInputType(inputType: Int, text: String): Boolean {
            return when (inputType) {
                InputType.TYPE_CLASS_TEXT -> true // any character is valid
                InputType.TYPE_CLASS_PHONE -> text.all { it.isDigit() || it == '+' || it == '-' }
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS -> text.matches(
                    // use a regex to validate email
                    Regex("^[\\w-]+(\\.[\\w-]+)*@[\\w-]+(\\.[\\w-]+)*(\\.[a-zA-Z]{2,})$")
                )
                InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS -> true // any character is valid
                else -> false
            }
        }
    }
}