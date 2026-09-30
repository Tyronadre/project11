# Raspberry Pi: Docker, HTTPS, and direct outgoing email

Run three containers together with Docker Compose:

```text
Internet -- TCP 80/443 --> Caddy -- private network --> Spring Boot + H2
                                                       |
                                                private SMTP :587
                                                       |
                                                 Postfix + DKIM
                                                       |
                                          outbound TCP 25 --> recipient MX
```

There is **no external SMTP relay, SMTP subscription, or provider account**.
The Pi delivers directly to recipients' mail servers. No SMTP port is published
on the Pi. This is an outgoing server for this application, not an inbox/IMAP server.

The images support ARM64, and the Dockerfile builds and tests the application
using Java 25 before making a smaller runtime image. Java, Gradle, Node, and a
separate database do not need to be installed on the Pi host.

## 1. Check the connection before setting up mail

These commands support **Ubuntu 22.04, 24.04, or 26.04 on ARM64** on the Pi.
Keep your Ubuntu installation; the installer detects it and uses Docker's Ubuntu
repository. It also supports 64-bit Raspberry Pi OS based on Debian Bookworm or Trixie.
A Pi 4 or 5 with **4 GB RAM or more** is a sensible starting point for building
and running this app; measure resource use on your device. Allow several GB of
free disk space for images, builds, and photo uploads. An SSD is preferable for
the database. This deployment has not been hardware-tested on your Pi.

For this direct IPv4 mail setup you need:

- A domain whose DNS records you can edit.
- A stable public IPv4 address and router forwarding for the website. CGNAT or
  DS-Lite without a reachable public IPv4 does not support this forwarding recipe.
- Your ISP to allow **outbound TCP port 25** to other mail servers.
- Your ISP/IP owner to set your public IPv4's **PTR/reverse DNS** to, for example,
  `mail.example.com`. This is separate from DNS at your domain registrar.
- An IP range that recipients permit for direct email delivery. Residential or
  dynamic ranges can be blocked even when all DNS records are correct.

