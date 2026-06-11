#!/usr/bin/env bash
#
# publish-spm.sh — publish DNLibrary as a Swift package, in one of two modes.
# Plain shell: Gradle is used ONLY to assemble the XCFramework; everything else
# (copy, zip, checksum, manifest, git, release) is done here.
#
#   local   : copy the XCFramework next to the iOS project and generate a local
#             Package.swift, so the Tech Lead can TEST a build before releasing.
#   publish : zip + checksum the XCFramework, rewrite the SPMDNLibrary manifest,
#             then (with confirm) tag, push, and create a GitHub release.
#
# Just run it — it is interactive:
#   ./scripts/publish-spm.sh
#
# Non-interactive (automation/CI) is still supported:
#   ./scripts/publish-spm.sh local   <ios-project-dir> [debug|release]
#   ./scripts/publish-spm.sh publish <tag> [spm-repo-dir] [confirm]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"

die() { echo "❌ $*" >&2; exit 1; }
xcframework_src() { echo "$REPO_ROOT/sharedLogic/build/XCFrameworks/$1/DNLibrary.xcframework"; }

# Prompt until the answer is one of $2 (space-separated); result in global CHOICE.
ask_choice() {  # $1=prompt  $2=valid answers
  local prompt="$1" valid="$2" v
  while :; do
    read -r -p "$prompt" CHOICE
    for v in $valid; do [[ "$CHOICE" == "$v" ]] && return 0; done
    echo "  ⚠️  Please enter one of: $valid"
  done
}

# Assemble the XCFramework with Gradle (Gradle's only job).
assemble() {  # $1 = debug|release
  local task
  case "$1" in
    debug)   task="assembleDNLibraryDebugXCFramework" ;;
    release) task="assembleDNLibraryReleaseXCFramework" ;;
    *) die "config must be 'debug' or 'release' (was '$1')" ;;
  esac
  echo "▶ Assembling XCFramework via Gradle ($1)…"
  ( cd "$REPO_ROOT" && ./gradlew ":sharedLogic:$task" )
}

# ============================================================== LOCAL mode ==
publish_local() {  # $1=ios-project-dir  $2=debug|release
  local ios_project_dir="${1:?iOS project dir required}"
  local config="${2:-debug}"
  [[ -d "$ios_project_dir" ]] || die "Not a directory: $ios_project_dir"

  assemble "$config"

  local src; src="$(xcframework_src "$config")"
  [[ -d "$src" ]] || die "XCFramework not found after assemble: $src"

  local package_dir; package_dir="$(cd "$ios_project_dir/.." && pwd)/DNLibraryLocal"

  echo "▶ Publishing LOCAL SPM package ($config) → $package_dir"
  mkdir -p "$package_dir"
  rm -rf "$package_dir/DNLibrary.xcframework"
  cp -R "$src" "$package_dir/"

  cat > "$package_dir/Package.swift" <<'SWIFT'
// swift-tools-version: 5.10
// AUTO-GENERATED for LOCAL development by scripts/publish-spm.sh. Do not edit or commit.
import PackageDescription

let package = Package(
    name: "DNLibrary",
    platforms: [.iOS(.v15)],
    products: [
        .library(name: "DNLibrary", targets: ["DNLibrary"])
    ],
    targets: [
        .binaryTarget(
            name: "DNLibrary",
            path: "DNLibrary.xcframework"
        )
    ]
)
SWIFT

  cat <<EOF

✅ Local package ready at:
   $package_dir

⚠️  Dapur Naura links DNLibrary REMOTELY, so to test this local build you must, in Xcode, MANUALLY:
   1. Remove the remote SPMDNLibrary dependency (the github.com/Fostahh/SPMDNLibrary URL).
   2. File ▸ Add Package Dependencies ▸ Add Local… ▸ select  $package_dir
   3. Link the DNLibrary product to the app target (static → Do Not Embed).
   When finished testing, switch the dependency back to the remote URL.
   (If Xcode shows stale errors, quit and reopen it.)
EOF
}

