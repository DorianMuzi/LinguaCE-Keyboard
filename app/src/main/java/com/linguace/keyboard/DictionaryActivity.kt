package com.linguace.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/**
 * Dictionnaire personnel, comme celui de Gboard : les mots appris par le
 * clavier (avec leur nombre d'emplois), à supprimer un par un ; et les mots
 * que l'utilisateur ajoute lui-même (prénoms, lieux, mots de famille…).
 * Un seul champ sert à chercher dans la liste et à ajouter un mot.
 */
class DictionaryActivity : Activity() {

    private var all: List<Pair<String, Int>> = emptyList()
    private var shown: List<Pair<String, Int>> = emptyList()
    private lateinit var field: EditText
    private lateinit var empty: TextView
    private lateinit var clearAll: Button
    private val adapter = WordsAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = dp(20)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            setText(R.string.dict_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            setText(R.string.dict_intro)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(8), 0, dp(12))
        })

        // Chercher / ajouter.
        val addRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        field = EditText(this).apply {
            setHint(R.string.dict_add_hint)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { _, _, _ -> addWord(); true }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) = filter()
            })
        }
        addRow.addView(field, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        addRow.addView(Button(this).apply {
            setText(R.string.dict_add)
            minHeight = dp(48)
            setOnClickListener { addWord() }
        })
        root.addView(addRow)

        empty = TextView(this).apply {
            setText(R.string.dict_empty)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            setPadding(0, dp(32), 0, dp(32))
        }
        root.addView(empty, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(ListView(this).apply { adapter = this@DictionaryActivity.adapter },
            LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        // Proposer ses mots au projet, puis tout effacer.
        root.addView(TextView(this).apply {
            setText(R.string.share_explain)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(12), 0, dp(4))
        })
        root.addView(Button(this).apply {
            setText(R.string.share_button)
            setOnClickListener { shareWords() }
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        clearAll = Button(this).apply {
            setText(R.string.dict_clear_all)
            setOnClickListener { confirmClearAll() }
        }
        root.addView(clearAll, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        root.padForSystemBars()
        setContentView(root)
        reload()
    }

    private fun reload() {
        all = LinguaImeService.learnedEntries(this)
        filter()
    }

    /** Filtre la liste selon le champ, accents ignorés (« noxc » → noxçiyŋ). */
    private fun filter() {
        val q = Fold.fold(field.text.toString().trim())
        shown = if (q.isEmpty()) all else all.filter { Fold.fold(it.first).contains(q) }
        adapter.notifyDataSetChanged()
        empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        clearAll.isEnabled = all.isNotEmpty()
    }

    private fun addWord() {
        val word = field.text.toString().trim()
        if (word.isEmpty()) return
        var ok = false
        LinguaImeService.editLearned(this) { ok = it.add(word) }
        if (!ok) {
            Toast.makeText(this, R.string.dict_invalid, Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, getString(R.string.dict_added, word), Toast.LENGTH_SHORT).show()
        field.text.clear()
        reload()
    }

    private fun removeWord(word: String) {
        LinguaImeService.editLearned(this) { it.remove(word) }
        reload()
    }

    private fun confirmClearAll() {
        AlertDialog.Builder(this)
            .setMessage(R.string.dict_clear_confirm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.dict_clear_all) { _, _ ->
                LinguaImeService.clearLearned(this)
                Toast.makeText(this, R.string.setup_cleared, Toast.LENGTH_SHORT).show()
                reload()
            }
            .show()
    }

    // ─── Proposer ses mots (déplacé depuis l'écran d'installation) ───
    /**
     * Liste des mots appris (aucun coché d'avance : ils peuvent contenir des
     * prénoms ou des mots privés), puis menu de partage d'Android avec un
     * e-mail pré-rempli. Le clavier n'envoie rien lui-même.
     */
    private fun shareWords() {
        val words = LinguaImeService.newWords(this)
        if (words.isEmpty()) {
            Toast.makeText(this, R.string.share_none, Toast.LENGTH_LONG).show()
            return
        }
        val checked = BooleanArray(words.size)
        AlertDialog.Builder(this)
            .setTitle(R.string.share_title)
            .setMultiChoiceItems(words.toTypedArray(), checked) { _, i, on -> checked[i] = on }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.share_send) { _, _ ->
                val chosen = words.filterIndexed { i, _ -> checked[i] }
                if (chosen.isNotEmpty()) sendWords(chosen)
            }
            .show()
    }

    private fun sendWords(words: List<String>) {
        val body = getString(R.string.share_intro) + "\n\n" + words.joinToString("\n") +
            "\n\n— LinguaCE Keyboard " +
            runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(PROJECT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject))
            putExtra(Intent.EXTRA_TEXT, body)
        }
        startActivity(Intent.createChooser(send, getString(R.string.share_button)))
    }

    /** Une ligne : le mot, son nombre d'emplois, le bouton de suppression. */
    private inner class WordsAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val (word, uses) = shown[position]
            val row = LinearLayout(this@DictionaryActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(52)
            }
            row.addView(TextView(this@DictionaryActivity).apply {
                text = word
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            row.addView(TextView(this@DictionaryActivity).apply {
                text = getString(R.string.dict_uses, uses)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                alpha = 0.6f
                setPadding(dp(8), 0, dp(8), 0)
            })
            row.addView(ImageButton(this@DictionaryActivity).apply {
                setImageResource(android.R.drawable.ic_menu_delete)
                background = null
                contentDescription = getString(R.string.dict_delete, word)
                setOnClickListener { removeWord(word) }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            return row
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        /** Adresse du projet, choisie par l'auteur (2026-11-21). */
        const val PROJECT_EMAIL = "linguachechen@gmail.com"
    }
}
