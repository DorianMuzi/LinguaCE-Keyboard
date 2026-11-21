package com.linguace.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.media.AudioManager
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
        fun onSuggestion(word: String)
    }

    /** Appelé quand la bande apparaît ou disparaît (zone visible modifiée). */
    var onContentTopChanged: (() -> Unit)? = null

    /** Mots proposés dans la bande du haut (3 au plus). */
    var suggestions: List<String> = emptyList()
        set(value) {
            if (field == value) return
            val shown = field.isEmpty() != value.isEmpty()
            field = value
            place()
            // La bande n'apparaît que s'il y a des mots (demande de l'auteur) :
            // la hauteur ne change pas, seule la zone visible et touchable
            // change — on demande au système de la recalculer.
            if (shown) onContentTopChanged?.invoke()
            invalidate()
            announceChange()
        }

    /** Bande de suggestions affichée (pas dans les champs de mot de passe). */
    var showStrip: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
            invalidate()
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
    /**
     * Proportions de Gboard, mesurées sur le téléphone de l'auteur
     * (2026-11-21) : une rangée tous les 50 dp, des touches de 40 dp de haut,
     * donc 10 dp entre deux rangées — on touche moins souvent la rangée d'à
     * côté. 42 dp si une disposition avait 5 rangées.
     */
    private val compactRowHeight = 42 * density
    private val rowHeight = 50 * density

    @Suppress("UNUSED_PARAMETER")
    private fun rowHeightOf(r: Int): Float =
        if (layout.rows.size >= 5) compactRowHeight else rowHeight

    /** Écart horizontal entre deux touches. */
    private val gap = 5 * density

    /** Écart vertical entre deux rangées (Gboard : ≈ 10 dp). */
    private val rowGap = 10 * density
    private val radius = 7 * density
    private val stripHeight = 40 * density
    /**
     * Place de la bande de suggestions, TOUJOURS réservée (hors mots de passe) :
     * la hauteur du clavier ne change jamais, donc les touches ne bougent
     * jamais sous les doigts. Quand il n'y a pas de suggestion, cette bande
     * est transparente et laisse passer les touchers vers l'app (voir
     * [contentTop] et LinguaImeService.onComputeInsets) : on ne la voit pas.
     * Une bande qui apparaissait en changeant la hauteur faisait taper des
     * lettres de la mauvaise rangée en frappe rapide.
     */
    private val stripSpace: Float
        get() = if (showStrip) stripHeight else 0f

    /** Haut de la partie visible et touchable du clavier, dans la vue. */
    val contentTop: Int
        get() = if (suggestions.isEmpty()) stripSpace.toInt() else 0

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
        textSize = 9 * density
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

    /**
     * Marge sous la dernière rangée, en plus de la barre de navigation : sans
     * elle, 🌐 et ↵ collaient aux boutons du système (⌄, changement de
     * clavier) — constaté sur un vrai téléphone, 2026-11-21.
     */
    private val bottomMargin = 16 * density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val rows = layout.rows.indices.sumOf { rowHeightOf(it).toDouble() }.toFloat()
        // Bas de la dernière touche, puis la même marge qu'avant (validée par
        // l'auteur) jusqu'aux boutons du système.
        val lastKeyBottom = stripSpace + gap + rows - rowGap
        val h = (lastKeyBottom + gap + bottomMargin).toInt() + bottomInset
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
        // Bande de suggestions : trois cases égales sur toute la largeur.
        if (showStrip && suggestions.isNotEmpty()) {
            val w = width.toFloat() / 3
            suggestions.take(3).forEachIndexed { i, word ->
                out += Placed(
                    Key(word, KeyType.SUGGESTION),
                    RectF(i * w + gap, gap, (i + 1) * w - gap, stripHeight),
                )
            }
        }
        var top = gap + stripSpace
        layout.rows.forEachIndexed { r, row ->
            val rowUnits = row.sumOf { it.width.toDouble() }.toFloat()
            var x = gap / 2 + (layout.units - rowUnits) * unit / 2
            val h = rowHeightOf(r)
            for (key in row) {
                val w = key.width * unit
                out += Placed(key, RectF(x + gap / 2, top, x + w - gap / 2, top + h - rowGap))
                x += w
            }
            top += h
        }
        placed = out
    }

    // ─── Dessin ─────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        // Fond seulement sous la partie visible : la bande vide reste transparente.
        fill.color = bgColor
        canvas.drawRect(0f, contentTop.toFloat(), width.toFloat(), height.toFloat(), fill)
        for (p in placed) drawKey(canvas, p)
        popupKey?.let { drawPopup(canvas) }
    }

    private fun isSpecial(k: Key) = k.type != KeyType.CHAR && k.type != KeyType.SPACE

    private fun drawSuggestion(canvas: Canvas, p: Placed) {
        if (p == pressed) {
            fill.color = pressedColor
            canvas.drawRoundRect(p.rect, radius, radius, fill)
        }
        text.color = textColor
        text.textSize = 17 * density
        var label = p.key.label
        // Mot trop long pour sa case : on le coupe avec « … ».
        val max = p.rect.width() - 8 * density
        if (text.measureText(label) > max) {
            while (label.length > 1 && text.measureText("$label…") > max) label = label.dropLast(1)
            label = "$label…"
        }
        val y = p.rect.centerY() - (text.descent() + text.ascent()) / 2
        canvas.drawText(label, p.rect.centerX(), y, text)
    }

    private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2.2f * density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    /**
     * Flèche ⇧ : contour quand elle est éteinte, pleine pour une majuscule,
     * pleine et soulignée quand les majuscules sont verrouillées.
     */
    private fun drawShiftIcon(canvas: Canvas, r: RectF) {
        val s = 11 * density
        val cx = r.centerX()
        val cy = r.centerY() - 1.5f * density
        val path = Path().apply {
            moveTo(cx, cy - s)
            lineTo(cx + s, cy)
            lineTo(cx + s * 0.45f, cy)
            lineTo(cx + s * 0.45f, cy + s * 0.85f)
            lineTo(cx - s * 0.45f, cy + s * 0.85f)
            lineTo(cx - s * 0.45f, cy)
            lineTo(cx - s, cy)
            close()
        }
        icon.color = if (shift == ShiftState.OFF) textColor else 0xFFFFFFFF.toInt()
        icon.style = if (shift == ShiftState.OFF) Paint.Style.STROKE else Paint.Style.FILL_AND_STROKE
        canvas.drawPath(path, icon)
        if (shift == ShiftState.LOCKED) {
            icon.style = Paint.Style.STROKE
            val y = cy + s * 0.85f + 4 * density
            canvas.drawLine(cx - s * 0.6f, y, cx + s * 0.6f, y, icon)
        }
    }

    /** Effacer ⌫ : pentagone pointé vers la gauche, une croix à l'intérieur. */
    private fun drawDeleteIcon(canvas: Canvas, r: RectF) {
        val w = 13 * density
        val h = 9 * density
        val cx = r.centerX() + 1 * density
        val cy = r.centerY()
        val path = Path().apply {
            moveTo(cx - w, cy)
            lineTo(cx - w * 0.45f, cy - h)
            lineTo(cx + w, cy - h)
            lineTo(cx + w, cy + h)
            lineTo(cx - w * 0.45f, cy + h)
            close()
        }
        icon.color = textColor
        icon.style = Paint.Style.STROKE
        canvas.drawPath(path, icon)
        val x = cx + w * 0.28f
        val d = h * 0.45f
        canvas.drawLine(x - d, cy - d, x + d, cy + d, icon)
        canvas.drawLine(x - d, cy + d, x + d, cy - d, icon)
    }

    private fun drawKey(canvas: Canvas, p: Placed) {
        val k = p.key
        if (k.type == KeyType.SUGGESTION) return drawSuggestion(canvas, p)
        fill.color = when {
            p == pressed && popupKey == null -> pressedColor
            k.type == KeyType.SHIFT && shift != ShiftState.OFF -> accent
            isSpecial(k) -> specialColor
            else -> keyColor
        }
        canvas.drawRoundRect(p.rect, radius, radius, fill)

        // ⇧ et ⌫ : icônes dessinées, épaisses comme sur Gboard (les caractères
        // de la police étaient trop fins — retour de l'auteur).
        if (k.type == KeyType.SHIFT) return drawShiftIcon(canvas, p.rect)
        if (k.type == KeyType.DELETE) return drawDeleteIcon(canvas, p.rect)

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
            KeyType.CHAR -> 20 * density
            KeyType.SPACE -> 13 * density
            // Symboles des touches de fonction : ⇧ ⌫ ↵ étaient trop petits.
            KeyType.SHIFT -> 26 * density
            KeyType.DELETE, KeyType.ENTER -> 24 * density
            else -> 16 * density
        }
        if (k.type == KeyType.SPACE) text.color = hintColor
        // Touche avec lettre d'appui long : la lettre principale descend un peu
        // et l'indice se loge dans le coin, pour que les majuscules (plus
        // hautes, Q̇ Ṫ Ə…) ne touchent pas l'indice (retour de l'auteur).
        // Seulement en latin (touches plus basses, 42 dp) : le cyrillique garde
        // le rendu que l'auteur avait validé.
        val latin = layout.isLatin
        // Toutes les lettres descendent d'autant : elles restent alignées.
        val drop = if (latin && k.type == KeyType.CHAR) 2.5f * density else 0f
        val y = p.rect.centerY() - (text.descent() + text.ascent()) / 2 + drop
        canvas.drawText(label, p.rect.centerX(), y, text)

        // Indices d'appui long : la lettre dérivée en haut à droite, le chiffre
        // (rangée du haut, comme Gboard) en haut à gauche.
        hint.textSize = (if (latin) 9f else 10f) * density
        val hintY = p.rect.top + (if (latin) 10.5f else 12f) * density
        k.variant?.let { v ->
            hint.textAlign = Paint.Align.RIGHT
            canvas.drawText(
                if (shifted) Alphabet.upper(v) else v,
                p.rect.right - (if (latin) 2.5f else 4f) * density, hintY, hint,
            )
        }
        k.digit?.let { d ->
            hint.textAlign = Paint.Align.LEFT
            canvas.drawText(d, p.rect.left + (if (latin) 3f else 4f) * density, hintY, hint)
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
    /**
     * Touche sous le doigt ; entre deux touches, la plus proche. Dans la bande
     * de suggestions, seulement un mot réellement touché (un appui à côté ne
     * doit pas taper la lettre la plus proche).
     */
    private fun keyAt(x: Float, y: Float): Placed? {
        placed.firstOrNull { it.rect.contains(x, y) }?.let { return it }
        if (y < gap + stripSpace) return null
        return placed.filter { it.key.type != KeyType.SUGGESTION }
            .minByOrNull { (it.rect.centerX() - x).let { d -> d * d } + (it.rect.centerY() - y).let { d -> d * d } }
    }

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
        // La première proposition (la lettre dérivée) s'ouvre juste au-dessus
        // du doigt ; on glisse à droite pour le chiffre.
        var left = p.rect.left
        left = left.coerceIn(gap, width - gap - n * w)
        val top = (p.rect.top - rowHeight).coerceAtLeast(gap)
        popupRects = p.key.popup.mapIndexed { i, letter ->
            letter to RectF(left + i * w, top, left + (i + 1) * w - gap, top + rowHeight - gap)
        }
        popupIndex = 0
        popupKey = p
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    // ─── Retour sonore et vibration (réglages de l'écran d'installation) ───
    /** Son à chaque touche, comme l'option de Gboard (désactivé par défaut). */
    var soundOn = false

    /** Vibration à chaque touche (activée par défaut). */
    var vibrateOn = true

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun feedback(k: Key) {
        if (vibrateOn) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (soundOn) {
            audio.playSoundEffect(
                when (k.type) {
                    KeyType.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                    KeyType.DELETE -> AudioManager.FX_KEYPRESS_DELETE
                    KeyType.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                    else -> AudioManager.FX_KEYPRESS_STANDARD
                },
                -1f, // volume des sons du système
            )
        }
    }

    // ─── Frappe rapide ──────────────────────────────────────────
    // Plusieurs doigts : quand un nouveau doigt se pose alors que le précédent
    // n'est pas encore levé (frappe rapide à deux pouces), la touche du doigt
    // précédent est tapée tout de suite — comme Gboard. Sans cela, des lettres
    // se perdaient ou arrivaient en retard (retour de l'auteur).
    private var pointerId = -1

    private fun press(p: Placed) {
        pressed = p
        longPressed = false
        repeating = false
        feedback(p.key)
        if (p.key.type == KeyType.DELETE) handler.postDelayed(repeat, 400)
        if (p.key.popup.isNotEmpty() || p.key.type == KeyType.GLOBE) {
            handler.postDelayed(longPress, 350)
        }
        invalidate()
    }

    /** Termine l'appui en cours : tape sa touche (ou la lettre choisie). */
    private fun release() {
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

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // Un doigt était déjà posé : sa touche part avant la nouvelle.
                if (pressed != null) release()
                val i = e.actionIndex
                val p = keyAt(e.getX(i), e.getY(i)) ?: return true
                pointerId = e.getPointerId(i)
                press(p)
            }
            MotionEvent.ACTION_MOVE -> {
                val i = e.findPointerIndex(pointerId)
                if (i < 0) return true
                val x = e.getX(i)
                val y = e.getY(i)
                if (popupKey != null) {
                    val j = popupRects.indexOfFirst { x < it.second.right + gap / 2 }
                    val next = if (j == -1) popupRects.lastIndex else j
                    if (next != popupIndex) {
                        popupIndex = next
                        invalidate()
                    }
                } else if (!longPressed && !repeating) {
                    // Le doigt glisse sur une autre touche avant d'être levé :
                    // c'est elle qui sera tapée (comme Gboard).
                    val p = keyAt(x, y)
                    if (p != null && p != pressed && p.key.type != KeyType.SUGGESTION &&
                        pressed?.key?.type != KeyType.SUGGESTION
                    ) {
                        handler.removeCallbacks(longPress)
                        handler.removeCallbacks(repeat)
                        pressed = p
                        if (p.key.popup.isNotEmpty() || p.key.type == KeyType.GLOBE) {
                            handler.postDelayed(longPress, 350)
                        }
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                // Seul le doigt de l'appui en cours compte ; un doigt déjà
                // « tapé » par l'arrivée d'un autre ne fait plus rien.
                if (e.getPointerId(e.actionIndex) == pointerId) {
                    release()
                    pointerId = -1
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPress)
                handler.removeCallbacks(repeat)
                pointerId = -1
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
        KeyType.SUGGESTION -> listener.onSuggestion(k.label)
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
            KeyType.SUGGESTION -> context.getString(R.string.key_suggestion, k.label)
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