# ============================================================ PUBLISH mode ==
# Find a sibling git repo whose origin remote is SPMDNLibrary.
detect_spm_repo() {
  local base; base="$(dirname "$REPO_ROOT")"
  local gitdir repo url
  while IFS= read -r gitdir; do
    repo="$(dirname "$gitdir")"
    url="$(git -C "$repo" remote get-url origin 2>/dev/null || true)"
    [[ "$url" == *SPMDNLibrary* ]] && { echo "$repo"; return 0; }
  done < <(find "$base" -maxdepth 3 -type d -name .git 2>/dev/null)
  return 1
}

publish_remote() {  # $1=tag  $2=spm-repo-dir(may be empty)  $3=dry|confirm
  local tag="${1:?tag required}"
  local spm_dir="${2:-}"
  local confirm="${3:-dry}"
  local config="release"

  command -v swift >/dev/null || die "'swift' toolchain not found (needed to compute the checksum)."

  assemble "$config"

  local src; src="$(xcframework_src "$config")"
  [[ -d "$src" ]] || die "Release XCFramework not found after assemble: $src"

  # Locate + validate the SPMDNLibrary repo.
  [[ -n "$spm_dir" ]] || spm_dir="$(detect_spm_repo)" || die "Could not auto-detect SPMDNLibrary. Provide the repo path."
  [[ -d "$spm_dir/.git" ]] || die "Not a git repo: $spm_dir"
  local origin; origin="$(git -C "$spm_dir" remote get-url origin)"
  [[ "$origin" == *SPMDNLibrary* ]] || die "Repo at $spm_dir is not SPMDNLibrary (origin: $origin)"

  # owner/repo slug → deterministic release-asset URL.
  local slug; slug="$(echo "$origin" | sed -E 's#(git@github.com:|https://github.com/)##; s#\.git$##')"
  local zip_url="https://github.com/$slug/releases/download/$tag/DNLibrary.zip"

  # Preflight: clean tree (Package.swift allowed, we rewrite it), tag not taken.
  local dirty; dirty="$(git -C "$spm_dir" status --porcelain --untracked-files=no | grep -v 'Package.swift$' || true)"
  [[ -z "$dirty" ]] || die "SPMDNLibrary has uncommitted changes other than Package.swift. Resolve first."
  git -C "$spm_dir" rev-parse "$tag" >/dev/null 2>&1 && die "Tag '$tag' already exists in SPMDNLibrary."

  # Zip, then compute the checksum FROM the zip.
  local zip="$REPO_ROOT/sharedLogic/build/XCFrameworks/$config/DNLibrary.zip"
  rm -f "$zip"
  ( cd "$(dirname "$src")" && ditto -c -k --sequesterRsrc --keepParent "DNLibrary.xcframework" "$zip" )
  local checksum; checksum="$(swift package compute-checksum "$zip")"

  # Rewrite the manifest — url + checksum, never hand-edited.
  cat > "$spm_dir/Package.swift" <<SWIFT
// swift-tools-version: 5.10
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let package = Package(
    name: "DNLibrary",
    platforms: [.iOS(.v15)],
    products: [
        .library(name: "DNLibrary", targets: ["DNLibrary"])
    ],
    targets: [
        .binaryTarget(
            name: "DNLibrary",
            url: "$zip_url",
            checksum: "$checksum"
        )
    ]
)
SWIFT

  cat <<EOF

──────────────── REMOTE RELEASE PLAN ────────────────
  Repo      : $slug   ($spm_dir)
  Tag       : $tag
  Asset     : $zip
  Zip URL   : $zip_url
  Checksum  : $checksum
──────────────────────────────────────────────────────
Package.swift rewritten (working tree only — nothing pushed yet):
EOF
  git -C "$spm_dir" --no-pager diff -- Package.swift || true

  if [[ "$confirm" != "confirm" ]]; then
    cat <<EOF

🛑 DRY RUN — nothing committed, pushed, or released.
   Re-run and choose "publish for real" to ship it.
   To discard the prepared manifest:
     git -C "$spm_dir" checkout -- Package.swift
EOF
    return 0
  fi

  echo "▶ Committing and tagging locally (no network yet)…"
  git -C "$spm_dir" commit -am "Release $tag"
  git -C "$spm_dir" tag "$tag"

  # Push with plain git — uses whatever auth you have (credential helper, GitHub
  # Desktop, or an SSH remote). If it's cancelled/fails, hand off cleanly.
  echo "▶ Pushing to GitHub…"
  if ! git -C "$spm_dir" push origin HEAD --tags; then
    cat <<EOF

⚠️  Commit + tag '$tag' were created LOCALLY, but the push did not complete
   (GitHub sign-in cancelled or auth failed). Nothing was lost — finish it later:
     git -C "$spm_dir" push origin HEAD --tags
   Then create the release:  https://github.com/$slug/releases/new?tag=$tag
   (attach $zip as DNLibrary.zip)
   Tip: set up an SSH remote or GitHub Desktop once to avoid the sign-in popup.
EOF
    return 0
  fi

  # Create the release: gh if present, otherwise the browser.
  if command -v gh >/dev/null; then
    echo "▶ Creating the GitHub release and uploading the asset via gh…"
    gh release create "$tag" "$zip" --repo "$slug" --title "$tag" --notes "DNLibrary $tag"
    echo "✅ Released $tag → $zip_url"
  else
    cat <<EOF

✅ Pushed commit + tag '$tag'. No gh CLI, so finish the release in the browser:
   1. Open : https://github.com/$slug/releases/new?tag=$tag
   2. Title: $tag
   3. Attach this file as a release asset — it MUST be named DNLibrary.zip:
      $zip
   4. Click "Publish release".
   The manifest already points at: $zip_url
   ⚠️  Until that asset is uploaded, the URL 404s — upload it before anyone resolves the package.
EOF
  fi
  echo "   Dapur Naura can then bump its SPMDNLibrary dependency to $tag."
}

