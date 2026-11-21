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
        column.addView(text(R.string.setup_privacy, 14f).apply { setPadding(0, dp(24), 0, 0) })

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

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
