#!/usr/bin/env bash
# Run on a fresh 64-bit Raspberry Pi OS (Bookworm/Trixie) installation.
set -euo pipefail

if [[ "$(dpkg --print-architecture)" != arm64 ]]; then
    echo "This guide needs a 64-bit (arm64) OS. Install Raspberry Pi OS Lite 64-bit first." >&2
    exit 1
fi

. /etc/os-release
case "${VERSION_CODENAME:-}" in
    bookworm|trixie) ;;
    *) echo "Use Debian-based Raspberry Pi OS Bookworm or Trixie for this installer." >&2; exit 1 ;;
esac

if command -v docker >/dev/null 2>&1; then
    sudo docker compose version
    sudo systemctl enable --now docker
    echo "Docker is already installed; kept the existing installation."
    exit 0
fi

sudo apt-get update
sudo apt-get install -y ca-certificates curl openssl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
sudo tee /etc/apt/sources.list.d/docker.sources >/dev/null <<EOF
Types: deb
URIs: https://download.docker.com/linux/debian
Suites: ${VERSION_CODENAME}
Components: stable
Architectures: arm64
Signed-By: /etc/apt/keyrings/docker.asc
EOF
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo docker compose version
