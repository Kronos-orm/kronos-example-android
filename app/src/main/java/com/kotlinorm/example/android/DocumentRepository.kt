package com.kotlinorm.example.android

import com.kotlinorm.orm.delete.delete
import com.kotlinorm.orm.insert.insert
import com.kotlinorm.orm.select.select
import com.kotlinorm.orm.update.update

class DocumentRepository {
    fun all(): List<MarkdownDocument> = MarkdownDocument()
        .select()
        .orderBy { it.updatedAt.desc() }
        .toList()

    fun find(id: Long): MarkdownDocument? = MarkdownDocument()
        .select()
        .where { it.id == id }
        .firstOrNull()

    fun create(title: String, content: String, favorite: Boolean): MarkdownDocument {
        val now = System.currentTimeMillis()
        val document = MarkdownDocument(
            title = title.trim(),
            content = content,
            favorite = favorite,
            createdAt = now,
            updatedAt = now
        )
        val id = document.insert().withId().execute().lastInsertId
            ?: error("SQLite did not return the generated document id")
        return find(id) ?: error("The saved document could not be loaded")
    }

    fun update(document: MarkdownDocument): MarkdownDocument {
        val id = requireNotNull(document.id) { "Document id is required" }
        val title = document.title.orEmpty().trim()
        val content = document.content.orEmpty()
        val favorite = document.favorite == true
        val updatedAt = System.currentTimeMillis()

        MarkdownDocument(id = id)
            .update()
            .set {
                it.title = title
                it.content = content
                it.favorite = favorite
                it.updatedAt = updatedAt
            }
            .by { it.id }
            .execute()

        return find(id) ?: error("The updated document could not be loaded")
    }

    fun setFavorite(document: MarkdownDocument, favorite: Boolean) {
        MarkdownDocument(id = document.id)
            .update()
            .set {
                it.favorite = favorite
                it.updatedAt = System.currentTimeMillis()
            }
            .by { it.id }
            .execute()
    }

    fun delete(id: Long) {
        MarkdownDocument(id = id)
            .delete()
            .by { it.id }
            .execute()
    }
}
