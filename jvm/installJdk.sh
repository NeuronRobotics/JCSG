#!/usr/bin/env bash
# Downloads the pinned Zulu JavaFX JDK for this OS/arch from jvm.json (single source
# of truth) and installs it to ~/bin/CaDoodle-ApplicationInstall/<archive-name>/ so
# that <install>/bin/java exists. Zulu's macOS zips nest the JDK home in a
# <name>/zulu-25.jdk/Contents/Home bundle behind top-level symlinks; the Home
# directory's content is copied (dereferenced) to the install root so the real
# files are in place. Verifies the install with java -version and the pinned
# major version; exits non-zero if the JVM is missing or broken.
# Re-run any time jvm.json changes: an existing install is replaced when the pinned
# archive differs. Override the install root with JDK_INSTALL_ROOT, the jvm.json key
# with JVM_KEY, and the expected major version with JVM_MAJOR_VERSION.
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)
JVM_SCRIPTS=$SCRIPT_DIR
[ -f "$JVM_SCRIPTS/jvm.json" ] || { echo "jvm.json not found at $JVM_SCRIPTS" >&2; exit 1; }

JVM=$(bash "$JVM_SCRIPTS/getJdkArchiveName.sh")
ZIP=$(bash "$JVM_SCRIPTS/getJdkArchiveFile.sh")
URL=$(bash "$JVM_SCRIPTS/getJdkUrl.sh")
MAJOR_VERSION=${JVM_MAJOR_VERSION:-25}
INSTALL_ROOT=${JDK_INSTALL_ROOT:-$HOME/bin/CaDoodle-ApplicationInstall}
INSTALL_DIR=$INSTALL_ROOT/$JVM
TEMP_DIR=$(mktemp -d)
trap 'rm -rf "$TEMP_DIR"' EXIT

# Validate an existing install before trusting it; a stale or broken one is reinstalled.
is_working_jvm() {
	local dir=$1 java out
	# Windows ships the launcher as java.exe; everything else as java
	java=$(find -L "$dir" -type f \( -name java -o -name java.exe \) -path '*/bin/*' 2>/dev/null | head -1)
	[ -n "$java" ] || return 1
	chmod +x "$java" 2>/dev/null || true
	# java -version prints to stderr and quotes the version: openjdk version "25.0.4"
	out=$("$java" -version 2>&1) || return 1
	grep -q "version \"${MAJOR_VERSION}\." <<<"$out" || return 1
}

if is_working_jvm "$INSTALL_DIR"; then
	echo "JDK already installed and working: $INSTALL_DIR"
else
	echo "Installing JDK $JVM to $INSTALL_DIR"
	curl -fL --retry 3 -o "$TEMP_DIR/$ZIP" "$URL"
	rm -rf "$INSTALL_DIR"
	EXTRACT_DIR=$TEMP_DIR/extract
	mkdir -p "$EXTRACT_DIR"
	case $ZIP in
	*.zip)
		if command -v unzip >/dev/null 2>&1; then
			unzip -o -q "$TEMP_DIR/$ZIP" -d "$EXTRACT_DIR"
		elif command -v python3 >/dev/null 2>&1; then
			# Git Bash and minimal MSYS2 installs ship no unzip; python3 does
			python3 -m zipfile -e "$TEMP_DIR/$ZIP" "$EXTRACT_DIR"
		elif command -v python >/dev/null 2>&1; then
			# Windows runners expose python.exe without a python3 alias
			python -m zipfile -e "$TEMP_DIR/$ZIP" "$EXTRACT_DIR"
		else
			echo "unzip or python is required to extract $ZIP" >&2
			exit 1
		fi
		;;
	*) tar -xzf "$TEMP_DIR/$ZIP" -C "$EXTRACT_DIR" ;;
	esac

	# macOS archives hide the JDK home inside a .jdk bundle: pick the deepest
	# Home directory (Zulu ships exactly one), else the archive top dir
	HOME_DIR=$(find "$EXTRACT_DIR" -type d -path '*/Contents/Home' | sort | tail -1)
	if [ -n "$HOME_DIR" ]; then
		SRC_DIR=$HOME_DIR
	else
		SRC_DIR=$(find "$EXTRACT_DIR" -mindepth 1 -maxdepth 1 -type d | head -1)
		[ -d "$SRC_DIR" ] || { echo "Archive $ZIP extracted nothing recognisable" >&2; exit 1; }
	fi
	mkdir -p "$INSTALL_DIR"
	# -L dereferences the top-level symlinks so real files land in the install root
	cp -RL "$SRC_DIR/." "$INSTALL_DIR/"
	chmod -R u+rwX "$INSTALL_DIR"

	if is_working_jvm "$INSTALL_DIR"; then
		echo "JDK installed successfully: $INSTALL_DIR"
	else
		echo "FAIL: JDK install at $INSTALL_DIR did not pass 'java -version' ($MAJOR_VERSION.x expected)" >&2
		exit 1
	fi
fi

if [ -x "$INSTALL_DIR/bin/java.exe" ]; then
	JAVA_LAUNCHER=$INSTALL_DIR/bin/java.exe
else
	JAVA_LAUNCHER=$INSTALL_DIR/bin/java
fi
echo "--- java -version ---"
"$JAVA_LAUNCHER" -version