# ============================================================= interactive ==
interactive() {
  cat <<'EOF'

╭───────────────────────── DNLibrary ─────────────────────────╮
│ 1) local   — copy the XCFramework next to the iOS project    │
│             and generate a local Package.swift, to TEST a    │
│             build before releasing.                          │
│ 2) publish — zip + checksum the XCFramework, rewrite the     │
│             SPMDNLibrary manifest, then tag, push, and       │
│             create a GitHub release.                         │
╰──────────────────────────────────────────────────────────────╯
EOF
  ask_choice "Which one do you want to do? (1/2): " "1 2"
  local mode="$CHOICE"

  if [[ "$mode" == "1" ]]; then
    echo
    echo "Choose build type:"
    echo "  1) debug"
    echo "  2) release"
    ask_choice "Choice (1/2): " "1 2"
    local config="debug"; [[ "$CHOICE" == "2" ]] && config="release"

    local ios_dir
    while :; do
      read -r -p "iOS project path: " ios_dir
      [[ -n "$ios_dir" && -d "$ios_dir" ]] && break
      echo "  ⚠️  Not a directory: '$ios_dir'"
    done

    publish_local "$ios_dir" "$config"
  else
    echo
    local tag
    while :; do
      read -r -p "Release tag (e.g. 1.4.0): " tag
      [[ -n "$tag" ]] && break
    done

    local spm_dir
    read -r -p "SPMDNLibrary path (leave empty to auto-detect): " spm_dir

    echo
    echo "Dry run or publish for real?"
    echo "  1) dry run  (prepare + show the plan, push nothing)  ← recommended"
    echo "  2) publish  (commit, tag, push, create the release)"
    ask_choice "Choice (1/2): " "1 2"
    local confirm="dry"; [[ "$CHOICE" == "2" ]] && confirm="confirm"

    publish_remote "$tag" "$spm_dir" "$confirm"
  fi
}

# ================================================================ dispatch ==
mode="${1:-}"; [[ $# -gt 0 ]] && shift
case "$mode" in
  "")      interactive ;;
  local)   publish_local  "$@" ;;
  publish) publish_remote "$@" ;;
  *)       die "Usage: publish-spm.sh [local <ios-dir> [debug|release] | publish <tag> [spm-dir] [confirm]]  (or no args for interactive)" ;;
esac
