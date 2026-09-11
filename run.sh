#!/data/data/com.termux/files/usr/bin/bash
# dpt-unpacker -- run the static DPT-Shell unpacker in Termux
# usage: bash run.sh app.apk [options]   -- everything automatic
set -e
cd "$(dirname "$0")"

# auto-detect apktool for LSParanoid / rebuild stages
if [ -z "$DPT_APKTOOL_JAR" ]; then
  for cand in "$PWD/apktool.jar" "${APKTOOL_HOME:-$HOME}/apktool.jar"; do
    if [ -f "$cand" ]; then
      export DPT_APKTOOL_JAR="$cand"
      break
    fi
  done
fi

if [ ! -d build/install/dpt-unpacker ]; then
  echo "[run.sh] no build found -> building"
  if command -v gradle >/dev/null 2>&1; then
    gradle --no-daemon installDist
  else
    ./gradlew --no-daemon installDist
  fi
fi

CP=""
for j in build/install/dpt-unpacker/lib/*.jar; do
  CP="$CP:$j"
done

# one-shot mode: unless an explicit --mode/--analyze/--inspect/--help is given,
# route every invocation through --mode auto-all (profile -> extract embedded
# apk if any -> auto-pick pipeline -> unpack/rebuild).
AUTO_ALL=1
for a in "$@"; do
  case "$a" in
    --mode|--analyze|--inspect|--help|-h) AUTO_ALL=0 ;;
  esac
done
if [ "$AUTO_ALL" -eq 1 ]; then
  set -- "$@" --mode auto-all
fi

exec java -Xmx1g -cp "$CP" com.dpt.unpack.MainKt "$@"