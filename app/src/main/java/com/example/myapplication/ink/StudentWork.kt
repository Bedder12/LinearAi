package com.example.myapplication.ink

data class StudentWork(
    val document: InkDocument = InkDocument(),
    val revision: Long = 0L
) {
    fun clear(): StudentWork {
        return copy(
            document = document.clear(),
            revision = revision + 1
        )
    }
    fun updateDocument(
        updatedDocument: InkDocument
    ): StudentWork {
        return copy(
            document = updatedDocument,
            revision = revision + 1
        )
    }
}
