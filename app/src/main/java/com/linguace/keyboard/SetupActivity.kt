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

/** Écran d'installation : activer le clavier, le choisir, l'essayer, le régler. */
class SetupActivity : Activity() {

    private lateinit var dictionaryButton: Button

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

        // Disposition latine : QWERTY ou AZERTY (comme dans Gboard).
        val prefs = getSharedPreferences("keyboard", Context.MODE_PRIVATE)
        column.addView(text(R.string.set_latin_layout, 16f).apply { setPadding(0, dp(16), 0, dp(4)) })
        column.addView(RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            val azerty = LinguaImeService.isAzerty(this@SetupActivity)
            for ((label, value) in listOf("QWERTY" to "qwerty", "AZERTY" to "azerty")) {
                addView(RadioButton(this@SetupActivity).apply {
                    id = View.generateViewId()
                    text = label
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    minHeight = dp(48)
                    setPadding(dp(4), 0, dp(24), 0)
                    isChecked = (value == "azerty") == azerty
                    setOnCheckedChangeListener { _, on ->
                        if (on) prefs.edit().putString(LinguaImeService.LATIN_KEY, value).apply()
                    }
                })
            }
        })

        // Son et vibration à chaque touche (comme les options de Gboard).
        fun toggle(label: Int, key: String, default: Boolean) = Switch(this).apply {
            setText(label)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            isChecked = prefs.getBoolean(key, default)
            minHeight = dp(48)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(key, on).apply() }
        }
        column.addView(
            toggle(R.string.set_sound, LinguaImeService.SOUND_KEY, false),
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(16) },
        )
        column.addView(toggle(R.string.set_vibrate, LinguaImeService.VIBRATE_KEY, true))

        column.addView(text(R.string.setup_privacy, 14f).apply { setPadding(0, dp(24), 0, dp(8)) })

        // Suggestions : les mots appris restent sur le téléphone. Le
        // dictionnaire personnel permet de les voir, les supprimer un par un,
        // en ajouter, les proposer au projet ou tout effacer.
        column.addView(text(R.string.setup_learning, 14f))
        dictionaryButton = Button(this).apply {
            setOnClickListener {
                startActivity(Intent(this@SetupActivity, DictionaryActivity::class.java))
            }
        }
        column.addView(dictionaryButton, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

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
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
