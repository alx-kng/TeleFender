package com.telefender.phone.gui.adapters.recycler_view_items

import java.util.Comparator


/***************************************************************************************************
 * For ContactsFragment / ContactsAdapter
 **************************************************************************************************/

sealed class BaseContactItem(
    open val adjustedFirstChar: Char,
)

object BaseContactItemComparator : Comparator<BaseContactItem> {
    override fun compare (o1: BaseContactItem, o2: BaseContactItem) : Int {
        // Only compare by BaseContactItem or finer when first letters (uppercase) are the same.
        if (o1.adjustedFirstChar != o2.adjustedFirstChar) {
            return o1.adjustedFirstChar - o2.adjustedFirstChar
        } else {
            // BaseDivider always goes first.
            if (o1 is BaseDivider) return -1
            if (o2 is BaseDivider) return 1

            // BaseFooter always goes last.
            if (o1 is BaseFooter) return 1
            if (o2 is BaseFooter) return -1

            // If the code reaches here, then o1 and o2 must be AggregateContacts. Compare Strings.
            return (o1 as AggregateContact).name.compareTo((o2 as AggregateContact).name)
        }
    }
}

object BaseFooter : BaseContactItem(adjustedFirstChar = Char.MAX_VALUE)

data class BaseDivider(
    override val adjustedFirstChar: Char,
    val text: String = getBaseDividerText(adjustedFirstChar)
) : BaseContactItem(adjustedFirstChar) {

    override fun toString(): String {
        return "BaseDivider: Letter = $adjustedFirstChar"
    }

    override fun equals(other: Any?): Boolean {
        return other is BaseDivider && this.adjustedFirstChar == other.adjustedFirstChar
    }

    override fun hashCode(): Int {
        return adjustedFirstChar.hashCode()
    }
}

/**
 * Chars that won't be lumped under the misc contacts are described here.
 */
enum class DividerCharType(val range: CharRange) {
    POUND('#'..'#'),
    ALPHABET('A'..'Z')
}

data class AggregateContact(
    val name : String,
    val aggregateID: Int,
    override val adjustedFirstChar: Char = getAdjustedFirstChar(name),
) : BaseContactItem(adjustedFirstChar) {

    override fun toString(): String {
        return "ContactData: Name = $name, aggregateID = $aggregateID"
    }

    override fun equals(other: Any?): Boolean {
        return other is AggregateContact
            && this.name == other.name
            && this.aggregateID == other.aggregateID
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + aggregateID
        return result
    }
}

/**
 * Returns the first non-whitespace char adjusted to the [DividerCharType]. That is, if the first
 * char isn't in [DividerCharType], then the adjusted first char is [Char.MAX_VALUE]. This way,
 * it's easier to lump all non [DividerCharType] under the misc contacts.
 */
fun getAdjustedFirstChar(name: String) : Char {
    val firstNonWhiteSpace = name.firstOrNull { !it.isWhitespace() }
        ?.uppercaseChar()
        ?: Char.MAX_VALUE

    for (charType in DividerCharType.values()) {
        if (firstNonWhiteSpace in charType.range) {
            return firstNonWhiteSpace
        }
    }
    return Char.MAX_VALUE
}

/**
 * Returns the BaseDivider text (title) given the adjustedFirstChar.
 */
fun getBaseDividerText(adjustedFirstChar: Char) : String {
    return if (adjustedFirstChar == Char.MAX_VALUE) {
        "Misc"
    } else {
        adjustedFirstChar.toString()
    }
}