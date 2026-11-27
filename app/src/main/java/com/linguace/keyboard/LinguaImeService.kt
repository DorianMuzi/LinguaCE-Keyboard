package com.linguace.keyboard

import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import java.io.File

/**
 * Le service de saisie. Il ne transmet rien : seuls la disposition choisie et
 * les mots appris pour les suggestions sont gardés, dans le stockage privé de
 * l'app, sur le téléphone (pas de sauvegarde dans le cloud : allowBackup=false).
 */
class LinguaImeService : InputMethodService(), KeyboardView.Listener {

    private var view: KeyboardView? = null
    private var letters: Layout = Layouts.LATIN
    private var lastShiftTap = 0L

    private val prefs by lazy { getSharedPreferences("keyboard", Context.MODE_PRIVATE) }

    private lateinit var words: WordStore
    private var wordsEpoch = 0

    /** Suggestions affichées dans ce champ ; apprentissage autorisé dans ce champ. */
    private var suggest = true
    private var learn = true

    /** Double espace → point dans ce champ (texte libre, pas une adresse). */
    private var punctuate = true

    /** Réglages lus à chaque ouverture du clavier. */
    private var autoCaps = true
    private var doubleSpace = true

    override fun onCreate() {
        super.onCreate()
        letters = Layouts.byId(prefs.getString("layout", null), isAzerty(this))
        val seed = runCatching {
            assets.open(SEED_FILE).bufferedReader().use { it.readText() }
        }.getOrDefault("")
        words = WordStore(WordStore.parseSeed(seed))
        loadLearned()
    }

    override fun onCreateInputView(): View =
        KeyboardView(this, this).also {
            it.layout = letters
            // Bande apparue / disparue : seule la zone visible change, pas la
            // hauteur ; un nouveau passage de mise en page recalcule les insets.
            it.onContentTopChanged = { it.requestLayout() }
            view = it
        }

