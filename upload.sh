#!/data/data/com.termux/files/usr/bin/bash
# run from inside the project folder: bash upload.sh
set -e
cd "$(dirname "$0")"

CFG="$HOME/.deadzone_gh"
CONF="app/src/main/java/com/deadzone/game/Config.java"

for p in git curl; do
  command -v $p >/dev/null || pkg install -y $p
done

if [ ! -f "$CFG" ]; then
  read -p "GitHub username: " U
  read -p "Repo name [dead-zone]: " R
  R=${R:-dead-zone}
  read -s -p "GitHub token (needs repo + workflow): " T
  echo
  printf 'U=%s\nR=%s\nT=%s\n' "$U" "$R" "$T" > "$CFG"
  chmod 600 "$CFG"
fi
. "$CFG"

if grep -q "YOUR-SERVER" "$CONF"; then
  read -p "Relay server url (wss://...), Enter to skip: " S
  if [ -n "$S" ]; then
    sed -i "s|wss://[^\"]*|$S|" "$CONF"
  fi
fi

code=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: token $T" "https://api.github.com/repos/$U/$R")
if [ "$code" = "404" ]; then
  echo "creating repo $R"
  curl -s -o /dev/null -X POST -H "Authorization: token $T" \
    -d "{\"name\":\"$R\",\"private\":false}" https://api.github.com/user/repos
fi

[ -d .git ] || git init -q -b main
git config user.name >/dev/null || git config user.name "$U"
git config user.email >/dev/null || git config user.email "$U@users.noreply.github.com"
git add -A
git commit -q -m "update" || echo "nothing new to commit"
git push "https://$U:$T@github.com/$U/$R.git" HEAD:main

echo
echo "pushed. build is running:"
echo "https://github.com/$U/$R/actions"
echo "apk will show up here when it finishes:"
echo "https://github.com/$U/$R/releases"
