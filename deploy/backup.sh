#!/usr/bin/env bash
# Stop all writers while copying H2 and the mail queue, then restart them.
set -euo pipefail
cd "$(dirname "$0")/.."
umask 077
backup_dir="$PWD/backups/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$backup_dir"
cp .env compose.yaml compose.bootstrap.yaml "$backup_dir/"
cp deploy/Caddyfile "$backup_dir/"

sudo docker image inspect project11-app:local >/dev/null
trap 'sudo docker compose start' EXIT
sudo docker compose stop
for volume in app_data smtp_queue dkim_keys caddy_data caddy_config; do
    sudo docker run --rm --network none --user 0 --entrypoint tar \
        --mount "type=volume,src=project11_${volume},dst=/source,readonly" \
        project11-app:local -C /source -czf - . > "$backup_dir/$volume.tar.gz"
    tar -tzf "$backup_dir/$volume.tar.gz" >/dev/null
done
echo "Backup saved in $backup_dir. Copy this private directory off the Pi."
