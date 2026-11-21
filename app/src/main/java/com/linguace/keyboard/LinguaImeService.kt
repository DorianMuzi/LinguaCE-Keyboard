package com.linguace.keyboard

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager

/**
 * Le service de saisie. Il ne garde rien de ce qui est tapé : seul le choix
 * de la disposition (latin ou cyrillique) est mémorisé, pour la retrouver.
 */
class LinguaImeService : InputMethodService(), KeyboardView.Listener {

    private var view: KeyboardView? = null
    private var letters: Layout = Layouts.LATIN
    private var lastShiftTap = 0L

    private val prefs by lazy { getSharedPreferences("keyboard", Context.MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        letters = Layouts.byId(prefs.getString("layout", null))
    }

    override fun onCreateInputView(): View =
        KeyboardView(this, this).also {
            it.layout = letters
            view = it
        }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        view?.layout = letters
        view?.shift = ShiftState.OFF
        autoShift()
    }

    // ─── Saisie ─────────────────────────────────────────────────
    override fun onText(text: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(text, 1)
        if (view?.shift == ShiftState.ONCE) view?.shift = ShiftState.OFF
        autoShift()
    }

    override fun onDelete() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            val before = ic.getTextBeforeCursor(16, 0) ?: ""
            val n = Graphemes.lastLength(before)
            if (n > 0) {
                ic.deleteSurroundingText(n, 0)
            } else {
                // Champ vide ou texte inaccessible : on laisse l'app décider.
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            }
        }
        autoShift()
    }

    override fun onEnter() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val noAction = (info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        if (!noAction && action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED
        ) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
    }

    /** ⇧ : une majuscule ; deux appuis rapprochés : verrouillage ; puis arrêt. */
    override fun onShift() {
        val v = view ?: return
        val now = System.currentTimeMillis()
        v.shift = when {
            v.shift == ShiftState.ONCE && now - lastShiftTap < 400 -> ShiftState.LOCKED
            v.shift == ShiftState.OFF -> ShiftState.ONCE
            else -> ShiftState.OFF
        }
        lastShiftTap = now
    }

    override fun onGlobe() {
        letters = if (letters.id == Layouts.LATIN.id) Layouts.CYRILLIC else Layouts.LATIN
        prefs.edit().putString("layout", letters.id).apply()
        view?.layout = letters
    }

    /** Appui long sur 🌐 : changer de clavier (celui du système, par exemple). */
    override fun onGlobeLongPress() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    override fun onSymbols() {
        view?.layout = Layouts.SYMBOLS
    }

    override fun onLetters() {
        view?.layout = letters
    }

    /** Majuscule automatique en début de phrase, si le champ la demande. */
    private fun autoShift() {
        val v = view ?: return
        if (v.shift == ShiftState.LOCKED) return
        val info = currentInputEditorInfo ?: return
        val ic = currentInputConnection ?: return
        val type = info.inputType
        val isText = type and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT
        val caps = isText && ic.getCursorCapsMode(type) != 0
        v.shift = if (caps) ShiftState.ONCE else ShiftState.OFF
    }
}
