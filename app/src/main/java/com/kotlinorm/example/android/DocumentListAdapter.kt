package com.kotlinorm.example.android

import android.graphics.Color
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.TextView

class DocumentListAdapter(
    private val inflater: LayoutInflater,
    private val onOpen: (MarkdownDocument) -> Unit,
    private val onFavoriteChanged: (MarkdownDocument, Boolean) -> Unit
) : BaseAdapter() {
    private val documents = mutableListOf<MarkdownDocument>()

    fun replace(items: List<MarkdownDocument>) {
        documents.clear()
        documents.addAll(items)
        notifyDataSetChanged()
    }

    override fun getCount(): Int = documents.size
    override fun getItem(position: Int): MarkdownDocument = documents[position]
    override fun getItemId(position: Int): Long = documents[position].id ?: position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.document_row, parent, false)
        val document = getItem(position)
        val content = document.content.orEmpty()
        val timestamp = document.updatedAt ?: 0L

        view.findViewById<TextView>(R.id.documentTitle).text = document.title.orEmpty()
        view.findViewById<TextView>(R.id.documentExcerpt).text = excerpt(content)
        view.findViewById<TextView>(R.id.documentMeta).text = view.resources.getString(
            R.string.document_meta,
            relativeTime(timestamp),
            content.length
        )

        view.findViewById<ImageButton>(R.id.favoriteButton).apply {
            val favorite = document.favorite == true
            setImageResource(
                if (favorite) android.R.drawable.btn_star_big_on
                else android.R.drawable.btn_star_big_off
            )
            setColorFilter(if (favorite) view.context.getColor(R.color.gold) else Color.GRAY)
            contentDescription = view.resources.getString(
                if (favorite) R.string.remove_favorite else R.string.add_favorite
            )
            setOnClickListener { onFavoriteChanged(document, !favorite) }
        }
        view.setOnClickListener { onOpen(document) }
        return view
    }

    private fun excerpt(content: String): String = content
        .lineSequence()
        .map(String::trim)
        .firstOrNull { it.isNotEmpty() }
        ?.take(140)
        ?: "Empty document"

    private fun relativeTime(timestamp: Long): CharSequence = when {
        timestamp <= 0L -> "Just now"
        else -> DateUtils.getRelativeTimeSpanString(
            timestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        )
    }
}
