package com.linguace.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import android.widget.Button

enum class ShiftState { OFF, ONCE, LOCKED }

/**
 * Le clavier, dessiné par une seule vue : rapide, et maîtrise complète de
 * l'appui long (lettres dérivées) et de la répétition de ⌫.
 */
@SuppressLint("ViewConstructor")
class KeyboardView(context: Context, private val listener: Listener) : View(context) {

    interface Listener {
        fun onText(text: String)
        fun onDelete()
        fun onEnter()
        fun onShift()
        fun onGlobe()
        fun onGlobeLongPress()
        fun onSymbols()
        fun onLetters()
    }

    var layout: Layout = Layouts.LATIN
        set(value) {
            field = value
            requestLayout()
            invalidate()
            announceChange()
        }

    var shift: ShiftState = ShiftState.OFF
        set(value) {
            if (field == value) return
            field = value
            invalidate()
            announceChange()
        }

    private val density = resources.displayMetrics.density
    private val rowHeight = 54 * density
    private val gap = 5 * density
    private val radius = 7 * density

    private val dark =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    // Couleurs LinguaCE (bleu nuit / bleu accent).
    private val bgColor = if (dark) 0xFF0A1628.toInt() else 0xFFE3E8EF.toInt()
    private val keyColor = if (dark) 0xFF1B2C45.toInt() else 0xFFFFFFFF.toInt()
    private val specialColor = if (dark) 0xFF12213A.toInt() else 0xFFC7D0DC.toInt()
    private val pressedColor = if (dark) 0xFF2B4266.toInt() else 0xFFB4C2D6.toInt()
    private val textColor = if (dark) 0xFFF2F5FA.toInt() else 0xFF0A1628.toInt()
    private val hintColor = if (dark) 0xFF7F93B0.toInt() else 0xFF6B7C93.toInt()
    private val accent = 0xFF1E88E5.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        textSize = 10 * density
        color = hintColor
    }

    private data class Placed(val key: Key, val rect: RectF)

    private var placed: List<Placed> = emptyList()
    private var pressed: Placed? = null

    // Appui long : lettres proposées au-dessus de la touche.
    private var popupKey: Placed? = null
    private var popupRects: List<Pair<String, RectF>> = emptyList()
    private var popupIndex = 0

    private val handler = Handler(Looper.getMainLooper())
    private var repeating = false
    private var longPressed = false

    /**
     * Hauteur de la barre de navigation du système. Depuis Android 15, le
     * clavier s'étend dessous (bord à bord) : sans cette marge, les boutons
     * du système (⌄, changement de clavier) recouvrent 🌐 et ↵.
     */
    private var bottomInset = 0

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val bottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        } else {
            @Suppress("DEPRECATION")
            insets.systemWindowInsetBottom
        }
        if (bottom != bottomInset) {
            bottomInset = bottom
            requestLayout()
        }
        return insets
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (layout.rows.size * rowHeight + gap).toInt() + bottomInset
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = place()

    override fun requestLayout() {
        super.requestLayout()
        if (width > 0) place()
    }

    private fun place() {
        val unit = (width - gap) / layout.units
        val out = mutableListOf<Placed>()
        layout.rows.forEachIndexed { r, row ->
            val rowUnits = row.sumOf { it.width.toDouble() }.toFloat()
            var x = gap / 2 + (layout.units - rowUnits) * unit / 2
            val top = r * rowHeight + gap
            for (key in row) {
                val w = key.width * unit
                out += Placed(key, RectF(x + gap / 2, top, x + w - gap / 2, top + rowHeight - gap))
                x += w
            }
        }
        placed = out
    }

    // ─── Dessin ─────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(bgColor)
        for (p in placed) drawKey(canvas, p)
        popupKey?.let { drawPopup(canvas) }
    }

    private fun isSpecial(k: Key) = k.type != KeyType.CHAR && k.type != KeyType.SPACE

    private fun drawKey(canvas: Canvas, p: Placed) {
        val k = p.key
        fill.color = when {
            p == pressed && popupKey == null -> pressedColor
            k.type == KeyType.SHIFT && shift != ShiftState.OFF -> accent
            isSpecial(k) -> specialColor
            else -> keyColor
        }
        canvas.drawRoundRect(p.rect, radius, radius, fill)

        val shifted = shift != ShiftState.OFF
        val label = when (k.type) {
            KeyType.CHAR -> k.output(shifted)
            KeyType.SHIFT -> if (shift == ShiftState.LOCKED) "⇪" else "⇧"
            KeyType.SPACE -> if (layout.id == Layouts.CYRILLIC.id) "Нохчийн" else "Noxçiyŋ"
            KeyType.LETTERS -> "abc"
            else -> k.label
        }
        text.color = if (k.type == KeyType.SHIFT && shift != ShiftState.OFF) 0xFFFFFFFF.toInt() else textColor
        text.textSize = when (k.type) {
            KeyType.CHAR -> 22 * density
            KeyType.SPACE -> 13 * density
            else -> 17 * density
        }
        if (k.type == KeyType.SPACE) text.color = hintColor
        val y = p.rect.centerY() - (text.descent() + text.ascent()) / 2
        canvas.drawText(label, p.rect.centerX(), y, text)

        if (k.popup.isNotEmpty()) {
            canvas.drawText(
                k.popup.first().let { if (shifted) Alphabet.upper(it) else it },
                p.rect.right - 4 * density, p.rect.top + 12 * density, hint,
            )
        }
    }

    private fun drawPopup(canvas: Canvas) {
        val first = popupRects.firstOrNull()?.second ?: return
        val last = popupRects.last().second
        fill.color = if (dark) 0xFF22344F.toInt() else 0xFFFFFFFF.toInt()
        canvas.drawRoundRect(
            RectF(first.left - gap, first.top - gap, last.right + gap, last.bottom + gap),
            radius, radius, fill,
        )
        val shifted = shift != ShiftState.OFF
        popupRects.forEachIndexed { i, (letter, rect) ->
            if (i == popupIndex) {
                fill.color = accent
                canvas.drawRoundRect(rect, radius, radius, fill)
            }
            text.color = if (i == popupIndex) 0xFFFFFFFF.toInt() else textColor
            text.textSize = 22 * density
            val y = rect.centerY() - (text.descent() + text.ascent()) / 2
            canvas.drawText(if (shifted) Alphabet.upper(letter) else letter, rect.centerX(), y, text)
        }
    }

    // ─── Toucher ────────────────────────────────────────────────
    private fun keyAt(x: Float, y: Float): Placed? =
        placed.firstOrNull { it.rect.contains(x, y) }
            ?: placed.minByOrNull { (it.rect.centerX() - x).let { d -> d * d } + (it.rect.centerY() - y).let { d -> d * d } }

    private val longPress = Runnable {
        val p = pressed ?: return@Runnable
        longPressed = true
        when {
            p.key.type == KeyType.GLOBE -> listener.onGlobeLongPress()
            p.key.popup.isNotEmpty() -> openPopup(p)
        }
        invalidate()
    }

    private val repeat = object : Runnable {
        override fun run() {
            repeating = true
            listener.onDelete()
            handler.postDelayed(this, 50)
        }
    }

    private fun openPopup(p: Placed) {
        val w = p.rect.width()
        val n = p.key.popup.size
        var left = p.rect.centerX() - n * w / 2
        left = left.coerceIn(gap, width - gap - n * w)
        val top = (p.rect.top - rowHeight).coerceAtLeast(gap)
        popupRects = p.key.popup.mapIndexed { i, letter ->
            letter to RectF(left + i * w, top, left + (i + 1) * w - gap, top + rowHeight - gap)
        }
        popupIndex = 0
        popupKey = p
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val p = keyAt(e.x, e.y) ?: return true
                pressed = p
                longPressed = false
                repeating = false
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                if (p.key.type == KeyType.DELETE) handler.postDelayed(repeat, 400)
                if (p.key.popup.isNotEmpty() || p.key.type == KeyType.GLOBE) {
                    handler.postDelayed(longPress, 350)
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (popupKey != null) {
                    val i = popupRects.indexOfFirst { e.x < it.second.right + gap / 2 }
                    val next = if (i == -1) popupRects.lastIndex else i
                    if (next != popupIndex) {
                        popupIndex = next
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPress)
                handler.removeCallbacks(repeat)
                val p = pressed
                when {
                    popupKey != null -> listener.onText(
                        popupRects[popupIndex].first.let {
                            if (shift != ShiftState.OFF) Alphabet.upper(it) else it
                        },
                    )
                    p == null || longPressed || repeating -> Unit
                    else -> fire(p.key)
                }
                reset()
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPress)
                handler.removeCallbacks(repeat)
                reset()
            }
        }
        return true
    }

    private fun reset() {
        pressed = null
        popupKey = null
        popupRects = emptyList()
        invalidate()
    }

    private fun fire(k: Key) = when (k.type) {
        KeyType.CHAR -> listener.onText(k.output(shift != ShiftState.OFF))
        KeyType.SPACE -> listener.onText(" ")
        KeyType.DELETE -> listener.onDelete()
        KeyType.ENTER -> listener.onEnter()
        KeyType.SHIFT -> listener.onShift()
        KeyType.GLOBE -> listener.onGlobe()
        KeyType.SYMBOLS -> listener.onSymbols()
        KeyType.LETTERS -> listener.onLetters()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    // ─── Accessibilité (TalkBack) ───────────────────────────────
    //
    // Le clavier est dessiné, pas fait de vues : chaque touche est donc
    // déclarée à Android comme une « vue virtuelle » (nom, position, actions).
    // Exploration au toucher : TalkBack lit la touche sous le doigt, la lettre
    // est tapée quand on lève le doigt. Les lettres de l'appui long deviennent
    // des actions TalkBack (« Taper ċ »…) sur leur touche.

    private val a11yManager =
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    private val provider = KeyNodes()
    private var hovered = -1
    private var a11yFocused = -1

    private val altActionIds =
        intArrayOf(R.id.a11y_alt_0, R.id.a11y_alt_1, R.id.a11y_alt_2, R.id.a11y_alt_3)

    override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider = provider

    /** Nom de la touche lu par TalkBack, dans la langue du téléphone. */
    internal fun describe(k: Key): String {
        val shifted = shift != ShiftState.OFF
        return when (k.type) {
            KeyType.CHAR -> when {
                k.label == "Ӏ" -> context.getString(R.string.key_palochka)
                else -> k.output(shifted)
            }
            KeyType.SHIFT -> context.getString(
                if (shift == ShiftState.LOCKED) R.string.key_shift_locked else R.string.key_shift,
            )
            KeyType.DELETE -> context.getString(R.string.key_delete)
            KeyType.ENTER -> context.getString(R.string.key_enter)
            KeyType.SPACE -> context.getString(R.string.key_space)
            KeyType.GLOBE -> context.getString(R.string.key_globe)
            KeyType.SYMBOLS -> context.getString(R.string.key_symbols)
            KeyType.LETTERS -> context.getString(R.string.key_letters)
        }
    }

    private fun altOutput(letter: String) =
        if (shift != ShiftState.OFF) Alphabet.upper(letter) else letter

    override fun onHoverEvent(e: MotionEvent): Boolean {
        if (!a11yManager.isTouchExplorationEnabled) return super.onHoverEvent(e)
        val i = placed.indexOfFirst { it.rect.contains(e.x, e.y) }
        when (e.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                if (i != hovered) {
                    if (hovered >= 0) sendKeyEvent(hovered, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT)
                    hovered = i
                    if (i >= 0) sendKeyEvent(i, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
                }
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                // Doigt levé sur une touche : on la tape.
                if (hovered >= 0 && hovered == i) fire(placed[i].key)
                if (hovered >= 0) sendKeyEvent(hovered, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT)
                hovered = -1
            }
        }
        return true
    }

    /** À appeler quand la disposition ou ⇧ change : TalkBack relit l'arbre. */
    internal fun announceChange() {
        if (!a11yManager.isEnabled) return
        @Suppress("DEPRECATION")
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        event.packageName = context.packageName
        event.setSource(this)
        parent?.requestSendAccessibilityEvent(this, event)
    }

    private fun sendKeyEvent(i: Int, type: Int) {
        if (!a11yManager.isEnabled || i !in placed.indices) return
        @Suppress("DEPRECATION")
        val event = AccessibilityEvent.obtain(type)
        event.packageName = context.packageName
        event.className = Button::class.java.name
        event.contentDescription = describe(placed[i].key)
        event.setSource(this, i)
        event.isEnabled = true
        parent?.requestSendAccessibilityEvent(this, event)
    }

    private inner class KeyNodes : AccessibilityNodeProvider() {

        @Suppress("DEPRECATION")
        override fun createAccessibilityNodeInfo(virtualViewId: Int): AccessibilityNodeInfo? {
            if (virtualViewId == HOST_VIEW_ID) {
                val info = AccessibilityNodeInfo.obtain(this@KeyboardView)
                onInitializeAccessibilityNodeInfo(info)
                placed.indices.forEach { info.addChild(this@KeyboardView, it) }
                return info
            }
            val p = placed.getOrNull(virtualViewId) ?: return null
            val info = AccessibilityNodeInfo.obtain(this@KeyboardView, virtualViewId)
            info.packageName = context.packageName
            info.className = Button::class.java.name
            info.setParent(this@KeyboardView)
            info.contentDescription = describe(p.key)
            info.isEnabled = true
            info.isClickable = true
            info.isFocusable = true
            info.isVisibleToUser = true
            info.isAccessibilityFocused = virtualViewId == a11yFocused

            val r = android.graphics.Rect()
            p.rect.round(r)
            info.setBoundsInParent(r)
            val loc = IntArray(2)
            getLocationOnScreen(loc)
            r.offset(loc[0], loc[1])
            info.setBoundsInScreen(r)

            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
            info.addAction(
                if (virtualViewId == a11yFocused) {
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS
                } else {
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS
                },
            )
            p.key.popup.take(altActionIds.size).forEachIndexed { n, letter ->
                info.addAction(
                    AccessibilityNodeInfo.AccessibilityAction(
                        altActionIds[n],
                        context.getString(R.string.key_type_letter, altOutput(letter)),
                    ),
                )
            }
            return info
        }

        override fun performAction(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
            if (virtualViewId == HOST_VIEW_ID) {
                return this@KeyboardView.performAccessibilityAction(action, arguments)
            }
            val p = placed.getOrNull(virtualViewId) ?: return false
            when (action) {
                AccessibilityNodeInfo.ACTION_CLICK -> {
                    fire(p.key)
                    sendKeyEvent(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED)
                    return true
                }
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS -> {
                    if (a11yFocused == virtualViewId) return false
                    a11yFocused = virtualViewId
                    sendKeyEvent(virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
                    invalidate()
                    return true
                }
                AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> {
                    if (a11yFocused != virtualViewId) return false
                    a11yFocused = -1
                    sendKeyEvent(virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
                    invalidate()
                    return true
                }
            }
            val n = altActionIds.indexOf(action)
            if (n >= 0 && n < p.key.popup.size) {
                listener.onText(altOutput(p.key.popup[n]))
                return true
            }
            return false
        }
    }
}
