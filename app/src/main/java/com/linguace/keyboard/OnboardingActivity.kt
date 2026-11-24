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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView

/**
 * Accueil guidé, montré la première fois : 1) bienvenue et engagement de
 * confidentialité, 2) activer et choisir le clavier (étapes cochées au fur et
 * à mesure), 3) l'essayer, avec trois astuces. Sans bibliothèque : trois
 * « pages » construites en code, comme le reste du clavier.
 */
class OnboardingActivity : Activity() {

    private val prefs by lazy { getSharedPreferences("keyboard", Context.MODE_PRIVATE) }
    private var page = 0

    private lateinit var content: FrameLayout
    private lateinit var back: TextView
    private lateinit var skip: TextView
    private lateinit var next: Button
    private lateinit var dots: LinearLayout
    private var enableButton: Button? = null
    private var chooseButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = savedInstanceState?.getInt(PAGE_KEY, 0) ?: 0

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content = FrameLayout(this)
        root.addView(
            ScrollView(this).apply { addView(content) },
            LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f),
        )

        // Barre du bas : Retour · points · Suivant.
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        back = link(R.string.ob_back) { go(page - 1) }
        dots = LinearLayout(this).apply { gravity = Gravity.CENTER }
        next = Button(this).apply { setOnClickListener { if (page < LAST) go(page + 1) else finishOnboarding() } }
        bar.addView(back, LinearLayout.LayoutParams(0, dp(48), 1f))
        bar.addView(dots, LinearLayout.LayoutParams(0, dp(48), 1f))
        bar.addView(next, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        root.addView(bar)

        // « Passer » en haut à droite, au-dessus de tout.
        skip = link(R.string.ob_skip) { finishOnboarding() }.apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        val frame = FrameLayout(this)
        frame.addView(root, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        frame.addView(
            skip,
            FrameLayout.LayoutParams(WRAP_CONTENT, dp(48), Gravity.TOP or Gravity.END)
                .apply { topMargin = dp(8); rightMargin = dp(16) },
        )
        frame.padForSystemBars()
        setContentView(frame)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            ) { handleBack() }
        }
        show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(PAGE_KEY, page)
    }

    override fun onResume() {
        super.onResume()
        refreshSteps()
    }

    /**
     * Retour : page précédente, puis sortie vers les réglages. Android 13+ (et
     * obligatoire avec le niveau d'API cible 36) : le geste « retour » passe
     * par un rappel enregistré, l'ancienne méthode n'est plus appelée.
     */
    private fun handleBack() {
        if (page > 0) go(page - 1) else finishOnboarding()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) handleBack()
        else super.onBackPressed()
    }

    private fun finishOnboarding() {
        prefs.edit().putBoolean(ONBOARDED_KEY, true).apply()
        finish()
    }

    private fun go(p: Int) {
        page = p.coerceIn(0, LAST)
        show()
    }

    private fun show() {
        content.removeAllViews()
        enableButton = null
        chooseButton = null
        content.addView(
            when (page) {
                0 -> welcomePage()
                1 -> installPage()
                else -> tryPage()
            },
        )
        back.visibility = if (page == 0) View.INVISIBLE else View.VISIBLE
        skip.visibility = if (page == LAST) View.INVISIBLE else View.VISIBLE
        next.setText(if (page == LAST) R.string.ob_done else R.string.ob_next)
        dots.removeAllViews()
        for (i in 0..LAST) {
            dots.addView(TextView(this).apply {
                text = "●"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(if (i == page) ACCENT else 0x55888888)
                setPadding(dp(5), 0, dp(5), 0)
            })
        }
        refreshSteps()
    }

    // ─── Pages ──────────────────────────────────────────────────
    private fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(28), dp(64), dp(28), dp(16))
    }

    private fun title(res: Int) = TextView(this).apply {
        setText(res)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(12), 0, dp(12))
    }

    private fun body(res: Int, sp: Float = 17f) = TextView(this).apply {
        setText(res)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setLineSpacing(0f, 1.15f)
        setPadding(0, 0, 0, dp(16))
    }

    private fun link(res: Int, onClick: () -> Unit) = TextView(this).apply {
        setText(res)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(ACCENT)
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
        minHeight = dp(48)
        setOnClickListener { onClick() }
    }

    private fun welcomePage() = column().apply {
        addView(ImageView(this@OnboardingActivity).apply {
            setImageResource(R.mipmap.ic_launcher)
            contentDescription = null // décoratif : le titre suit
        }, LinearLayout.LayoutParams(dp(112), dp(112)).apply { bottomMargin = dp(8) })
        addView(title(R.string.ob_welcome_title))
        addView(body(R.string.ob_welcome_text))
        // L'engagement qui distingue ce clavier : bien visible.
        addView(TextView(this@OnboardingActivity).apply {
            setText(R.string.ob_welcome_private)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setLineSpacing(0f, 1.15f)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(0x1A1E88E5)
        })
    }

    private fun installPage() = column().apply {
        addView(title(R.string.ob_install_title))
        addView(body(R.string.ob_install_text))
        enableButton = Button(this@OnboardingActivity).apply {
            setText(R.string.setup_enable)
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        }
        chooseButton = Button(this@OnboardingActivity).apply {
            setText(R.string.setup_choose)
            setOnClickListener {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showInputMethodPicker()
            }
        }
        addView(enableButton)
        addView(chooseButton)
        addView(TextView(this@OnboardingActivity).apply {
            setText(R.string.ob_install_warning)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setLineSpacing(0f, 1.15f)
            setPadding(0, dp(16), 0, 0)
        })
    }

    private fun tryPage() = column().apply {
        addView(title(R.string.ob_try_title))
        addView(EditText(this@OnboardingActivity).apply {
            setHint(R.string.setup_try)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })
        addView(body(R.string.ob_try_hint, 14f))
        for (tip in listOf(R.string.ob_try_tip1, R.string.ob_try_tip2, R.string.ob_try_tip3)) {
            addView(body(tip, 16f))
        }
        // Disposition latine : à choisir dès le départ.
        addView(TextView(this@OnboardingActivity).apply {
            setText(R.string.set_latin_layout)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(8), 0, 0)
        })
        addView(RadioGroup(this@OnboardingActivity).apply {
            orientation = RadioGroup.HORIZONTAL
            val azerty = LinguaImeService.isAzerty(this@OnboardingActivity)
            for ((label, value) in listOf("QWERTY" to "qwerty", "AZERTY" to "azerty")) {
                addView(RadioButton(this@OnboardingActivity).apply {
                    id = View.generateViewId()
                    text = label
                    minHeight = dp(48)
                    setPadding(dp(4), 0, dp(24), 0)
                    isChecked = (value == "azerty") == azerty
                    setOnCheckedChangeListener { _, on ->
                        if (on) prefs.edit().putString(LinguaImeService.LATIN_KEY, value).apply()
                    }
                })
            }
        })
    }

    /** « ✓ » devant l'étape déjà faite (clavier activé / choisi). */
    private fun refreshSteps() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val chosen = current?.startsWith("$packageName/") == true
        enableButton?.text = (if (enabled) "✓  " else "") + getString(R.string.setup_enable)
        chooseButton?.text = (if (chosen) "✓  " else "") + getString(R.string.setup_choose)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val ONBOARDED_KEY = "onboarded"
        private const val PAGE_KEY = "ob_page"
        private const val LAST = 2
        private const val ACCENT = 0xFF1E88E5.toInt()
    }
}
