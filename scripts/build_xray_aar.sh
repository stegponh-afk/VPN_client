#!/usr/bin/env bash
# Builds app/libs/libv2ray.aar from github.com/2dust/AndroidLibXrayLite (Xray-core,
# LGPL-3.0). Re-run this whenever Xray-core needs updating; it overwrites the AAR
# in place. Requires: Go 1.21+, Android SDK + NDK, gomobile.
#
# Native code — this is the actual VLESS/Reality/XHTTP protocol implementation and
# Xray-core's built-in `tun` inbound (gVisor-based userspace network stack) that
# XrayTunnelEngine.kt talks to. See README.md → "How the tunnel engine works".
set -euo pipefail

: "${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK path}"
NDK_VERSION="${NDK_VERSION:-27.0.12077973}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/$NDK_VERSION"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

export PATH="$PATH:$(go env GOPATH)/bin"
go install golang.org/x/mobile/cmd/gomobile@latest
go install golang.org/x/mobile/cmd/gobind@latest

git clone --depth 1 https://github.com/2dust/AndroidLibXrayLite.git "$WORK_DIR/AndroidLibXrayLite"
cd "$WORK_DIR/AndroidLibXrayLite"

go mod tidy -v

# -target=android/arm64 only: swap/extend for other ABIs (adds build time + AAR size).
gomobile bind -v -androidapi 24 -target=android/arm64 -trimpath \
  -ldflags='-s -w -buildid= -checklinkname=0' \
  -o "$REPO_ROOT/app/libs/libv2ray.aar" \
  ./

echo "Built $REPO_ROOT/app/libs/libv2ray.aar"
