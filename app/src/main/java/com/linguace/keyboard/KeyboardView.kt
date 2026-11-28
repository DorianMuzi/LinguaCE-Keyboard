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
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import android.widget.Button

enum class ShiftState { OFF, ONCE, LOCKED }

/** Boutons de la barre d'outils, de gauche à droite. */
private val TOOLS = listOf("settings", "emoji", "undo", "paste")

/** Thème du clavier (réglage « Thème »). */
enum class Theme {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun of(pref: String?): Theme = entries.firstOrNull { it.name.equals(pref, true) } ?: SYSTEM
    }
}

/** Hauteur des touches (réglage « Hauteur des touches »). */
enum class KeyHeight(val scale: Float) {
    SMALL(0.88f), NORMAL(1f), LARGE(1.12f);

    companion object {
        fun of(pref: String?): KeyHeight = entries.firstOrNull { it.name.equals(pref, true) } ?: NORMAL
    }
}

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
        fun onEmojiPanel()
        fun onEmojiPage(page: String)
        fun onEmojiPick(emoji: String)
        fun onSettings()
        fun onUndo()
        fun onPaste()
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

    /**
     * Barre d'outils (⚙ 😊 ↶ 📋) dans la bande du haut quand il n'y a pas de
     * suggestion, comme Gboard. Réglage « Barre d'outils ». Désactivée, la
     * bande reste invisible et laisse passer les touchers jusqu'aux mots.
     */
    var showToolbar: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            place()
            onContentTopChanged?.invoke()
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

    /**
     * Alphabet de saisie en cours (latin ou cyrillique), retenu même quand on
     * passe sur les symboles ou les emoji : la touche de changement d'alphabet
     * affiche l'alphabet VERS lequel elle bascule (« КИР » depuis le latin,
     * « LAT » depuis le cyrillique).
     */
    private var lettersLatin = true

    var layout: Layout = Layouts.LATIN
        set(value) {
            // Une autre page d'emoji repart du haut ; la même page garde sa place.
            if (field.id != value.id) scrollY = 0f
            if (value.isLatin) lettersLatin = true
            else if (value.id == Layouts.CYRILLIC.id) lettersLatin = false
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
    private val compactRowHeight get() = 42 * density * heightScale
    private val rowHeight get() = 50 * density * heightScale

    /**
     * Réglage « Hauteur des touches » : 0,88 (petite), 1 (normale), 1,12
     * (grande). Seule la hauteur des rangées change, les écarts restent.
     */
    var heightScale = 1f
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    @Suppress("UNUSED_PARAMETER")
    private fun rowHeightOf(r: Int): Float =
        if (layout.visibleRows >= 5) compactRowHeight else rowHeight

    // ─── Défilement (grille des emoji) ──────────────────────────
    /** Décalage vertical de la grille qui défile, en pixels. */
    private var scrollY = 0f
    private var scrolling = false
    private var downY = 0f
    private var scrollStart = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val scrollTop: Float get() = stripSpace

    /** Bas de la zone qui défile : juste avant la rangée fixe des onglets. */
    private val scrollBottom: Float
        get() = gap + stripSpace + Layout.VISIBLE_SCROLL_ROWS * rowHeightOf(0) - rowGap / 2

    private val maxScroll: Float
        get() = ((layout.scrollRows - Layout.VISIBLE_SCROLL_ROWS).coerceAtLeast(0)) * rowHeightOf(0)

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
        get() = if (suggestions.isEmpty() && !(showStrip && showToolbar)) stripSpace.toInt() else 0

    /**
     * Réglage « Thème » : [Theme.SYSTEM] suit le téléphone (clair le jour,
     * sombre la nuit si le téléphone le fait), sinon toujours clair ou sombre.
     */
    var theme: Theme = Theme.SYSTEM
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** Le clavier est-il sombre en ce moment (selon le thème choisi et le téléphone) ? */
    val isDark: Boolean get() = dark

    private val dark: Boolean
        get() = when (theme) {
            Theme.LIGHT -> false
            Theme.DARK -> true
            Theme.SYSTEM ->
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
        }

    // Couleurs LinguaCE (bleu nuit / bleu accent).
    private val bgColor get() = if (dark) 0xFF0A1628.toInt() else 0xFFE3E8EF.toInt()
    private val keyColor get() = if (dark) 0xFF1B2C45.toInt() else 0xFFFFFFFF.toInt()
    private val specialColor get() = if (dark) 0xFF12213A.toInt() else 0xFFC7D0DC.toInt()
    private val pressedColor get() = if (dark) 0xFF2B4266.toInt() else 0xFFB4C2D6.toInt()
    private val textColor get() = if (dark) 0xFFF2F5FA.toInt() else 0xFF0A1628.toInt()
    private val hintColor get() = if (dark) 0xFF7F93B0.toInt() else 0xFF6B7C93.toInt()
    private val accent = 0xFF1E88E5.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        textSize = 9 * density
        color = hintColor
    }

    /** [scrolls] : la touche fait partie de la grille qui défile. */
    private data class Placed(val key: Key, val rect: RectF, val scrolls: Boolean = false)

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
        val rows = layout.visibleRows * rowHeightOf(0)
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
        } else if (showStrip && showToolbar) {
            // Pas de mot à proposer : la barre d'outils, quatre cases égales.
            val w = width.toFloat() / TOOLS.size
            TOOLS.forEachIndexed { i, tool ->
                out += Placed(
                    Key(tool, KeyType.TOOL, target = tool),
                    RectF(i * w + gap, gap, (i + 1) * w - gap, stripHeight),
                )
            }
        }
        scrollY = scrollY.coerceIn(0f, maxScroll)
        val h = rowHeightOf(0)
        layout.rows.forEachIndexed { r, row ->
            val scrolls = r < layout.scrollRows
            // Grille : décalée par le défilement ; rangées fixes : sous les
            // rangées visibles de la grille.
            val rowIndex = if (scrolls) r else r - layout.scrollRows +
                layout.visibleRows - (layout.rows.size - layout.scrollRows)
            val top = gap + stripSpace + rowIndex * h - (if (scrolls) scrollY else 0f)
            // Hors de la zone visible : pas de touche (ni dessin, ni toucher).
            if (scrolls && (top + h <= scrollTop || top >= scrollBottom)) return@forEachIndexed
            val rowUnits = row.sumOf { it.width.toDouble() }.toFloat()
            var x = gap / 2 + (layout.units - rowUnits) * unit / 2
            for (key in row) {
                val w = key.width * unit
                out += Placed(key, RectF(x + gap / 2, top, x + w - gap / 2, top + h - rowGap), scrolls)
                x += w
            }
        }
        placed = out
    }

    // ─── Dessin ─────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        // Fond seulement sous la partie visible : la bande vide reste transparente.
        fill.color = bgColor
        canvas.drawRect(0f, contentTop.toFloat(), width.toFloat(), height.toFloat(), fill)
        // La grille qui défile est rognée à sa zone ; le reste se dessine normalement.
        for (p in placed) if (!p.scrolls) drawKey(canvas, p)
        if (placed.any { it.scrolls }) {
            canvas.save()
            canvas.clipRect(0f, scrollTop, width.toFloat(), scrollBottom)
            for (p in placed) if (p.scrolls) drawKey(canvas, p)
            canvas.restore()
        }
        popupKey?.let { drawPopup(canvas) }
    }

    private fun isSpecial(k: Key) =
        k.type != KeyType.CHAR && k.type != KeyType.SPACE && k.type != KeyType.PICTO

    /** Touches de la bande du haut (mots ou outils) : jamais choisies « à côté ». */
    private fun isStripKey(k: Key) = k.type == KeyType.SUGGESTION || k.type == KeyType.TOOL

    /** Touches en forme de pilule, comme sur Gboard : Entrée, 123, abc. */
    private fun isPill(k: Key) =
        k.type == KeyType.ENTER || k.type == KeyType.SYMBOLS || k.type == KeyType.LETTERS

    private val enterPressedColor = 0xFF1565C0.toInt()

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
        // La flèche va de cy − s à cy + 0,85 s : on la centre exactement.
        val cy = r.centerY() + 0.075f * s
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
        // Le pentagone va de cx − w à cx + w : centré exactement dans la touche.
        val cx = r.centerX()
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

    /** Un emoji : sans fond (comme Gboard), un fond seulement quand on l'appuie. */
    private fun drawPicto(canvas: Canvas, p: Placed) {
        if (p.key.label.isEmpty()) return
        if (p == pressed) {
            fill.color = pressedColor
            canvas.drawRoundRect(p.rect, radius, radius, fill)
        }
        text.color = textColor
        text.textSize = 24 * density
        val y = p.rect.centerY() - (text.descent() + text.ascent()) / 2
        canvas.drawText(p.key.label, p.rect.centerX(), y, text)
    }

    /** Entrée ↵ : flèche coudée (descend puis part vers la gauche), même trait que ⇧ et ⌫. */
    private fun drawEnterIcon(canvas: Canvas, r: RectF) {
        val s = 11 * density
        val cx = r.centerX()
        // De cy − 0,7 s (haut) à cy + 0,9 s (pointe de la flèche) : centrée.
        val cy = r.centerY() - 0.1f * s
        val head = s * 0.6f
        val path = Path().apply {
            moveTo(cx + s, cy - 0.7f * s)
            lineTo(cx + s, cy + 0.3f * s)
            lineTo(cx - s, cy + 0.3f * s)
            moveTo(cx - s + head, cy + 0.3f * s - head)
            lineTo(cx - s, cy + 0.3f * s)
            lineTo(cx - s + head, cy + 0.3f * s + head)
        }
        icon.color = 0xFFFFFFFF.toInt() // sur le fond d'accent
        icon.style = Paint.Style.STROKE
        canvas.drawPath(path, icon)
    }

    /**
     * Icônes de la barre d'outils, dessinées au même trait que ⇧ ⌫ ↵ :
     * ⚙ réglages, 😊 emoji, ↶ annuler, 📋 coller.
     */
    private fun drawTool(canvas: Canvas, p: Placed) {
        if (p == pressed) {
            fill.color = pressedColor
            canvas.drawRoundRect(p.rect, radius, radius, fill)
        }
        val c = p.rect.centerX()
        val cy = p.rect.centerY()
        val d = density
        icon.color = textColor
        icon.style = Paint.Style.STROKE
        when (p.key.target) {
            "settings" -> {
                canvas.drawCircle(c, cy, 6f * d, icon)
                canvas.drawCircle(c, cy, 2.2f * d, icon)
                for (i in 0 until 8) {
                    val a = i * Math.PI / 4
                    val x = kotlin.math.cos(a).toFloat()
                    val y = kotlin.math.sin(a).toFloat()
                    canvas.drawLine(c + x * 7.5f * d, cy + y * 7.5f * d, c + x * 10f * d, cy + y * 10f * d, icon)
                }
            }
            "emoji" -> {
                canvas.drawCircle(c, cy, 10f * d, icon)
                icon.style = Paint.Style.FILL
                canvas.drawCircle(c - 3.6f * d, cy - 2.6f * d, 1.3f * d, icon)
                canvas.drawCircle(c + 3.6f * d, cy - 2.6f * d, 1.3f * d, icon)
                icon.style = Paint.Style.STROKE
                canvas.drawArc(RectF(c - 5.5f * d, cy - 5.5f * d, c + 5.5f * d, cy + 5.5f * d), 30f, 120f, false, icon)
            }
            "undo" -> {
                // Arc qui revient en arrière, pointe de flèche à gauche.
                canvas.drawArc(RectF(c - 7f * d, cy - 7f * d, c + 9f * d, cy + 9f * d), 190f, 230f, false, icon)
                val tx = c - 6.9f * d
                val ty = cy - 0.8f * d
                canvas.drawLine(tx, ty, tx - 0.5f * d, ty - 6f * d, icon)
                canvas.drawLine(tx, ty, tx + 6f * d, ty - 1.2f * d, icon)
            }
            else -> {
                canvas.drawRoundRect(RectF(c - 7f * d, cy - 8f * d, c + 7f * d, cy + 9f * d), 2f * d, 2f * d, icon)
                canvas.drawRoundRect(RectF(c - 3.5f * d, cy - 10.5f * d, c + 3.5f * d, cy - 5.5f * d), 1.5f * d, 1.5f * d, icon)
                canvas.drawLine(c - 3.5f * d, cy, c + 3.5f * d, cy, icon)
                canvas.drawLine(c - 3.5f * d, cy + 4f * d, c + 3.5f * d, cy + 4f * d, icon)
            }
        }
    }

    private fun drawKey(canvas: Canvas, p: Placed) {
        val k = p.key
        if (k.type == KeyType.SUGGESTION) return drawSuggestion(canvas, p)
        if (k.type == KeyType.TOOL) return drawTool(canvas, p)
        if (k.type == KeyType.PICTO) return drawPicto(canvas, p)
        val activeTab = k.type == KeyType.PAGE && Emoji.pageOf(layout.id) == k.target
        fill.color = when {
            // Entrée : couleur d'accent, comme sur Gboard.
            k.type == KeyType.ENTER ->
                if (p == pressed && popupKey == null) enterPressedColor else accent
            p == pressed && popupKey == null -> pressedColor
            k.type == KeyType.SHIFT && shift != ShiftState.OFF -> accent
            activeTab -> accent
            isSpecial(k) -> specialColor
            else -> keyColor
        }
        val corner = if (isPill(k)) p.rect.height() / 2 else radius
        canvas.drawRoundRect(p.rect, corner, corner, fill)

        // ⇧ et ⌫ : icônes dessinées, épaisses comme sur Gboard (les caractères
        // de la police étaient trop fins — retour de l'auteur).
        if (k.type == KeyType.SHIFT) return drawShiftIcon(canvas, p.rect)
        if (k.type == KeyType.DELETE) return drawDeleteIcon(canvas, p.rect)
        if (k.type == KeyType.ENTER) return drawEnterIcon(canvas, p.rect)

        val shifted = shift != ShiftState.OFF
        val label = when (k.type) {
            KeyType.CHAR -> k.output(shifted)
            KeyType.SHIFT -> if (shift == ShiftState.LOCKED) "⇪" else "⇧"
            KeyType.SPACE -> if (layout.id == Layouts.CYRILLIC.id) "Нохчийн" else "Noxçiyŋ"
            KeyType.LETTERS -> "abc"
            // Le globe était peu parlant : on écrit l'alphabet visé (КИР / LAT).
            KeyType.GLOBE -> if (lettersLatin) "КИР" else "LAT"
            else -> k.label
        }
        text.color = when {
            k.type == KeyType.SHIFT && shift != ShiftState.OFF -> 0xFFFFFFFF.toInt()
            // КИР / LAT : bleu d'accent, plus discret que le texte des lettres.
            k.type == KeyType.GLOBE -> accent
            else -> textColor
        }
        text.textSize = when (k.type) {
            KeyType.CHAR -> 20 * density
            KeyType.SPACE -> 13 * density
            // Symboles des touches de fonction : ⇧ ⌫ ↵ étaient trop petits.
            KeyType.SHIFT -> 26 * density
            KeyType.DELETE, KeyType.ENTER -> 24 * density
            KeyType.PAGE, KeyType.EMOJI -> 19 * density
            KeyType.GLOBE -> 12 * density
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
        // Cyrillique : seulement en majuscules (leurs capitales montent jusqu'aux
        // chiffres de la rangée du haut, retour de l'auteur) ; en minuscules, le
        // rendu validé ne bouge pas.
        // Seules les LETTRES descendent : la virgule, le point et Ӏ (qui ne change
        // pas de forme en majuscules) restent fixes quand on appuie sur MAJ.
        // Et seulement dans une rangée qui porte des indices (chiffres, ё, ь) : la
        // rangée ф…э n'en a pas, ses lettres restent centrées dans la touche
        // (retour de l'auteur, 2026-11-28). Toute la rangée bouge ensemble, pour
        // que ses lettres restent alignées entre elles.
        val rowHasHint = layout.rows.firstOrNull { r -> r.any { it === k } }
            ?.any { it.variant != null || it.digit != null } == true
        val capsCyrillic = shifted && layout.id == Layouts.CYRILLIC.id && rowHasHint &&
            k.type == KeyType.CHAR && !k.caseless && k.label.firstOrNull()?.isLetter() == true
        val drop = if ((latin || capsCyrillic) && k.type == KeyType.CHAR) 2.5f * density else 0f
        text.isFakeBoldText = k.type == KeyType.GLOBE
        val y = p.rect.centerY() - (text.descent() + text.ascent()) / 2 + drop
        canvas.drawText(label, p.rect.centerX(), y, text)
        text.isFakeBoldText = false // le pinceau est partagé : ne pas durcir les autres textes

        // Indices d'appui long : la lettre dérivée en haut à droite, le chiffre
        // (rangée du haut, comme Gboard) en haut à gauche.
        hint.textSize = (if (latin) 9f else 10f) * density
        hint.color = hintColor
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
        // Une touche de la grille à moitié sortie de la zone n'est touchable
        // que dans la partie visible (sinon elle volerait les onglets).
        val inArea = { p: Placed -> !p.scrolls || (y >= scrollTop && y < scrollBottom) }
        placed.firstOrNull { it.rect.contains(x, y) && inArea(it) }?.let { return it }
        if (y < gap + stripSpace) return null
        return placed.filter { !isStripKey(it.key) && inArea(it) }
            .minByOrNull { (it.rect.centerX() - x).let { d -> d * d } + (it.rect.centerY() - y).let { d -> d * d } }
    }

    private val longPress = Runnable {
        val p = pressed ?: return@Runnable
        longPressed = true
        when {
            p.key.type == KeyType.GLOBE -> listener.onGlobeLongPress()
            p.key.longAction == KeyType.EMOJI -> listener.onEmojiPanel()
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
        if (hasLongPress(p.key)) handler.postDelayed(longPress, 350)
        invalidate()
    }

    private fun hasLongPress(k: Key) =
        k.popup.isNotEmpty() || k.type == KeyType.GLOBE || k.longAction != null

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
                downY = e.getY(i)
                scrollStart = scrollY
                scrolling = false
                press(p)
            }
            MotionEvent.ACTION_MOVE -> {
                val i = e.findPointerIndex(pointerId)
                if (i < 0) return true
                val x = e.getX(i)
                val y = e.getY(i)
                // Grille des emoji : un glissement vertical la fait défiler ;
                // l'emoji touché au départ n'est alors pas tapé.
                if (layout.scrollRows > 0 && (scrolling || pressed?.scrolls == true) &&
                    (scrolling || kotlin.math.abs(y - downY) > touchSlop)
                ) {
                    if (!scrolling) {
                        scrolling = true
                        handler.removeCallbacks(longPress)
                        pressed = null
                    }
                    scrollY = (scrollStart - (y - downY)).coerceIn(0f, maxScroll)
                    place()
                    invalidate()
                    return true
                }
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
                    if (p != null && p != pressed && !isStripKey(p.key) &&
                        pressed?.key?.let { isStripKey(it) } != true
                    ) {
                        handler.removeCallbacks(longPress)
                        handler.removeCallbacks(repeat)
                        pressed = p
                        if (hasLongPress(p.key)) handler.postDelayed(longPress, 350)
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
        KeyType.EMOJI -> listener.onEmojiPanel()
        KeyType.PAGE -> listener.onEmojiPage(k.target)
        KeyType.PICTO -> if (k.label.isNotEmpty()) listener.onEmojiPick(k.label) else Unit
        KeyType.TOOL -> when (k.target) {
            "settings" -> listener.onSettings()
            "emoji" -> listener.onEmojiPanel()
            "undo" -> listener.onUndo()
            else -> listener.onPaste()
        }
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
            KeyType.EMOJI -> context.getString(R.string.key_emoji)
            KeyType.PAGE -> context.getString(
                when (k.target) {
                    Emoji.RECENT -> R.string.emoji_recent
                    "smileys" -> R.string.emoji_smileys
                    "nature" -> R.string.emoji_nature
                    "food" -> R.string.emoji_food
                    "activities" -> R.string.emoji_activities
                    else -> R.string.emoji_symbols
                },
            )
            KeyType.PICTO -> k.label
            KeyType.TOOL -> context.getString(
                when (k.target) {
                    "settings" -> R.string.tool_settings
                    "emoji" -> R.string.key_emoji
                    "undo" -> R.string.tool_undo
                    else -> R.string.tool_paste
                },
            )
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
