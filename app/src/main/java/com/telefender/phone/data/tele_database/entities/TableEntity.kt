package com.telefender.phone.data.tele_database.entities

abstract class TableEntity {
    abstract fun toJson() : String
}

enum class TableType(val serverString: String) {
    CHANGE("change"),
    EXECUTE("execute"),
    UPLOAD_CHANGE("upload_change"),
    UPLOAD_ANALYZED("upload_analyzed"),
    ERROR("error"),
    STORED_MAP("stored_map"),
    PARAMETERS("parameters"),
    CALL_DETAIL("call_detail"),
    INSTANCE("instance"),
    CONTACT("contact"),
    CONTACT_NUMBER("contact_number"),
    ANALYZED("analyzed"),
    NOTIFY_ITEM("notify_item")
}

/**
 * Converts serverStr to ChangeType if possible.
 */
fun String.toTableType() : TableType? {
    for (tableType in TableType.values()) {
        if (this.lowercase() == tableType.serverString) {
            return tableType
        }
    }

    return null
}