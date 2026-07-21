package com.kotlinorm.example.android

import com.kotlinorm.annotations.ColumnType
import com.kotlinorm.annotations.Default
import com.kotlinorm.annotations.PrimaryKey
import com.kotlinorm.annotations.Table
import com.kotlinorm.enums.KColumnType.VARCHAR
import com.kotlinorm.interfaces.KPojo

@Table("markdown_documents")
data class MarkdownDocument(
    @PrimaryKey(identity = true)
    var id: Long? = null,
    @ColumnType(VARCHAR, length = 160)
    var title: String? = null,
    @ColumnType(VARCHAR, length = 65_535)
    var content: String? = null,
    @Default("0")
    var favorite: Boolean? = false,
    @Default("0")
    var createdAt: Long? = 0L,
    @Default("0")
    var updatedAt: Long? = 0L
) : KPojo