    /**
     * La fenêtre du clavier garde toujours la même hauteur (bande de
     * suggestions comprise), mais on n'annonce comme visible et touchable que
     * la partie réellement dessinée : sans suggestion, la bande est
     * transparente, l'app ne se redimensionne pas pour elle et les touchers
     * y passent vers l'app.
     */
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val v = view ?: return
        if (!v.isShown) return
        val loc = IntArray(2)
        v.getLocationInWindow(loc)
        val top = loc[1] + v.contentTop
        outInsets.contentTopInsets = top
        outInsets.visibleTopInsets = top
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.set(0, top, loc[0] + v.width, loc[1] + v.height)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Mots effacés depuis l'écran d'installation : on repart de zéro.
        if (prefs.getInt(EPOCH_KEY, 0) != wordsEpoch) loadLearned()
        configureField(info)
        selStart = info?.initialSelStart ?: 0
        selEnd = info?.initialSelEnd ?: 0
        // QWERTY / AZERTY a pu changer dans les réglages entre-temps.
        if (letters.isLatin) letters = Layouts.latin(isAzerty(this))
        view?.layout = letters
        view?.shift = ShiftState.OFF
        view?.showStrip = suggest
        view?.soundOn = prefs.getBoolean(SOUND_KEY, false)
        view?.vibrateOn = prefs.getBoolean(VIBRATE_KEY, true)
        view?.theme = Theme.of(prefs.getString(THEME_KEY, null))
        view?.heightScale = KeyHeight.of(prefs.getString(HEIGHT_KEY, null)).scale
        view?.showToolbar = prefs.getBoolean(TOOLBAR_KEY, true)
        applyNavigationBarIcons()
        undo.clear()
        autoCaps = prefs.getBoolean(AUTOCAPS_KEY, true)
        doubleSpace = prefs.getBoolean(DOUBLE_SPACE_KEY, true)
        scheduleRefresh()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        applyNavigationBarIcons()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Le téléphone passe de clair à sombre (ou inverse) : le thème « comme le téléphone » suit.
        view?.invalidate()
        applyNavigationBarIcons()
    }

    /**
     * Icônes de la barre de navigation (⌄, changement de clavier, barre de
     * geste) : sombres sur un clavier clair, claires sur un clavier sombre.
     * Par défaut elles suivent le thème du TÉLÉPHONE : un clavier clair sur un
     * téléphone sombre les rendait blanches sur fond clair, presque invisibles
     * (retour de l'auteur).
     */
    private fun applyNavigationBarIcons() {
        val w = window?.window ?: return
        val light = view?.isDark == false
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            w.insetsController?.setSystemBarsAppearance(
                if (light) android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS else 0,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            )
        } else {
            @Suppress("DEPRECATION")
            val flags = w.decorView.systemUiVisibility
            @Suppress("DEPRECATION")
            w.decorView.systemUiVisibility = if (light) {
                flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else {
                flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            }
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        undo.clear()
        saveLearned()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        saveLearned()
        super.onDestroy()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        selStart = newSelStart
        selEnd = newSelEnd
        scheduleRefresh()
    }

    /**
     * Sélection connue, tenue à jour par le système. Frappe rapide : on ne
     * demande JAMAIS à l'app « y a-t-il du texte sélectionné ? » à chaque
     * touche (un aller-retour bloquant avec l'app ralentit nettement la
     * frappe, constaté en mesurant contre Gboard).
     */
    private var selStart = 0
    private var selEnd = 0
    private val hasSelection: Boolean get() = selStart != selEnd

    /**
     * Jamais de suggestions dans un mot de passe ni si l'app les refuse ;
     * jamais d'apprentissage non plus dans une adresse e-mail ou web, ni si
     * l'app le demande (mode incognito : IME_FLAG_NO_PERSONALIZED_LEARNING).
     */
    private fun configureField(info: EditorInfo?) {
        val type = info?.inputType ?: 0
        val cls = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION
        val password = (cls == InputType.TYPE_CLASS_TEXT && variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )) || (cls == InputType.TYPE_CLASS_NUMBER &&
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        val noSuggestions = type and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0
        suggest = cls == InputType.TYPE_CLASS_TEXT && !password && !noSuggestions
        val address = variation in setOf(
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI,
        )
        val incognito = (info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0
        learn = suggest && !address && !incognito
        punctuate = suggest && !address
    }

    // ─── Saisie ─────────────────────────────────────────────────
    override fun onText(text: String) {
        val ic = currentInputConnection ?: return
        // Un séparateur (espace, ponctuation) termine le mot : on l'apprend.
        if (text.none { it.isLetter() || Character.getType(it) == Character.NON_SPACING_MARK.toInt() }) {
            learnCurrentWord()
        }
        if (text == " " && doubleSpace && punctuate &&
            TextRules.doubleSpaceToPeriod(ic.getTextBeforeCursor(4, 0) ?: "")
        ) {
            // « mot␣ » + espace → « mot.␣ »
            ic.beginBatchEdit()
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            ic.endBatchEdit()
            remember(" ", ". ")
        } else {
            // Un texte sélectionné est remplacé : ↶ le remettra.
            val replaced = if (hasSelection) ic.getSelectedText(0)?.toString().orEmpty() else ""
            ic.commitText(text, 1)
            remember(replaced, text)
        }
        if (view?.shift == ShiftState.ONCE) view?.shift = ShiftState.OFF
        scheduleRefresh()
    }

    override fun onSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        val typed = WordStore.currentWord(ic.getTextBeforeCursor(MAX_WORD, 0) ?: "")
        ic.beginBatchEdit()
        if (typed.isNotEmpty()) ic.deleteSurroundingText(typed.length, 0)
        ic.commitText("$word ", 1)
        ic.endBatchEdit()
        remember(typed, "$word ")
        if (learn) words.learn(word)
        if (view?.shift == ShiftState.ONCE) view?.shift = ShiftState.OFF
        scheduleRefresh()
    }

    override fun onDelete() {
        val ic = currentInputConnection ?: return
        val selected = if (hasSelection) ic.getSelectedText(0) else null
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
            remember(selected.toString(), "")
        } else {
            val before = ic.getTextBeforeCursor(16, 0) ?: ""
            val n = Graphemes.lastLength(before)
            if (n > 0) {
                ic.deleteSurroundingText(n, 0)
                remember(before.takeLast(n).toString(), "")
            } else {
                // Champ vide ou texte inaccessible : on laisse l'app décider.
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            }
        }
        scheduleRefresh()
    }

    override fun onEnter() {
        val ic = currentInputConnection ?: return
        learnCurrentWord()
        val info = currentInputEditorInfo
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val noAction = (info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        if (!noAction && action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED
        ) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
            remember("", "\n")
        }
        scheduleRefresh()
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
        letters = if (letters.isLatin) Layouts.CYRILLIC else Layouts.latin(isAzerty(this))
        prefs.edit().putString("layout", if (letters.isLatin) "latin" else letters.id).apply()
        view?.layout = letters
    }

    /** Appui long sur КИР / LAT : changer de clavier (celui du système, par exemple). */
    override fun onGlobeLongPress() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    override fun onSymbols() {
        view?.layout = Layouts.SYMBOLS
    }

    override fun onLetters() {
        view?.layout = letters
    }

    // ─── Barre d'outils : réglages, annuler, coller ─────────────
    /**
     * Historique de ↶, en mémoire seulement (voir [UndoStack]). Rien n'y
     * entre dans un champ de mot de passe ni dans un champ sans texte.
     */
    private val undo = UndoStack()

    private fun remember(removed: String, inserted: String) {
        if (suggest) undo.record(removed, inserted)
    }

    override fun onSettings() {
        startActivity(
            Intent(this, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** ↶ : défait la dernière saisie (le mot, l'emoji, la suggestion, l'effacement…). */
    override fun onUndo() {
        val ic = currentInputConnection ?: return
        val edit = undo.peek() ?: return
        if (edit.inserted.isNotEmpty()) {
            val before = ic.getTextBeforeCursor(edit.inserted.length, 0)?.toString()
            // Le texte a changé ailleurs (curseur déplacé, autre app) : on ne touche à rien.
            if (before != edit.inserted) {
                undo.clear()
                return
            }
        }
        undo.pop()
        ic.beginBatchEdit()
        if (edit.inserted.isNotEmpty()) ic.deleteSurroundingText(edit.inserted.length, 0)
        if (edit.removed.isNotEmpty()) ic.commitText(edit.removed, 1)
        ic.endBatchEdit()
        scheduleRefresh()
    }

    /**
     * 📋 : colle le presse-papiers. Il n'est lu QUE quand on appuie sur ce
     * bouton : le clavier ne le surveille pas, n'en garde aucun historique et
     * n'a de toute façon aucun accès à Internet.
     */
    override fun onPaste() {
        val ic = currentInputConnection ?: return
        val clip = (getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
            .primaryClip
        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        if (text.isNullOrEmpty()) return
        learnCurrentWord()
        val replaced = ic.getSelectedText(0)?.toString().orEmpty()
        ic.commitText(text, 1)
        remember(replaced, text)
        if (view?.shift == ShiftState.ONCE) view?.shift = ShiftState.OFF
        scheduleRefresh()
    }

    // ─── Emoji ──────────────────────────────────────────────────
    private var recentEmoji: List<String> = emptyList()
    private var emojiPage = Emoji.CATEGORIES.first().id
    private val emojiLayouts = HashMap<String, Layout>()

    override fun onEmojiPanel() {
        recentEmoji = Emoji.parse(prefs.getString(RECENT_EMOJI_KEY, null))
        // Les récents d'abord s'il y en a ; sinon la dernière catégorie regardée.
        showEmojiPage(
            if (recentEmoji.isNotEmpty()) Emoji.RECENT
            else emojiPage.takeIf { it != Emoji.RECENT } ?: Emoji.firstPage(recentEmoji),
        )
    }

    override fun onEmojiPage(page: String) = showEmojiPage(page)

    private fun showEmojiPage(page: String) {
        emojiPage = page
        view?.layout = emojiLayout(page)
    }

    /**
     * La page [page], sans les emoji que la police du téléphone ne sait pas
     * dessiner (sur un téléphone ancien, ils s'afficheraient en carrés).
     */
    private fun emojiLayout(page: String): Layout {
        if (page == Emoji.RECENT) return Emoji.layout(page, supported(Emoji.withDefaults(recentEmoji)))
        return emojiLayouts.getOrPut(page) {
            Emoji.layout(page, supported(Emoji.category(page)?.emojis.orEmpty()))
        }
    }

    private val glyphPaint by lazy { android.graphics.Paint() }
    private fun supported(list: List<String>) = list.filter { glyphPaint.hasGlyph(it) }

    override fun onEmojiPick(emoji: String) {
        val ic = currentInputConnection ?: return
        // Un emoji ferme le mot en cours, comme un séparateur.
        learnCurrentWord()
        ic.commitText(emoji, 1)
        remember("", emoji)
        if (learn) {
            recentEmoji = Emoji.pushRecent(recentEmoji, emoji)
            prefs.edit().putString(RECENT_EMOJI_KEY, Emoji.serialize(recentEmoji)).apply()
        }
        if (view?.shift == ShiftState.ONCE) view?.shift = ShiftState.OFF
        scheduleRefresh()
    }

    // ─── Après chaque touche ────────────────────────────────────
    // Majuscule automatique et suggestions demandent le texte au champ (un
    // aller-retour avec l'app) : on les fait juste après, une seule fois même
    // si plusieurs touches arrivent, pour que la lettre parte tout de suite.
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = Runnable {
        autoShift()
        updateSuggestions()
    }

    private fun scheduleRefresh() {
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }

    // ─── Suggestions ────────────────────────────────────────────
    private fun updateSuggestions() {
        val v = view ?: return
        if (!suggest) {
            v.suggestions = emptyList()
            return
        }
        val before = currentInputConnection?.getTextBeforeCursor(MAX_WORD, 0) ?: ""
        v.suggestions = words.suggest(WordStore.currentWord(before))
    }

    private fun learnCurrentWord() {
        if (!learn) return
        val before = currentInputConnection?.getTextBeforeCursor(MAX_WORD, 0) ?: return
        words.learn(WordStore.currentWord(before))
    }

    private val learnedFile: File get() = File(filesDir, LEARNED_FILE)

    private fun loadLearned() {
        wordsEpoch = prefs.getInt(EPOCH_KEY, 0)
        val text = runCatching { learnedFile.readText() }.getOrDefault("")
        words.load(text)
    }

    private fun saveLearned() {
        if (!::words.isInitialized) return
        // Effacés entre-temps depuis l'écran d'installation : ne pas réécrire.
        if (prefs.getInt(EPOCH_KEY, 0) != wordsEpoch) return
        runCatching { learnedFile.writeText(words.serialize()) }
    }

    /** Majuscule automatique en début de phrase, si le champ la demande. */
    private fun autoShift() {
        val v = view ?: return
        if (v.shift == ShiftState.LOCKED) return
        // Réglage désactivé : la majuscule ne vient que de ⇧.
        if (!autoCaps) return
        val info = currentInputEditorInfo ?: return
        val ic = currentInputConnection ?: return
        val type = info.inputType
        val isText = type and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT
        val caps = isText && ic.getCursorCapsMode(type) != 0
        v.shift = if (caps) ShiftState.ONCE else ShiftState.OFF
    }

    companion object {
        const val SEED_FILE = "seed_words.tsv"
        const val LEARNED_FILE = "learned_words.tsv"
        const val EPOCH_KEY = "words_epoch"
        const val SOUND_KEY = "sound"
        const val LATIN_KEY = "latin_layout"

        /**
         * Latin en AZERTY ? Choix fait dans les réglages ; à défaut, AZERTY si
         * le téléphone est en français (France, Belgique…), sinon QWERTY.
         */
        fun isAzerty(context: Context): Boolean {
            val saved = context.getSharedPreferences("keyboard", Context.MODE_PRIVATE)
                .getString(LATIN_KEY, null)
            return when (saved) {
                "azerty" -> true
                "qwerty" -> false
                else -> context.resources.configuration.locales[0].language == "fr"
            }
        }
        const val VIBRATE_KEY = "vibrate"
        const val RECENT_EMOJI_KEY = "recent_emoji"
        const val TOOLBAR_KEY = "toolbar"
        const val THEME_KEY = "theme"
        const val HEIGHT_KEY = "key_height"
        const val AUTOCAPS_KEY = "autocaps"
        const val DOUBLE_SPACE_KEY = "double_space"
        private const val MAX_WORD = 48

        /** Efface les mots appris (écran d'installation). */
        fun clearLearned(context: Context) {
            File(context.filesDir, LEARNED_FILE).delete()
            val prefs = context.getSharedPreferences("keyboard", Context.MODE_PRIVATE)
            prefs.edit().putInt(EPOCH_KEY, prefs.getInt(EPOCH_KEY, 0) + 1).apply()
        }

        /** Mots appris (dictionnaire personnel), les plus utilisés d'abord. */
        fun learnedEntries(context: Context): List<Pair<String, Int>> =
            WordStore().apply {
                load(runCatching { File(context.filesDir, LEARNED_FILE).readText() }.getOrDefault(""))
            }.entries()

        /**
         * Modifie les mots appris depuis l'écran du dictionnaire. Le clavier
         * les recharge à sa prochaine ouverture (même mécanisme que
         * l'effacement : le compteur d'époque change).
         */
        fun editLearned(context: Context, edit: (WordStore) -> Unit) {
            val file = File(context.filesDir, LEARNED_FILE)
            val store = WordStore()
            store.load(runCatching { file.readText() }.getOrDefault(""))
            edit(store)
            runCatching { file.writeText(store.serialize()) }
            val prefs = context.getSharedPreferences("keyboard", Context.MODE_PRIVATE)
            prefs.edit().putInt(EPOCH_KEY, prefs.getInt(EPOCH_KEY, 0) + 1).apply()
        }

        /** Mots appris absents de la liste de base (à proposer au projet). */
        fun newWords(context: Context): List<String> {
            val seed = runCatching {
                context.assets.open(SEED_FILE).bufferedReader().use { it.readText() }
            }.getOrDefault("")
            val store = WordStore(WordStore.parseSeed(seed))
            store.load(runCatching { File(context.filesDir, LEARNED_FILE).readText() }.getOrDefault(""))
            return store.newWords()
        }

        fun learnedCount(context: Context): Int =
            runCatching {
                File(context.filesDir, LEARNED_FILE).readLines().count { it.contains('\t') }
            }.getOrDefault(0)
    }
}
