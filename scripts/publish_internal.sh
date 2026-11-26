#!/usr/bin/env bash
set -euo pipefail

# Determine repository root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

VENV_DIR="${SCRIPT_DIR}/play-store/.venv"
REQ_FILE="${SCRIPT_DIR}/play-store/requirements.txt"
PY_SCRIPT="${SCRIPT_DIR}/play-store/publish_to_play_store.py"

BUILD_APP=false
EXTRA_ARGS=()

show_help() {
    cat <<EOF
Usage: $(basename "$0") [OPTIONS] [-- [PYTHON_DEPLOY_ARGS]]

Options:
  -b, --build             Build the release App Bundle (:app:bundleRelease) before publishing
  -c, --credentials FILE  Path to Google Cloud service account JSON credentials
  -a, --aab FILE          Path to custom .aab file (defaults to app/build/outputs/bundle/release/app-release.aab)
  -n, --notes "TEXT"      Release notes text for this release
  -f, --notes-file FILE   File containing release notes
  -t, --track TRACK       Target track (default: internal; choices: internal, alpha, beta, production)
  --dry-run               Validate the release with Google Play without publishing
  -h, --help              Show this help message

Examples:
  # Build and publish to internal testing track
  ./scripts/publish_internal.sh --build -n "Bug fixes and performance enhancements"

  # Publish an existing AAB with specified credentials
  ./scripts/publish_internal.sh -a app/release/app-release.aab -c path/to/service-account.json

  # Dry-run validation only
  ./scripts/publish_internal.sh --dry-run
EOF
}

# Parse options
while [[ $# -gt 0 ]]; do
    case "$1" in
        -b|--build)
            BUILD_APP=true
            shift
            ;;
        -c|--credentials)
            EXTRA_ARGS+=("--credentials" "$2")
            shift 2
            ;;
        -a|--aab)
            EXTRA_ARGS+=("--aab" "$2")
            shift 2
            ;;
        -n|--notes)
            EXTRA_ARGS+=("--notes" "$2")
            shift 2
            ;;
        -f|--notes-file)
            EXTRA_ARGS+=("--notes-file" "$2")
            shift 2
            ;;
        -t|--track)
            EXTRA_ARGS+=("--track" "$2")
            shift 2
            ;;
        --dry-run|--validate-only)
            EXTRA_ARGS+=("--validate-only")
            shift
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        --)
            shift
            EXTRA_ARGS+=("$@")
            break
            ;;
        *)
            EXTRA_ARGS+=("$1")
            shift
            ;;
    esac
done

cd "$ROOT_DIR"

# 1. Optionally build the app bundle
if [ "$BUILD_APP" = true ]; then
    echo "=================================================="
    echo "🔨 Building Release App Bundle (:app:bundleRelease)..."
    echo "=================================================="
    ./gradlew :app:bundleRelease
    echo "✓ App bundle built successfully."
fi

# 2. Upload ProGuard mapping to GlitchTip
upload_glitchtip_mapping() {
    local mapping_file="app/build/outputs/mapping/release/mapping.txt"
    if [ ! -f "$mapping_file" ]; then
        return 0
    fi

    local token="${GLITCHTIP_AUTH_TOKEN:-}"
    local url="${GLITCHTIP_URL:-https://pipistrelus-glitchtip-a927ea-89-168-108-145.sslip.io}"
    local org="${GLITCHTIP_ORG:-tiwi}"
    local proj="${GLITCHTIP_PROJECT:-tiwi-tv}"

    if [ -z "$token" ] && [ -f "local.properties" ]; then
        local prop_token=$(grep '^GLITCHTIP_AUTH_TOKEN=' local.properties | cut -d'=' -f2- | tr -d ' \r\n')
        local prop_url=$(grep '^GLITCHTIP_URL=' local.properties | cut -d'=' -f2- | tr -d ' \r\n')
        local prop_org=$(grep '^GLITCHTIP_ORG=' local.properties | cut -d'=' -f2- | tr -d ' \r\n')
        local prop_proj=$(grep '^GLITCHTIP_PROJECT=' local.properties | cut -d'=' -f2- | tr -d ' \r\n')
        [ -n "$prop_token" ] && token="$prop_token"
        [ -n "$prop_url" ] && url="$prop_url"
        [ -n "$prop_org" ] && org="$prop_org"
        [ -n "$prop_proj" ] && proj="$prop_proj"
    fi

    if [ -z "$token" ]; then
        echo "⚠️  GLITCHTIP_AUTH_TOKEN not found. Skipping GlitchTip mapping upload."
        return 0
    fi

    echo "=================================================="
    echo "📤 Uploading ProGuard Mapping to GlitchTip..."
    echo "=================================================="
    python3 -c "
import zipfile, uuid, os, subprocess, sys

mapping_path = '$mapping_file'
uid = str(uuid.uuid4())
zip_path = f'/tmp/proguard_{uid}.zip'

try:
    with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as z:
        z.write(mapping_path, f'proguard/{uid}.txt')

    upload_url = '${url}/api/0/projects/${org}/${proj}/files/dsyms/'
    res = subprocess.run([
        'curl', '-s', '-w', '\n%{http_code}',
        '-X', 'POST',
        '-H', 'Authorization: Bearer $token',
        '-F', f'file=@{zip_path}',
        upload_url
    ], capture_output=True, text=True)

    output = res.stdout.strip().split('\n')
    http_code = output[-1] if output else ''
    response_body = '\n'.join(output[:-1])

    if http_code in ['200', '201']:
        print(f'   ✓ Mapping uploaded to GlitchTip (debugId: {uid})')
    else:
        print(f'   ❌ GlitchTip mapping upload failed (HTTP {http_code}): {response_body}', file=sys.stderr)
finally:
    if os.path.exists(zip_path):
        os.remove(zip_path)
"
}

upload_glitchtip_mapping

# 2. Check / Setup Python virtual environment
if [ ! -d "$VENV_DIR" ]; then
    echo "=================================================="
    echo "📦 Setting up deployment virtual environment..."
    echo "=================================================="
    if ! command -v python3 &>/dev/null; then
        echo "❌ Error: python3 is not installed or not in PATH." >&2
        exit 1
    fi
    python3 -m venv "$VENV_DIR"
    "$VENV_DIR/bin/pip" install --upgrade pip
    "$VENV_DIR/bin/pip" install -r "$REQ_FILE"
elif [ ! -f "$VENV_DIR/bin/python" ]; then
    python3 -m venv "$VENV_DIR"
    "$VENV_DIR/bin/pip" install -r "$REQ_FILE"
fi

# 3. Execute the publishing script using the official Google Play library
if [ ${#EXTRA_ARGS[@]} -gt 0 ]; then
    exec "$VENV_DIR/bin/python" "$PY_SCRIPT" "${EXTRA_ARGS[@]}"
else
    exec "$VENV_DIR/bin/python" "$PY_SCRIPT"
fi
