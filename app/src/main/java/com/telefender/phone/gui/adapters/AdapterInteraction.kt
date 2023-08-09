package com.telefender.phone.gui.adapters

/**
 * TODO: See if existing solutions are good enough, or if it's easy to integrate this generically
 *  with our current adapters.
 *
 * Interface for Fragments to implement, which allows RecyclerView adapters to have onItemClick
 * callbacks in a loosely coupled way.
 */
interface AdapterInteraction {
    fun onItemClicked(item: Any)
}