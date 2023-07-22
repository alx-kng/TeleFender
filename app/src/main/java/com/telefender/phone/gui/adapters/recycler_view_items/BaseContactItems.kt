package com.telefender.phone.gui.adapters.recycler_view_items


/***************************************************************************************************
 * For ContactsFragment / ContactsAdapter
 **************************************************************************************************/

sealed interface BaseContactItem

object ContactFooter : BaseContactItem

data class Divider(
    val letter: String
) : BaseContactItem {
    override fun toString(): String {
        return "Divider: Letter = $letter"
    }

    override fun equals(other: Any?): Boolean {
        return other is Divider && this.letter == other.letter
    }

    override fun hashCode(): Int {
        return letter.hashCode()
    }
}

data class AggregateContact(
    val name : String,
    val id: Int
) : BaseContactItem {

    override fun toString(): String {
        return "ContactData: Name = $name, ID = $id"
    }

    override fun equals(other: Any?): Boolean {
        return other is AggregateContact && this.name == other.name && this.id == other.id
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + id
        return result
    }
}