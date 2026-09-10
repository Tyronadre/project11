# Offline preparation and Raspberry Pi deployment

The Pi runs one JAR containing the backend, templates, stylesheet, and embedded database engine.
Build on your development machine; copy the JAR to the Pi. Node.js, Gradle, and a separate database server are not needed on the Pi to run it.

## Prepare while internet is available

1. Install JDK 25 on the development machine and import the Gradle project in the IDE.
2. Build and test once online, then force a complete offline rebuild:

```powershell
.\gradlew.bat clean test bootJar copyDatabaseTools
.\gradlew.bat --offline clean test bootJar copyDatabaseTools
```

3. Keep the Gradle user cache (normally `C:\Users\<you>\.gradle` / `~/.gradle`), wrapper files, JDK, and this repository.
4. Download IDE source/Javadoc attachments for dependencies if you want them offline.
5. Install a **Java 25 runtime for the Pi's actual OS/CPU architecture** before disconnecting.

An offline build can use only artifacts already cached. New dependencies or versions need preparation again; `--offline` cannot download missing files.
Running the packaged JAR makes no Gradle dependency downloads. See [Gradle dependency caching](https://docs.gradle.org/current/userguide/dependency_caching.html).

The project currently targets Java 25. Do not assume a Pi's distribution has that version installed by default.
For a 64-bit Raspberry Pi OS installation, use a Linux ARM64/AArch64 Java 25 runtime from a trusted Java vendor.
Check on the Pi:

```bash
uname -m
java -version
```

If the device/runtime cannot support Java 25, choose a compatible supported Java target, change `build.gradle`, install that JDK locally, rebuild, and rerun all tests.
A JAR compiled for Java 25 will not run on Java 21 or 17. This repository keeps the original Java version; Pi hardware testing still needs to happen on your device.

## First manual run

Use your own SSH username and Pi hostname/address in place of `YOUR_USER@PI_HOST`:

```bash
ssh YOUR_USER@PI_HOST 'mkdir -p ~/project11'
scp build/libs/project11.jar YOUR_USER@PI_HOST:project11/project11.jar
ssh YOUR_USER@PI_HOST
cd ~/project11
java -Xms64m -Xmx256m -jar project11.jar
```

These are shell commands; `ssh` and `scp` are also available in many Windows installations.
The heap sizes are a starting point for a small test app, not a total process-memory limit. Measure on your Pi and adjust if needed.
BCrypt deliberately uses CPU; registration may be slower on a small device.

Browse to `http://PI_HOST:8080` from the same network. If name resolution fails, use the Pi's LAN IP.
For local-only testing on the Pi, append `--server.address=127.0.0.1`.
Stop the manual process with `Ctrl+C` before starting a service on the same database/port.

The default database is `~/project11/data/project11.mv.db` **because the working directory is `~/project11`**.
Use an absolute DB_URL for a service, and keep its data directory when replacing the JAR.
H2 is a single-application embedded file here. Do not run two app instances against it or place it on a network share.

## Optional systemd service

Run these setup commands on the Pi with an account allowed to use sudo:

```bash
sudo useradd --system --home /opt/project11 --shell /usr/sbin/nologin project11
sudo install -d -o root -g root /opt/project11
sudo install -d -o project11 -g project11 -m 700 /var/lib/project11
sudo install -o root -g root -m 644 ~/project11/project11.jar /opt/project11/project11.jar
sudo install -o root -g root -m 600 /dev/null /etc/project11.env
sudoedit /etc/project11.env
```

Create the account/directories only if they do not already exist. Do not recreate or truncate an existing environment file during an update.
In `/etc/project11.env`, set:

```ini
PORT=8080
DB_URL=jdbc:h2:file:/var/lib/project11/project11;DB_CLOSE_ON_EXIT=FALSE
DB_USERNAME=project11
DB_PASSWORD=REPLACE_WITH_YOUR_OWN_DATABASE_PASSWORD
```

Choose the password before the database is first created; do not leave the placeholder.
This is a database password, separate from registered users' passwords. Setting it later does not change an existing database's credentials.
The service path creates a new database unless you migrate existing data. To retain a manual-run database, stop the manual process, copy its closed `.mv.db` file into the service data path, set ownership to `project11`, and configure its original credentials.

Create `/etc/systemd/system/project11.service` with `sudoedit`:

```ini
[Unit]
Description=Project 11 Spring Boot app
After=network.target

[Service]
Type=simple
User=project11
Group=project11
WorkingDirectory=/opt/project11
EnvironmentFile=/etc/project11.env
ExecStart=/usr/bin/java -Xms64m -Xmx256m -jar /opt/project11/project11.jar
Restart=on-failure
RestartSec=5
TimeoutStopSec=30
SuccessExitStatus=143
UMask=0077
NoNewPrivileges=true
PrivateTmp=true
ProtectHome=true
ProtectSystem=strict
ReadWritePaths=/var/lib/project11

[Install]
WantedBy=multi-user.target
```

Check that `/usr/bin/java` is Java 25. If Java is elsewhere, use its verified absolute path in ExecStart.
The Java installation must be readable by the service account; an installation in a home directory is blocked by `ProtectHome=true`.

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now project11
sudo systemctl status project11
sudo journalctl -u project11 -n 100 --no-pager
sudo journalctl -u project11 -f
```

Leave the live log view with Ctrl+C; this does not stop the service.

## Back up and update

Back up while the service is stopped and no SQL shell is using its database:

```bash
sudo systemctl stop project11
sudo install -d -o root -g root -m 700 /var/backups/project11
sudo cp /var/lib/project11/project11.mv.db "/var/backups/project11/project11-$(date +%Y%m%d-%H%M%S).mv.db"
sudo systemctl start project11
```

Verify the copy succeeded before continuing. Protect backups: they contain email addresses and password hashes.
For a restore, stop the service, preserve the current file separately, copy a verified backup to `/var/lib/project11/project11.mv.db`, restore ownership/mode (`project11:project11`, 600), and start again with the matching DB credentials and compatible H2 version.
Test restoring a copy before relying on your backup process.

To deploy a newly built JAR after making a backup:

```bash
# On your development machine:
scp build/libs/project11.jar YOUR_USER@PI_HOST:project11/project11-new.jar

# On the Pi:
sudo systemctl stop project11
sudo cp /opt/project11/project11.jar /opt/project11/project11-previous.jar
sudo install -o root -g root -m 644 ~/project11/project11-new.jar /opt/project11/project11.jar
sudo systemctl start project11
sudo journalctl -u project11 -n 100 --no-pager
```

Keep `/var/lib/project11` intact. Rolling back code may also require a compatible database backup after schema changes.
Hibernate uses `ddl-auto=update` to apply supported schema changes at startup. Back up before upgrading: renames, data transformations, and changes to existing constraints may require deliberate migrations.

## Access and troubleshooting

```bash
curl -I http://127.0.0.1:8080/register
hostname -I
ss -ltn | grep 8080
free -h
```

If localhost works but another LAN device cannot connect, check the Pi IP, binding address, network isolation, and firewall.
Allow only the network access you intend. Do not set up router port forwarding merely to test on your home LAN.
If the service fails, check logs for Java version, database permissions, wrong credentials, file locking, or an occupied port.

This starter supports registration and session-based sign-in. For internet access, first add HTTPS through a configured reverse proxy, set secure session cookies (`server.servlet.session.cookie.secure=true`), and add registration/sign-in rate limiting and abuse controls.
Use test credentials over plain HTTP on a trusted local network; hashing in the database does not encrypt traffic in transit.
If a reverse proxy runs on the same Pi, bind the app to loopback and expose the proxy's HTTPS port instead.
Trust forwarded headers only from a proxy you control. Email verification and password recovery require additional application work.

## Offline checklist

- The clean offline build and tests pass on the development machine.
- Java 25 and the JAR are already on the Pi.
- These Markdown files and H2 tools are copied wherever you will develop.
- The database location and credentials are recorded securely.
- The app starts and a registration survives an app/service restart; users can sign in again after in-memory sessions expire.
- A database backup has been restored successfully to a separate location.
- The frontend loads without external fonts, scripts, or styles.
