#!/usr/bin/env bash
# Compila FefeTV con l'SDK di Android (quello di Android Studio va benissimo). Niente Gradle.
# Funziona su Windows (Git Bash), Linux e macOS.
#   bash build.sh        ->  build/fefetv-home.apk
# Variabili facoltative: ANDROID_HOME (SDK), JAVA_HOME (JDK 17+), FEFETV_KEYSTORE (chiave di firma).
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
source version.env
MIN_SDK=28   # Fire OS 7 e 8

if [[ -z ${ANDROID_HOME:-} ]]; then
  if [[ -n ${LOCALAPPDATA:-} ]]; then ANDROID_HOME="$(cygpath -u "$LOCALAPPDATA")/Android/Sdk"
  elif [[ -d $HOME/Library/Android/sdk ]]; then ANDROID_HOME="$HOME/Library/Android/sdk"
  else ANDROID_HOME="$HOME/Android/Sdk"; fi
fi
SDK=$ANDROID_HOME
JAR=$(ls -d "$SDK"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -1)
BT=$(ls -d "$SDK"/build-tools/*/ 2>/dev/null | sort -V | tail -1); BT=${BT%/}
[[ -f $JAR ]] || { echo "errore: nessuna piattaforma Android in $SDK/platforms" >&2; exit 1; }
[[ -d $BT ]] || { echo "errore: nessun build-tools in $SDK/build-tools" >&2; exit 1; }
ext() { if [[ -e $BT/$1.exe ]]; then echo "$BT/$1.exe"; elif [[ -e $BT/$1.bat ]]; then echo "$BT/$1.bat"; else echo "$BT/$1"; fi; }
AAPT2=$(ext aapt2); D8=$(ext d8); ZIPALIGN=$(ext zipalign); APKSIGNER=$(ext apksigner)

if [[ -z ${JAVA_HOME:-} || ! -x ${JAVA_HOME:-}/bin/javac && ! -x ${JAVA_HOME:-}/bin/javac.exe ]]; then
  for j in "/c/Program Files/Android/Android Studio/jbr" "/c/Program Files/Android/Android Studio/jre" \
           /c/Program\ Files/Java/jdk-* /c/Program\ Files/Eclipse\ Adoptium/jdk-*; do
    if [[ -x $j/bin/javac.exe || -x $j/bin/javac ]]; then JAVA_HOME=$j; break; fi
  done
fi
if [[ -z ${JAVA_HOME:-} ]] && command -v javac >/dev/null; then
  JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")
fi
[[ -n ${JAVA_HOME:-} ]] || { echo "errore: nessun JDK trovato (Android Studio ne include uno in jbr)" >&2; exit 1; }
export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"
echo "sdk:   $SDK"
echo "jar:   $JAR"
echo "tools: $BT"
echo "jdk:   $JAVA_HOME"

KEYSTORE=${FEFETV_KEYSTORE:-fefetv.keystore}
KEYPASS=fefetvhome
if [[ ! -f $KEYSTORE ]]; then
  keytool -genkeypair -keystore "$KEYSTORE" -alias home -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass "$KEYPASS" -keypass "$KEYPASS" -dname "CN=FefeTV" >/dev/null
  echo "creata la chiave di firma $KEYSTORE (tienila: gli aggiornamenti vanno firmati con questa)"
fi

# Percorsi relativi: gli strumenti sono programmi Windows e non capiscono /tmp di Git Bash.
WORK=build/work
rm -rf "$WORK"; mkdir -p "$WORK/gen" "$WORK/classes" "$WORK/dex"

"$AAPT2" compile --dir res -o "$WORK/res.zip"
"$AAPT2" link -I "$JAR" --manifest AndroidManifest.xml "$WORK/res.zip" \
  --min-sdk-version "$MIN_SDK" --target-sdk-version 28 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  --java "$WORK/gen" -o "$WORK/app.apk"

find src "$WORK/gen" -name '*.java' > "$WORK/sources.txt"
javac --release 8 -Xlint:-options -encoding UTF-8 -classpath "$JAR" -d "$WORK/classes" @"$WORK/sources.txt"

find "$WORK/classes" -name '*.class' > "$WORK/classes.txt"
"$D8" --release --min-api "$MIN_SDK" --lib "$JAR" --output "$WORK/dex" @"$WORK/classes.txt"
(cd "$WORK/dex" && jar --update --no-manifest --file ../app.apk classes.dex)

"$ZIPALIGN" -f -p 4 "$WORK/app.apk" "$WORK/aligned.apk"
"$APKSIGNER" sign --ks "$KEYSTORE" --ks-pass "pass:$KEYPASS" --key-pass "pass:$KEYPASS" \
  --out build/fefetv-home.apk "$WORK/aligned.apk"
"$APKSIGNER" verify build/fefetv-home.apk
echo "pronto: build/fefetv-home.apk ($(wc -c < build/fefetv-home.apk | tr -d ' ') byte)"
