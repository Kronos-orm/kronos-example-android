package com.kotlinorm.example.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val io: ExecutorService = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val repository = DocumentRepository()

    private lateinit var adapter: DocumentListAdapter
    private lateinit var searchInput: EditText
    private lateinit var allFilter: TextView
    private lateinit var favoriteFilter: TextView
    private lateinit var summary: TextView
    private lateinit var emptyState: View
    private lateinit var status: TextView

    private var documents: List<MarkdownDocument> = emptyList()
    private var showFavoritesOnly = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        searchInput = findViewById(R.id.searchInput)
        allFilter = findViewById(R.id.filterAll)
        favoriteFilter = findViewById(R.id.filterFavorites)
        summary = findViewById(R.id.librarySummary)
        emptyState = findViewById(R.id.emptyState)
        status = findViewById(R.id.statusText)

        adapter = DocumentListAdapter(
            inflater = layoutInflater,
            onOpen = ::openDocument,
            onFavoriteChanged = ::setFavorite
        )
        findViewById<ListView>(R.id.documentList).adapter = adapter

        findViewById<ImageButton>(R.id.newDocumentButton).setOnClickListener {
            startActivity(Intent(this, EditorActivity::class.java))
        }
        allFilter.setOnClickListener {
            showFavoritesOnly = false
            updateFilterSelection()
            applyFilters()
        }
        favoriteFilter.setOnClickListener {
            showFavoritesOnly = true
            updateFilterSelection()
            applyFilters()
        }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = applyFilters()
            override fun afterTextChanged(s: Editable?) = Unit
        })

        updateFilterSelection()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    private fun refresh() {
        status.text = getString(R.string.loading_documents)
        runDatabase(
            action = { repository.all() },
            onSuccess = {
                documents = it
                applyFilters()
                status.text = getString(R.string.storage_status)
            }
        )
    }

    private fun applyFilters() {
        if (!::adapter.isInitialized) return
        val query = searchInput.text?.toString().orEmpty().trim()
        val visible = documents.filter { document ->
            val matchesFavorite = !showFavoritesOnly || document.favorite == true
            val matchesQuery = query.isEmpty() ||
                document.title.orEmpty().contains(query, ignoreCase = true) ||
                document.content.orEmpty().contains(query, ignoreCase = true)
            matchesFavorite && matchesQuery
        }

        adapter.replace(visible)
        summary.text = resources.getQuantityString(
            R.plurals.document_count,
            documents.size,
            documents.size,
            documents.count { it.favorite == true }
        )
        emptyState.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateFilterSelection() {
        allFilter.isSelected = !showFavoritesOnly
        favoriteFilter.isSelected = showFavoritesOnly
    }

    private fun openDocument(document: MarkdownDocument) {
        startActivity(
            Intent(this, EditorActivity::class.java)
                .putExtra(EditorActivity.EXTRA_DOCUMENT_ID, document.id)
        )
    }

    private fun setFavorite(document: MarkdownDocument, favorite: Boolean) {
        runDatabase(
            action = { repository.setFavorite(document, favorite) },
            onSuccess = { refresh() }
        )
    }

    private fun <T> runDatabase(action: () -> T, onSuccess: (T) -> Unit) {
        io.execute {
            runCatching {
                (application as KronosExampleApp).awaitSchemaReady()
                action()
            }
                .onSuccess { value -> main.post { if (!isFinishing) onSuccess(value) } }
                .onFailure { error ->
                    Log.e(TAG, "Database operation failed", error)
                    main.post {
                        if (isFinishing) return@post
                        status.text = error.message ?: getString(R.string.database_error)
                        Toast.makeText(this, status.text, Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    companion object {
        private const val TAG = "MarkdownStudio"
    }
}
