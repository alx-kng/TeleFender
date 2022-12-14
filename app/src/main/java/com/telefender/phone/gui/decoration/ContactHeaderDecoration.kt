package com.telefender.phone.gui.decoration

import android.graphics.Canvas
import androidx.recyclerview.widget.RecyclerView
import com.telefender.phone.gui.adapters.ContactsAdapter

class ContactHeaderDecoration(val letterUpdater : (String?) -> Unit) : RecyclerView.ItemDecoration() {

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDrawOver(c, parent, state)

        // Finds the topmost visible view on the screen.
        val topView = parent.findChildViewUnder(
            parent.paddingLeft.toFloat(),
            parent.paddingTop.toFloat()
        ) ?: return

        // ViewHolder of topView
        val topHolder = parent.findContainingViewHolder(topView)

        /**
         * topLetter is the first letter if topHolder contains a contact view. Otherwise,
         * topLetter is null since topHolder must contain a divider or footer view.
         */
        val topLetter = if (topHolder is ContactsAdapter.ContactViewHolder) {
            topHolder.name.text[0].toString()
        } else {
            null
        }

        letterUpdater(topLetter)
    }
}