If the ISP cannot provide these, this connection cannot reliably deliver mail
directly. Opening more inbound ports or changing SMTP to port 587 will not fix
outbound port 25 restrictions. This configuration does not fall back to a relay.
[Gmail's sender requirements](https://support.google.com/mail/answer/81126)
describe authentication, TLS, and matching forward/reverse DNS requirements;
[Spamhaus PBL](https://www.spamhaus.org/blocklists/policy-blocklist/) explains
IP ranges that should not send directly to recipient mail servers.

On the Pi:

```bash
uname -m
dpkg --print-architecture
cat /etc/os-release
sudo apt-get update
sudo apt-get install -y ca-certificates curl openssl dnsutils netcat-openbsd
mail_test_host="$(dig +short MX gmail.com | sort -n | head -n 1 | awk '{print $2}')"
test -n "$mail_test_host" && nc -4 -vz -w 10 "$mail_test_host" 25
```

The architecture should be `aarch64` / `arm64`. The connection test should
succeed. It only opens a TCP connection; it sends no email and does not prove
that recipients will accept your messages. If it times out, check the ISP and
outbound firewall before continuing with email setup.

In the remaining examples replace `YOUR_USER`, `PI_HOST`, `example.com`, and
`YOUR_PUBLIC_IPV4` with your values. `PI_HOST` is initially the Pi's LAN address.

## 2. Copy this working tree from Windows to the Pi

From PowerShell in the project directory:

```powershell
tar -czf project11-deploy.tar.gz Dockerfile compose.yaml compose.bootstrap.yaml .dockerignore .env.example build.gradle settings.gradle gradlew gradle src deploy doc
scp project11-deploy.tar.gz YOUR_USER@PI_HOST:project11-deploy.tar.gz
ssh YOUR_USER@PI_HOST
```

This includes the new deployment files even before they have been committed or
pushed. It excludes your Windows database, `.env`, `.git`, and build outputs.
The following commands run in the SSH session **on the Pi**:

```bash
mkdir -p ~/project11
cd ~/project11
tar -xzf ~/project11-deploy.tar.gz
bash deploy/install-docker.sh
```

On Ubuntu the installer uses [Docker's Ubuntu repository](https://docs.docker.com/engine/install/ubuntu/)
with your Ubuntu release codename. On Debian-based Raspberry Pi OS it uses
[Docker's Debian repository](https://docs.docker.com/engine/install/debian/).
It targets a fresh OS and keeps an existing Docker installation. If conflicting
distribution Docker packages are already installed, follow Docker's migration
instructions rather than removing an installation with existing workloads.
Use `sudo docker` in this guide; joining the Docker group is not necessary.

## 3. Configure the application

For a **new database**, create the local configuration once:

```bash
cd ~/project11
umask 077
if [ ! -e .env ]; then
    cp .env.example .env
    sed -i "s/REPLACE_WITH_RANDOM_DATABASE_PASSWORD/$(openssl rand -hex 32)/" .env
fi
nano .env
```

Set these values, leaving the generated database password in place:

```dotenv
SITE_DOMAIN=friends.example.com
ACME_EMAIL=you@example.com
DB_USERNAME=project11
DB_PASSWORD=YOUR_GENERATED_DATABASE_PASSWORD
MAIL_FROM=notifications@example.com
MAIL_DOMAIN=example.com
MAIL_HOSTNAME=mail.example.com
```

`SITE_DOMAIN` and `MAIL_HOSTNAME` are hostnames, without `https://` or a path.
`MAIL_DOMAIN` is exactly the part after `@` in `MAIL_FROM`. Do not use someone
else's sender domain such as `gmail.com`.

Use a working sender mailbox at your domain if you want replies and delivery
failure notices. Keep that domain's existing inbound MX records pointing at its
mailbox service. **Do not point an MX record at this Pi:** this stack has no
incoming mailbox service and does not publish port 25. Having an existing inbox
for bounces does not route the Pi's outgoing messages through a relay.

The named `project11_app_data` volume starts empty. If you want existing Windows
accounts, photos, and records, follow the migration section below **before the
first app startup**. Changing `.env` credentials does not change a stored H2 password.

```bash
chmod 600 .env
sudo docker compose config --quiet
sudo docker compose pull smtp caddy
sudo docker compose build --pull app
```

The first build downloads dependencies and runs the test suite; it may take a
while on a Pi. Subsequent builds reuse Docker/Gradle caches. The app runs as a
non-root user with a 512 MB Java heap and a 1 GB container memory limit. Upload
temporary files use disk, rather than putting potentially large uploads in RAM.

## 4. Create the first administrator privately

Keep router forwarding disabled until this step is complete. The existing app
makes its **first registered user an administrator**.

On the Pi, start only the app and its SMTP dependency using the bootstrap override:

```bash
sudo docker compose -f compose.yaml -f compose.bootstrap.yaml up -d --wait --wait-timeout 600 app
sudo docker compose -f compose.yaml -f compose.bootstrap.yaml ps
```

In a **second PowerShell window on your computer**, leave this SSH tunnel running:

```powershell
ssh -N -L 18080:127.0.0.1:8080 YOUR_USER@PI_HOST
```

Open `http://localhost:18080/register` on your computer. Create your own account
first, then sign in. If you migrated an existing database, sign in to its existing
admin account instead. The bootstrap setup binds HTTP only to Pi loopback,
temporarily permits HTTP session cookies, and disables application email workers.

The application currently permits public registration and does not verify email
ownership or rate-limit login/registration. Registered members can access group
data. That existing membership model still applies to this deployment; HTTPS
does not make registration invitation-only.

## 5. Publish mail and website DNS

In your DNS provider's dashboard set the records below. Names in this table are
relative to the `example.com` zone. `YOUR_PUBLIC_IPV4` is your **public** address,
not the Pi's `192.168.x.x` LAN address.

| Type | Name | Value |
| --- | --- | --- |
| A | `friends` | `YOUR_PUBLIC_IPV4` |
| A | `mail` | `YOUR_PUBLIC_IPV4` |
| TXT | `@` | `v=spf1 ip4:YOUR_PUBLIC_IPV4 ~all` |
| TXT | `_dmarc` | `v=DMARC1; p=none` |
| TXT | `project11._domainkey` | Generated DKIM value from the command below |

The SPF/DMARC examples are for a domain without those records. If records already
exist, add the Pi IP to the **existing** SPF policy, retaining legitimate senders;
keep your existing DMARC policy. Do not publish a second SPF or DMARC record, or
weaken an existing policy. `p=none` is a monitoring policy for initial verification.
For a sender subdomain, publish these policies under that subdomain instead.

Ask the ISP to set:

```text
YOUR_PUBLIC_IPV4 -> PTR -> mail.example.com
```

Use DNS-only records if your DNS service also offers HTTP proxying. This guide
assumes clients reach Caddy directly. Publish an AAAA record only if IPv6 access
and firewall rules are also correctly configured; the SMTP container explicitly
uses IPv4 so mail always leaves through the IP in this SPF/PTR setup.

Postfix generated a persistent DKIM key when it started. Print **only the public
DNS record**, replacing `example.com` with `MAIL_DOMAIN`:

```bash
sudo docker compose exec smtp cat /etc/opendkim/keys/example.com.txt
```

Create the TXT record `project11._domainkey.example.com` using the text inside the
quoted strings, joining them without extra spaces. The TXT value starts with
`v=DKIM1;`; do not include the record name, `IN TXT`, parentheses, or comment.
Never publish the `.private` file. Keep the `project11_dkim_keys` Docker volume
so upgrades retain the signing key. This uses the pinned image's
[DKIM configuration](https://github.com/bokysan/docker-postfix/tree/v5.1.0#dkim--domainkeys).

Verify after DNS propagation:

```bash
dig +short A friends.example.com
dig +short A mail.example.com
dig +short -x YOUR_PUBLIC_IPV4
dig +short TXT example.com
dig +short TXT _dmarc.example.com
dig +short TXT project11._domainkey.example.com
sudo docker compose exec smtp postconf relayhost inet_protocols myhostname
```

`relayhost =` must be empty, `inet_protocols = ipv4`, and `myhostname` must match
your PTR hostname. DNS/authentication helps deliverability but cannot guarantee
inbox placement or overcome an IP range blocked by the recipient.

## 6. Expose the website and start the normal configuration

Give the Pi a reserved LAN address in the router. Configure **only** these
inbound TCP forwards to that address:

| Router port | Pi port | Purpose |
| --- | --- | --- |
| 80 | 80 | HTTP redirect and certificate validation |
| 443 | 443 | HTTPS website |

Allow outbound TCP 25 for SMTP, outbound DNS (UDP/TCP 53), and outbound HTTPS for
certificates/downloads. Do not forward 8080, 25, 465, 587, database ports, or the
Docker API. Docker publishes ports using its own firewall rules, which can
bypass UFW rules; the Compose file itself publishes only 80 and 443 in normal use.

On the Pi:

```bash
cd ~/project11
sudo docker compose -f compose.yaml -f compose.bootstrap.yaml down
sudo docker compose up -d --wait --wait-timeout 600
sudo docker compose ps
sudo docker compose logs --tail=100 caddy app smtp
```

**Do not add `-v` to `down`: it deletes persistent volumes.** The normal start
removes the temporary loopback app port, enables secure cookies and application
emails, and starts Caddy. Caddy obtains and renews certificates automatically;
the hostname must resolve to this connection and reach ports 80/443. See
[Caddy automatic HTTPS](https://caddyserver.com/docs/automatic-https).

You can now close the SSH tunnel with Ctrl+C. Open
`https://friends.example.com` and sign in. Test once from mobile data so the test
does not depend on your router's support for accessing its public IP from the LAN.
The services restart after reboot. Only Caddy can reach the app from the internet;
Spring trusts forwarding headers on its private network, and Caddy replaces or
strips client-supplied forwarding information.

## 7. Verify actual email delivery

On the public site, use **Forgot password** for your registered email address.
This exercises the app, private SMTP submission, DKIM signing, direct delivery,
and the public URL in the message. Verify the message arrives and its link points
at your HTTPS domain. Check spam and the recipient's full message headers for
`spf=pass`, `dkim=pass` (your domain), and `dmarc=pass`.

```bash
sudo docker compose logs --tail=100 app smtp
sudo docker compose exec smtp postqueue -p
```

The app's `SENT` means the local Postfix server accepted a message; it is not an
inbox receipt. Postfix `status=sent` means the next server accepted it. Permanent
failures produce bounces to the envelope sender, so monitor that mailbox too.
Messages deferred by recipient servers remain in the persistent Postfix queue.
After fixing DNS/connectivity, request a retry:

```bash
sudo docker compose exec smtp postqueue -f
```

If mail is deferred with timeouts, check outbound 25. For rejections involving
PTR, SPF, DKIM, DMARC or IP reputation, address the named issue and test again.
STARTTLS is used when the destination advertises it; direct SMTP is configured
for opportunistic TLS. No external relay is configured or used as a fallback.

## Back up and update

After a successful deployment, make a consistent backup:

```bash
cd ~/project11
bash deploy/backup.sh
```

This pauses the stack, backs up all five named volumes plus `.env` and proxy/
Compose configuration to a private timestamped directory under `backups/`, checks
that each archive is readable, then restarts the existing containers. It includes
H2 accounts/photos/application mail jobs, the Postfix queue, DKIM private keys,
and Caddy certificates. Copy backups off the Pi and keep their DB password.
Archive validation is not a database restore test; test a restoration on a separate
installation before relying on the backup.

Before deploying new source, take a backup, retain the old application image,
copy/extract a new deployment bundle, and run:

```bash
sudo docker image tag project11-app:local project11-app:previous
sudo docker compose pull smtp caddy
sudo docker compose build --pull app
sudo docker compose up -d --wait --wait-timeout 600
sudo docker compose ps
```

Do not replace `.env` on upgrades. The Java 25 base tags receive patch updates
when rebuilt with `--pull`; Caddy and Postfix are pinned to explicit versions.
Review and update those versions deliberately. The pinned Postfix v5 image uses
OpenDKIM; a future major release may change its signing backend/key migration.
Hibernate can change the schema on startup: rolling back an image may also need
the corresponding database backup. Never run multiple app containers against
this single H2 volume.

To restore on a **fresh installation with empty volumes**, place the backup
directory at `~/project11/backups/RESTORE`, copy its `.env`, Compose files, and
Caddyfile back into the project, build a compatible app image, then:

```bash
cd ~/project11
sudo docker compose create
for volume in app_data smtp_queue dkim_keys caddy_data caddy_config; do
    sudo docker run --rm --network none --user 0 --entrypoint tar \
        --mount "type=volume,src=project11_${volume},dst=/restore" \
        --mount "type=bind,src=$PWD/backups/RESTORE,dst=/backup,readonly" \
        project11-app:local -C /restore -xzf "/backup/$volume.tar.gz"
done
sudo docker compose up -d --wait --wait-timeout 600
```

These restore commands are only for empty target volumes, not a live deployment.
Restoring old database jobs/mail queues can resend messages whose successful
delivery happened after the backup. Review queued work before resuming a restore.

## Migrating the existing Windows database (optional)

Before the first Pi app startup, stop the Windows app and any H2 SQL shell. Copy
the closed `data/project11.mv.db` to the Pi:

```powershell
scp data/project11.mv.db YOUR_USER@PI_HOST:project11-existing.mv.db
```

In Pi `.env`, set `DB_USERNAME` and `DB_PASSWORD` to that database's **original**
credentials. The local defaults were `sa` and an empty password. For that legacy
empty password, replace `${DB_PASSWORD:?Set DB_PASSWORD in .env before first startup}`
in `compose.yaml` with `${DB_PASSWORD-}`; the app is still private, but set a proper
database password using H2 administration later. Environment changes alone do
not re-key a database.

After building the app image, and before first starting it:

```bash
sudo docker volume create project11_app_data
sudo docker run --rm --network none --user 0 --entrypoint sh \
    --mount type=volume,src=project11_app_data,dst=/restore \
    --mount "type=bind,src=$HOME/project11-existing.mv.db,dst=/source.mv.db,readonly" \
    project11-app:local -c 'test ! -e /restore/project11.mv.db && cp /source.mv.db /restore/project11.mv.db && chown -R 10001:10001 /restore && chmod 700 /restore && chmod 600 /restore/project11.mv.db'
```

Continue with private bootstrap and use the existing administrator. Once normal
deployment enables email, pre-existing pending application notifications can be
sent; review old queued decisions before enabling delivery on a migrated database.

## Validation scope

The Compose files can be checked without a Docker daemon with
`docker compose --env-file .env.example config --quiet`. Runtime validation still
requires a Linux Docker engine, your Pi, public DNS, and your ISP connection.
The build runs the application's tests. DNS, certificate issuance, and inbox
delivery must be verified using the steps above on the actual installation.
