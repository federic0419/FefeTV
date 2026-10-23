# FefeTV

Un launcher per **Amazon Fire TV** (Fire OS 7 e 8) che sostituisce la Home di Amazon:
niente pubblicità, niente righe sponsorizzate, solo le tue app.

- Barra laterale: Home, Cerca, App, Film e Serie, Giochi, File, Impostazioni
- Saluto personalizzato, orologio, data, Wi‑Fi e meteo della tua città
- Le tue app in prima fila (con i banner veri), **Riprendi** con le ultime aperte,
  **Giochi e utility**, tutte le altre
- Tieni premuto **OK** su un'app: aggiungi/togli dai preferiti, sposta, cambia gruppo, nascondi
- 10 colori del tema, sfondo con paesaggio notturno disegnato oppure una tua foto
- Suono di scorrimento (disattivabile)
- **Cambia launcher** dalle impostazioni: FefeTV, dumbtv home o la Home Amazon originale
- Niente root, nulla scritto in `/system`: si torna alla Home Amazon con un comando

Basato sul launcher di [dumbtv](https://github.com/humblemuzzu/dumbtv) (MIT): da lì vengono
il meccanismo che prende il tasto HOME, l'elenco delle app e il file delle impostazioni.
dumbtv fa anche il debloat della Fire TV: conviene usarli insieme.

## Cosa serve

- La Fire TV con **Opzioni sviluppatore → Debug ADB** attivo
  (Impostazioni → La mia Fire TV → Informazioni → premi OK 7 volte sul nome del dispositivo)
- Un PC sulla stessa rete con:
  - [Android Platform-Tools](https://developer.android.com/tools/releases/platform-tools) (`adb`)
  - l'Android SDK (quello che installa Android Studio va bene) e un JDK 17+
  - su Windows, **Git Bash** (viene con Git per Windows)

## Installazione

```sh
git clone https://github.com/federic0419/FefeTV.git
cd FefeTV
adb connect <ip-della-tv>:5555      # sulla TV: "Consenti sempre" + OK
export ADB_SERIAL=<ip-della-tv>:5555
bash install.sh                     # compila, installa, prende il tasto HOME
```

Da PowerShell su Windows, al posto delle ultime due righe:

```powershell
$env:ADB_SERIAL = "<ip-della-tv>:5555"
& "C:\Program Files\Git\bin\bash.exe" .\install.sh
```

Per tornare alla Home Amazon: `bash install.sh --undo` (FefeTV resta installato), oppure
**Impostazioni → Launcher** dentro FefeTV.

## Impostazioni

Quasi tutto si cambia dalla TV in **Impostazioni**. Tutto finisce in un file di testo sulla TV:

```
/sdcard/Android/data/tv.fefetv.home/files/home.conf
```

| Chiave | Esempio | Cosa fa |
|---|---|---|
| `fefe.name` | `Federico` | il nome nel saluto |
| `fefe.city`, `fefe.lat`, `fefe.lon` | `Imola`, `44.3533`, `11.7148` | città del meteo |
| `fefe.accent` | `Viola` oppure `#9B59FF` | colore del tema |
| `fefe.favset` + `fefe.fav` | `1` + `com.amazon.avl.ftv,org.xbmc.kodi` | le app in "Le tue app", in ordine |
| `fefe.group.<pacchetto>` | `video` / `games` / `file` / `none` | in quale pagina compare un'app |
| `fefe.sound` | `off` | spegne il suono di scorrimento |
| `apps.hide` | `com.amazon.firetv.troubleshooting` | app nascoste |

Per modificarlo dal PC:

```sh
adb pull /sdcard/Android/data/tv.fefetv.home/files/home.conf
# modifica home.conf
adb push home.conf /sdcard/Android/data/tv.fefetv.home/files/home.conf
```

FefeTV lo rilegge quando torni alla Home. Per lo sfondo, copia una foto come `wallpaper.jpg`
nella stessa cartella.

## Permessi

| Permesso | Perché |
|---|---|
| `INTERNET` | solo il meteo, da [Open-Meteo](https://open-meteo.com) (gratis, senza account) |
| `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | l'icona del Wi‑Fi |
| `WRITE_SECURE_SETTINGS` | "Cambia launcher"; lo concede solo `install.sh` via adb |

Il tasto HOME passa da un servizio di accessibilità (`Guard`) che guarda solo quando si apre la
Home di Amazon e la sostituisce: non legge il contenuto dello schermo.

## Limiti

- Fire OS non dà a un launcher esterno l'elenco di cosa stai guardando nelle app, né i
  consigli di Amazon: per questo c'è "Riprendi" con le ultime app aperte.
- Solo Fire OS 7 e 8 (Android 9+). I modelli con Vega OS non sono Android.
- Ogni PC firma l'app con la sua chiave: per aggiornarla da un altro PC usa la stessa
  chiave (`FEFETV_KEYSTORE=/percorso/fefetv.keystore`), altrimenti va disinstallata prima.

---

*FefeTV is a home screen for Amazon Fire TV (Fire OS 7/8), built on dumbtv's launcher: no ads,
your apps first, sidebar navigation, weather, themes, and a launcher switch. Build and install
with `bash install.sh` (needs adb, the Android SDK and a JDK). MIT licensed.*
