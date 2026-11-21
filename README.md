# LinguaCE Keyboard

Clavier Android pour le **tchétchène** : alphabet **latin tchétchène** et
cyrillique avec la touche **Ӏ**. Un produit de l'écosystème
[LinguaCE](https://github.com/DorianMuzi/LinguaCE).

## Vie privée

Ce clavier **ne demande aucune permission**, pas même l'accès à Internet :
Android garantit ainsi qu'il ne peut rien envoyer. Il ne garde rien de ce que
vous tapez ; il mémorise seulement la dernière disposition choisie (latin ou
cyrillique). Le code est ouvert pour que chacun puisse le vérifier.

Vérifier vous-même un APK :

```
aapt2 dump permissions app-debug.apk
```

## Dispositions (v0.1)

**Latin** — QWERTY ; **w**, absent de l'alphabet, est remplacé par **ə**.
Les lettres à diacritique sont sur l'appui long de leur lettre de base :

```
 q  ə  e  r  t  y  u  i  o  p
  a  s  d  f  g  h  j  k  l
 ⇧   z  x  c  v  b  n  m   ⌫
 🌐 123  ,     espace     .  ↵
```

Appui long : a → ä, c → ċ ç ç̇, e → ə, g → ġ, k → k̇, n → ŋ, o → ö,
p → ṗ, q → q̇, s → ş, t → ṫ, u → ü, x → ẋ, z → ƶ. Les suggestions aident aussi :
taper « noxciyn » propose « noxçiyŋ ». **ə → w** : w ne fait pas
partie de l'alphabet tchétchène, mais reste disponible pour les mots
d'origine étrangère (WhatsApp, Wi-Fi, Wagner…).

**Cyrillique** — ЙЦУКЕН, touche **Ӏ** (U+04C0) dans la rangée du bas ;
ъ par appui long sur ь, ё sur е.

**🌐** passe du latin au cyrillique ; un appui long sur 🌐 ouvre le choix du
clavier système. **⇧** : une majuscule ; deux appuis : verrouillage.

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

## Licence

[Apache 2.0](LICENSE). Le système de translittération Chechen Latin Script et
le lexique LinguaCE ne font pas partie de ce dépôt et ne sont pas couverts par
cette licence.
