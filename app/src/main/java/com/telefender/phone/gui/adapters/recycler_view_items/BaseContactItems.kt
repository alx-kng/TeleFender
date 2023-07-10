package com.telefender.phone.gui.adapters.recycler_view_items


/***************************************************************************************************
 * For ContactsFragment / ContactsAdapter
 **************************************************************************************************/
sealed interface ContactItem

object ContactFooter : ContactItem

data class Divider(
    val letter: String
) : ContactItem {
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

data class ContactDetail(
    val name : String,
    val id: Int
) : ContactItem {

    override fun toString(): String {
        return "ContactData: Name = $name, ID = $id"
    }

    override fun equals(other: Any?): Boolean {
        return other is ContactDetail && this.name == other.name && this.id == other.id
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + id
        return result
    }
}