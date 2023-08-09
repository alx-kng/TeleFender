package com.telefender.phone.gui.adapters.custom_views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import com.google.android.material.textfield.TextInputEditText


class CustomTextInputEditText : TextInputEditText {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

    /**
     * onTouchEvent gets called whenever a touch event is detected on the view.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        super.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                // Calls the performClick() method on 'release' part of the click event
                performClick()
                // Calls the performClick() method on 'release' part of the click event
                return true
            }
        }

        // If the event is not ACTION_DOWN or ACTION_UP, it doesn't consume the event
        return false
    }

    /**
     * performClick() gets called when the view is clicked or when the system calls this via the Accessibility
     * Services. Since we are calling this from onTouchEvent, it will be triggered both when the user
     * physically touches the view and when a click is simulated via accessibility features
     */
    override fun performClick(): Boolean {
        super.performClick()
        // Consumes the click event
        return true
    }
}
