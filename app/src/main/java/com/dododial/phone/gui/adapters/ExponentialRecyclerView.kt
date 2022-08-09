package com.dododial.phone.gui.adapters

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView

// TODO implement exponential scrolling
/**
 * To be used for exponential scrolling in a RecyclerView.
 */
class ExponentialRecyclerView(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context) : this(context, null, 0)

}