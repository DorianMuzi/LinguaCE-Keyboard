package com.linguace.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Écran d'installation : activer le clavier, le choisir, l'essayer. */
class SetupActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = dp(24)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        fun text(res: Int, size: Float) = TextView(this).apply {
            setText(res)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            setPadding(0, 0, 0, dp(16))
        }

        // En-tête : l'icône du clavier et son nom (pas de barre de titre système).
        column.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(20))
            addView(ImageView(this@SetupActivity).apply {
                setImageResource(R.mipmap.ic_launcher)
                contentDescription = null // décoratif : le nom suit
            }, LinearLayout.LayoutParams(dp(56), dp(56)))
            addView(TextView(this@SetupActivity).apply {
                setText(R.string.app_name)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(14), 0, 0, 0)
            })
        })

        column.addView(text(R.string.setup_title, 24f))
        column.addView(text(R.string.setup_intro, 16f))
        column.addView(Button(this).apply {
            setText(R.string.setup_enable)
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        })
        column.addView(Button(this).apply {
            setText(R.string.setup_choose)
            setOnClickListener {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showInputMethodPicker()
            }
        })
        column.addView(EditText(this).apply {
            setHint(R.string.setup_try)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(16) })
        column.addView(text(R.string.setup_privacy, 14f).apply { setPadding(0, dp(24), 0, dp(8)) })

        // Suggestions : les mots appris restent sur le téléphone, effaçables ici.
        column.addView(text(R.string.setup_learning, 14f))

        // Proposer ses mots au projet : l'utilisateur choisit, puis envoie lui-même.
        column.addView(text(R.string.share_explain, 14f).apply { setPadding(0, dp(8), 0, dp(8)) })
        column.addView(Button(this).apply {
            setText(R.string.share_button)
            setOnClickListener { shareWords() }
        })
        column.addView(Button(this).apply {
            fun refresh() {
                val n = LinguaImeService.learnedCount(this@SetupActivity)
                text = getString(R.string.setup_clear, n)
                isEnabled = n > 0
            }
            refresh()
            setOnClickListener {
                LinguaImeService.clearLearned(this@SetupActivity)
                refresh()
                android.widget.Toast.makeText(
                    this@SetupActivity, R.string.setup_cleared, android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        })

        val scroll = ScrollView(this).apply {
            addView(column)
            clipToPadding = false
            // Android 15 dessine l'app bord à bord : on écarte le contenu de la
            // barre d'état, de la barre de navigation et du clavier ouvert, sans
            // quoi le haut du texte passait sous la barre d'état.
            setOnApplyWindowInsetsListener { v, insets ->
                val top: Int
                val bottom: Int
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars())
                    val ime = insets.getInsets(WindowInsets.Type.ime())
                    top = bars.top
                    bottom = maxOf(bars.bottom, ime.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    top = insets.systemWindowInsetTop
                    @Suppress("DEPRECATION")
                    bottom = insets.systemWindowInsetBottom
                }
                v.setPadding(0, top, 0, bottom)
                insets
            }
        }
        setContentView(scroll)
    }

    /**
     * Liste des mots appris (aucun coché d'avance : ils peuvent contenir des
     * prénoms ou des mots privés), puis menu de partage d'Android avec un
     * e-mail pré-rempli. Le clavier n'envoie rien lui-même.
     */
    private fun shareWords() {
        val words = LinguaImeService.newWords(this)
        if (words.isEmpty()) {
            android.widget.Toast.makeText(this, R.string.share_none, android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val checked = BooleanArray(words.size)
        android.app.AlertDialog.Builder(this)
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

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        /** Adresse du projet, choisie par l'auteur (2026-11-21). */
        const val PROJECT_EMAIL = "linguachechen@gmail.com"
    }
}
