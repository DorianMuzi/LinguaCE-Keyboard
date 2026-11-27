# LinguaCE Keyboard

Clavier Android pour le **tchétchène** : alphabet **latin tchétchène** et
cyrillique avec la touche **Ӏ**. Un produit de l'écosystème
[LinguaCE](https://github.com/DorianMuzi/LinguaCE).

## Vie privée

Ce clavier **ne demande aucune permission**, pas même l'accès à Internet :
Android garantit ainsi qu'il ne peut rien envoyer. Il ne garde rien de ce que
vous tapez ; il mémorise seulement la dernière disposition choisie (latin ou
cyrillique). Le code est ouvert pour que chacun puisse le vérifier.

**Presse-papiers** — le bouton 📋 colle ce qui est copié, et rien d'autre : le
clavier ne lit le presse-papiers que quand vous appuyez dessus, ne le surveille
pas, n'en garde aucun historique, et n'a de toute façon aucun accès à Internet.
**Annuler** (↶) s'appuie sur un historique en mémoire vive seulement : jamais
écrit sur le disque, vidé à chaque changement de champ, vide dans les champs de
mot de passe.

Vérifier vous-même un APK :

```
aapt2 dump permissions app-debug.apk
```

## Dispositions (v0.1)

**Latin** — QWERTY, avec **ə** (à droite de l, comme sur le clavier
azerbaïdjanais) et **ç** sur leurs propres touches. **w** garde sa place pour
les mots d'origine étrangère. Les autres lettres à diacritique sont sur
l'appui long de leur lettre de base :

```
 q  w  e  r  t  y  u  i  o  p
 a  s  d  f  g  h  j  k  l  ə
 ⇧  z  x  c  v  b  n  m  ç  ⌫
 КИР 123  ,     espace     .  ↵
```

Appui long : a → ä, c → ċ, ç → ç̇, g → ġ, k → k̇, n → ŋ, o → ö, p → ṗ,
q → q̇, s → ş, t → ṫ, u → ü, x → ẋ, z → ƶ. Les suggestions aident aussi :
taper « noxciyn » propose « noxçiyŋ ». **ə → w** : w ne fait pas
partie de l'alphabet tchétchène, mais reste disponible pour les mots
d'origine étrangère (WhatsApp, Wi-Fi, Wagner…).

**Cyrillique** — ЙЦУКЕН, touche **Ӏ** (U+04C0) dans la rangée du bas ;
ъ par appui long sur ь, ё sur е.

**КИР / LAT** (l'alphabet visé) passe du latin au cyrillique ; un appui long sur cette touche ouvre le choix du
clavier système. **⇧** : une majuscule ; deux appuis : verrouillage.

## Emoji

Appui long sur la virgule (ou la touche 😊 du clavier de symboles) : le panneau
des emoji, avec une grille qui défile, des onglets (récents, smileys, animaux et
nature, nourriture, activités / voyages / objets, symboles) et ⌫. Les emoji que
la police du téléphone ne sait pas dessiner sont écartés. Les récents restent
sur le téléphone, comme les mots appris (rien n'est mémorisé dans les champs de
mot de passe, d'adresse ou en navigation privée).

## Réglages

La **barre d'outils** (⚙ réglages, 😊 emoji, ↶ annuler, 📋 coller) occupe la bande
du haut quand aucun mot n'est proposé ; dès qu'un mot est reconnu, les
suggestions prennent sa place. Elle se désactive dans les réglages.

Dans l'app LinguaCE Keyboard : disposition latine (QWERTY / AZERTY), **thème**
(comme le téléphone / clair / sombre), **hauteur des touches** (petite /
normale / grande), son et vibration, **majuscule en début de phrase**,
**double espace pour un point**. Tout reste sur le téléphone.

## Accessibilité

Le clavier fonctionne avec **TalkBack** : chaque touche a un nom lu dans la
langue du téléphone (« Effacer », « Majuscule », « Palotchka Ӏ »…). On glisse
le doigt pour entendre les touches ; la lettre est tapée quand on le lève (ou
par double appui). Les lettres de l'appui long sont proposées comme actions
TalkBack sur leur touche (« Taper ċ », « Taper w »…).

## Encodage

Les lettres suivent la spécification de l'alphabet du LinguaCE Core, comme
LinguaCE App : texte NFC ; **ç̇, k̇, q̇** = lettre + U+0307 (point en chef
combinant), émis d'un seul coup et effacés d'un seul coup par ⌫ ; **ş** à
cédille (U+015F), **ƶ** barré (U+01B6), **ə** schwa (U+0259).

## Construire

JDK 17 ou plus, SDK Android 35.

```
gradlew test assembleDebug
```

L'APK se trouve dans `app/build/outputs/apk/debug/`.

Tests d'accessibilité, sur un appareil ou un émulateur branché :

```
gradlew assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.linguace.keyboard.test/androidx.test.runner.AndroidJUnitRunner
```

## Publier (Google Play)

Google Play demande un fichier `.aab` signé avec une clé de dépôt. Cette clé reste
sur le poste de la personne qui publie et n'entre **jamais** dans Git
(`keystore.properties` et `*.jks` sont ignorés).

1. Créer la clé (une seule fois ; `keytool` demande les mots de passe) :
   ```
   keytool -genkeypair -v -keystore linguace-release.jks -alias linguace -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Créer `keystore.properties` à la racine du projet, ou de préférence dans
   `~/.linguace/` (hors du dossier du projet, donc hors d'un dossier synchronisé
   dans le cloud ; `storeFile` y est alors relatif à ce dossier) :
   ```
   storeFile=linguace-release.jks
   storePassword=…
   keyAlias=linguace
   keyPassword=…
   ```
3. `gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`.
   Sans `keystore.properties`, la version « release » est signée avec la clé de
   débogage : utile pour la tester sur l'émulateur, refusée par Google Play.

Les textes de la fiche (FR / EN / RU) sont dans [`store/`](store) et la
[politique de confidentialité](PRIVACY.md) est la page à indiquer à Google Play.
À chaque nouvelle publication, augmenter `versionCode` dans `app/build.gradle.kts`.

## Licence

[Apache 2.0](LICENSE). Le système de translittération Chechen Latin Script et
le lexique LinguaCE ne font pas partie de ce dépôt et ne sont pas couverts par
cette licence.
