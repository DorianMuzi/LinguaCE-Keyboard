package com.linguace.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/**
 * Écran d'accueil : activer le clavier, le choisir, l'essayer, puis les
 * réglages rangés en quatre sections (Disposition, Apparence, Saisie,
 * Confidentialité).
 */
class SetupActivity : Activity() {

    private lateinit var dictionaryButton: Button
    private lateinit var enableButton: Button
    private lateinit var chooseButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = dp(24)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        fun text(s: CharSequence, size: Float) = TextView(this).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            setPadding(0, 0, 0, dp(16))
        }
        fun text(res: Int, size: Float) = text(getText(res), size)

        /** Titre de section, dans la couleur d'accent du clavier. */
        fun section(res: Int) = column.addView(TextView(this).apply {
            setText(res)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ACCENT)
            letterSpacing = 0.04f
            setPadding(0, dp(28), 0, dp(4))
        })

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

        // Une phrase d'abord ; les astuces (les puces) se déplient à la demande.
        val intro = getString(R.string.setup_intro).split("\n\n", limit = 2)
        column.addView(text(intro[0], 16f))
        if (intro.size > 1) {
            val tips = text(intro[1], 15f).apply { visibility = View.GONE }
            val label = getString(R.string.setup_tips)
            column.addView(TextView(this).apply {
                this.text = "$label ▾"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(ACCENT)
                setTypeface(typeface, Typeface.BOLD)
                minHeight = dp(48)
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener {
                    val open = tips.visibility != View.VISIBLE
                    tips.visibility = if (open) View.VISIBLE else View.GONE
                    this.text = "$label ${if (open) "▴" else "▾"}"
                }
            })
            column.addView(tips)
        }

        enableButton = Button(this).apply {
            setText(R.string.setup_enable)
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        }
        chooseButton = Button(this).apply {
            setText(R.string.setup_choose)
            setOnClickListener {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showInputMethodPicker()
            }
        }
        column.addView(enableButton)
        column.addView(chooseButton)
        column.addView(EditText(this).apply {
            setHint(R.string.setup_try)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(16) })

        val prefs = getSharedPreferences("keyboard", Context.MODE_PRIVATE)

        // Première ouverture : l'accueil guidé passe devant. Un lien en bas de
        // page permet de le revoir.
        if (savedInstanceState == null && !prefs.getBoolean(OnboardingActivity.ONBOARDED_KEY, false)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }

        /**
         * Un réglage à choix unique : titre puis boutons radio. Deux choix
         * tiennent sur une ligne ; trois se mettent l'un sous l'autre (les
         * libellés russes sont longs).
         */
        fun choice(title: Int, key: String, current: String, options: List<Pair<CharSequence, String>>) {
            column.addView(text(title, 16f).apply { setPadding(0, dp(12), 0, dp(4)) })
            column.addView(RadioGroup(this).apply {
                orientation = if (options.size > 2) RadioGroup.VERTICAL else RadioGroup.HORIZONTAL
                for ((label, value) in options) {
                    addView(RadioButton(this@SetupActivity).apply {
                        id = View.generateViewId()
                        text = label
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                        minHeight = dp(48)
                        setPadding(dp(4), 0, dp(24), 0)
                        isChecked = value == current
                        setOnCheckedChangeListener { _, on ->
                            if (on) prefs.edit().putString(key, value).apply()
                        }
                    })
                }
            })
        }

        fun toggle(label: Int, key: String, default: Boolean) = Switch(this).apply {
            setText(label)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            isChecked = prefs.getBoolean(key, default)
            minHeight = dp(48)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(key, on).apply() }
        }

        // ─── Disposition ───
        section(R.string.section_layout)
        choice(
            R.string.set_latin_layout, LinguaImeService.LATIN_KEY,
            if (LinguaImeService.isAzerty(this)) "azerty" else "qwerty",
            listOf("QWERTY" to "qwerty", "AZERTY" to "azerty"),
        )

        // ─── Apparence ───
        section(R.string.section_appearance)
        choice(
            R.string.set_theme, LinguaImeService.THEME_KEY,
            Theme.of(prefs.getString(LinguaImeService.THEME_KEY, null)).name.lowercase(),
            listOf(
                getText(R.string.set_theme_system) to "system",
                getText(R.string.set_theme_light) to "light",
                getText(R.string.set_theme_dark) to "dark",
            ),
        )
        choice(
            R.string.set_key_height, LinguaImeService.HEIGHT_KEY,
            KeyHeight.of(prefs.getString(LinguaImeService.HEIGHT_KEY, null)).name.lowercase(),
            listOf(
                getText(R.string.set_height_small) to "small",
                getText(R.string.set_height_normal) to "normal",
                getText(R.string.set_height_large) to "large",
            ),
        )
        column.addView(
            toggle(R.string.set_toolbar, LinguaImeService.TOOLBAR_KEY, true),
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) },
        )

        // ─── Saisie ───
        section(R.string.section_typing)
        column.addView(toggle(R.string.set_sound, LinguaImeService.SOUND_KEY, false))
        column.addView(toggle(R.string.set_vibrate, LinguaImeService.VIBRATE_KEY, true))
        column.addView(toggle(R.string.set_autocaps, LinguaImeService.AUTOCAPS_KEY, true))
        column.addView(toggle(R.string.set_double_space, LinguaImeService.DOUBLE_SPACE_KEY, true))

        // ─── Confidentialité ───
        // Les mots appris restent sur le téléphone. Le dictionnaire personnel
        // permet de les voir, les supprimer un par un, en ajouter, les
        // proposer au projet ou tout effacer.
        section(R.string.section_privacy)
        column.addView(text(R.string.setup_privacy, 14f).apply { setPadding(0, dp(8), 0, dp(8)) })
        column.addView(text(R.string.setup_learning, 14f))
        dictionaryButton = Button(this).apply {
            setOnClickListener {
                startActivity(Intent(this@SetupActivity, DictionaryActivity::class.java))
            }
        }
        column.addView(dictionaryButton, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        column.addView(TextView(this).apply {
            setText(R.string.ob_replay)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(ACCENT)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(48)
            setOnClickListener { startActivity(Intent(this@SetupActivity, OnboardingActivity::class.java)) }
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) })

        val scroll = ScrollView(this).apply {
            addView(column)
            clipToPadding = false
            padForSystemBars()
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        // Le nombre de mots a pu changer dans le dictionnaire.
        dictionaryButton.text =
            getString(R.string.setup_dictionary, LinguaImeService.learnedCount(this))
        refreshSteps()
    }

    /** « ✓ » devant l'étape déjà faite (clavier activé / choisi comme clavier). */
    private fun refreshSteps() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val chosen = current?.startsWith("$packageName/") == true
        enableButton.text = (if (enabled) "✓  " else "") + getString(R.string.setup_enable)
        chooseButton.text = (if (chosen) "✓  " else "") + getString(R.string.setup_choose)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private companion object {
        const val ACCENT = 0xFF1E88E5.toInt()
    }
}
