package com.kotlinorm.example.android

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class EditorActivity : Activity() {
    private val io: ExecutorService = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val repository = DocumentRepository()

    private lateinit var titleInput: EditText
    private lateinit var markdownInput: EditText
    private lateinit var markdownPreview: TextView
    private lateinit var previewScroll: ScrollView
    private lateinit var formatBar: HorizontalScrollView
    private lateinit var editMode: TextView
    private lateinit var previewMode: TextView
    private lateinit var favoriteButton: ImageButton
    private lateinit var deleteButton: ImageButton
    private lateinit var saveButton: ImageButton
    private lateinit var editorStatus: TextView
    private lateinit var markwon: Markwon

    private var document: MarkdownDocument? = null
    private var documentId: Long? = null
    private var favorite = false
    private var dirty = false
    private var suppressChanges = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)

        titleInput = findViewById(R.id.titleInput)
        markdownInput = findViewById(R.id.markdownInput)
        markdownPreview = findViewById(R.id.markdownPreview)
        previewScroll = findViewById(R.id.previewScroll)
        formatBar = findViewById(R.id.formatBar)
        editMode = findViewById(R.id.modeEdit)
        previewMode = findViewById(R.id.modePreview)
        favoriteButton = findViewById(R.id.editorFavoriteButton)
        deleteButton = findViewById(R.id.editorDeleteButton)
        saveButton = findViewById(R.id.editorSaveButton)
        editorStatus = findViewById(R.id.editorStatus)

        markwon = Markwon.builder(this)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(this))
            .build()

        documentId = intent.getLongExtra(EXTRA_DOCUMENT_ID, NO_DOCUMENT_ID)
            .takeUnless { it == NO_DOCUMENT_ID }

        bindActions()
        bindChangeTracking()
        showEditor()

        if (documentId == null) {
            deleteButton.visibility = View.GONE
            updateFavoriteIcon()
            updateStatus()
            titleInput.requestFocus()
        } else {
            loadDocument(requireNotNull(documentId))
        }
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!dirty) {
            super.onBackPressed()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.unsaved_changes)
            .setMessage(R.string.unsaved_changes_message)
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.discard) { _, _ -> finish() }
            .setPositiveButton(R.string.save) { _, _ -> saveDocument(finishAfterSave = true) }
            .show()
    }

    private fun bindActions() {
        findViewById<ImageButton>(R.id.editorBackButton).setOnClickListener { onBackPressed() }
        saveButton.setOnClickListener { saveDocument() }
        deleteButton.setOnClickListener { confirmDelete() }
        favoriteButton.setOnClickListener {
            favorite = !favorite
            dirty = true
            updateFavoriteIcon()
            updateStatus()
        }
        editMode.setOnClickListener { showEditor() }
        previewMode.setOnClickListener { showPreview() }

        findViewById<View>(R.id.formatHeading).setOnClickListener { prefixCurrentLine("# ") }
        findViewById<View>(R.id.formatBold).setOnClickListener { wrapSelection("**", "**", "bold text") }
        findViewById<View>(R.id.formatItalic).setOnClickListener { wrapSelection("_", "_", "italic text") }
        findViewById<View>(R.id.formatBullet).setOnClickListener { prefixCurrentLine("- ") }
        findViewById<View>(R.id.formatNumbered).setOnClickListener { prefixCurrentLine("1. ") }
        findViewById<View>(R.id.formatQuote).setOnClickListener { prefixCurrentLine("> ") }
        findViewById<View>(R.id.formatCode).setOnClickListener { wrapSelection("`", "`", "code") }
        findViewById<View>(R.id.formatLink).setOnClickListener {
            wrapSelection("[", "](https://example.com)", "link text")
        }
    }

    private fun bindChangeTracking() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!suppressChanges) {
                    dirty = true
                    updateStatus()
                }
            }

            override fun afterTextChanged(s: Editable?) = Unit
        }
        titleInput.addTextChangedListener(watcher)
        markdownInput.addTextChangedListener(watcher)
    }

    private fun loadDocument(id: Long) {
        setBusy(true, getString(R.string.loading_document))
        runDatabase(
            action = { repository.find(id) ?: error("Document not found") },
            onSuccess = { loaded ->
                document = loaded
                favorite = loaded.favorite == true
                suppressChanges = true
                titleInput.setText(loaded.title.orEmpty())
                markdownInput.setText(loaded.content.orEmpty())
                markdownInput.setSelection(markdownInput.text.length)
                suppressChanges = false
                dirty = false
                deleteButton.visibility = View.VISIBLE
                updateFavoriteIcon()
                setBusy(false)
                updateStatus()
            }
        )
    }

    private fun saveDocument(finishAfterSave: Boolean = false) {
        val content = markdownInput.text.toString()
        val title = resolvedTitle(titleInput.text.toString(), content)
        val current = document

        setBusy(true, getString(R.string.saving_document))
        runDatabase(
            action = {
                if (current == null) {
                    repository.create(title, content, favorite)
                } else {
                    repository.update(
                        current.copy(
                            title = title,
                            content = content,
                            favorite = favorite
                        )
                    )
                }
            },
            onSuccess = { saved ->
                document = saved
                documentId = saved.id
                suppressChanges = true
                titleInput.setText(saved.title.orEmpty())
                titleInput.setSelection(titleInput.text.length)
                suppressChanges = false
                dirty = false
                deleteButton.visibility = View.VISIBLE
                setBusy(false)
                updateStatus(saved = true)
                setResult(RESULT_OK)
                if (finishAfterSave) finish()
            }
        )
    }

    private fun confirmDelete() {
        val id = document?.id ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_document)
            .setMessage(R.string.delete_document_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                setBusy(true, getString(R.string.deleting_document))
                runDatabase(
                    action = { repository.delete(id) },
                    onSuccess = {
                        setResult(RESULT_OK)
                        finish()
                    }
                )
            }
            .show()
    }

    private fun showEditor() {
        editMode.isSelected = true
        previewMode.isSelected = false
        formatBar.visibility = View.VISIBLE
        markdownInput.visibility = View.VISIBLE
        previewScroll.visibility = View.GONE
    }

    private fun showPreview() {
        editMode.isSelected = false
        previewMode.isSelected = true
        formatBar.visibility = View.GONE
        markdownInput.visibility = View.GONE
        previewScroll.visibility = View.VISIBLE
        val source = markdownInput.text.toString().ifBlank { getString(R.string.empty_preview_markdown) }
        markwon.setMarkdown(markdownPreview, source)
    }

    private fun updateFavoriteIcon() {
        favoriteButton.setImageResource(
            if (favorite) android.R.drawable.btn_star_big_on
            else android.R.drawable.btn_star_big_off
        )
        favoriteButton.setColorFilter(if (favorite) getColor(R.color.gold) else Color.GRAY)
        favoriteButton.contentDescription = getString(
            if (favorite) R.string.remove_favorite else R.string.add_favorite
        )
    }

    private fun updateStatus(saved: Boolean = false) {
        val characterCount = markdownInput.text?.length ?: 0
        editorStatus.text = when {
            saved -> getString(R.string.saved_status, characterCount)
            dirty -> getString(R.string.unsaved_status, characterCount)
            else -> getString(R.string.editor_ready_status, characterCount)
        }
    }

    private fun setBusy(busy: Boolean, message: String? = null) {
        saveButton.isEnabled = !busy
        deleteButton.isEnabled = !busy
        favoriteButton.isEnabled = !busy
        titleInput.isEnabled = !busy
        markdownInput.isEnabled = !busy
        if (message != null) editorStatus.text = message
    }

    private fun wrapSelection(open: String, close: String, placeholder: String) {
        val editable = markdownInput.text
        val start = markdownInput.selectionStart.coerceAtLeast(0)
        val end = markdownInput.selectionEnd.coerceAtLeast(start)
        val selected = editable.substring(start, end).ifEmpty { placeholder }
        val replacement = "$open$selected$close"
        editable.replace(start, end, replacement)
        val selectionStart = start + open.length
        markdownInput.setSelection(selectionStart, selectionStart + selected.length)
        markdownInput.requestFocus()
    }

    private fun prefixCurrentLine(prefix: String) {
        val editable = markdownInput.text
        val cursor = markdownInput.selectionStart.coerceAtLeast(0)
        val lineStart = editable.toString().lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)) + 1
        editable.insert(lineStart, prefix)
        markdownInput.setSelection((cursor + prefix.length).coerceAtMost(editable.length))
        markdownInput.requestFocus()
    }

    private fun resolvedTitle(typedTitle: String, content: String): String {
        if (typedTitle.isNotBlank()) return typedTitle.trim().take(160)
        val firstLine = content.lineSequence()
            .map(String::trim)
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
            .trimStart('#', '>', '-', '*', ' ')
        return firstLine.take(160).ifBlank { getString(R.string.untitled_document) }
    }

    private fun <T> runDatabase(action: () -> T, onSuccess: (T) -> Unit) {
        io.execute {
            runCatching(action)
                .onSuccess { value -> main.post { if (!isFinishing) onSuccess(value) } }
                .onFailure { error ->
                    Log.e(TAG, "Database operation failed", error)
                    main.post {
                        if (isFinishing) return@post
                        setBusy(false)
                        editorStatus.text = error.message ?: getString(R.string.database_error)
                        Toast.makeText(this, editorStatus.text, Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    companion object {
        const val EXTRA_DOCUMENT_ID = "document_id"
        private const val NO_DOCUMENT_ID = -1L
        private const val TAG = "MarkdownStudio"
    }
}
