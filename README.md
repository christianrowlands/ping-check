# PingCheck

A simple Android app for ping and traceroute.

## Features

- **Ping** — Execute pings with configurable count, interval, packet size, and timeout
- **Traceroute** — Trace network path to any destination via incremental TTL
- **Continuous Ping** — Run indefinite pings via a foreground service
- **Latency Charts** — Visualize ping latency over time
- **Geolocation** — Country, ASN, and org info via bundled MaxMind GeoLite2 databases
- **Favorites** — Save hosts with custom display names and per-host settings
- **History** — Browse and manage past ping and traceroute sessions
- **IPv4 & IPv6** — Automatic protocol detection

## Tech Stack

Kotlin, Jetpack Compose, Material 3, Hilt, Room, Coroutines, Vico (charts), MaxMind DB Reader

## Requirements

- Min SDK 24 (Android 7.0)
- Target SDK 35 (Android 15)
- Java 17

## Building

```sh
./gradlew assembleDebug
```

## License

This project is licensed under the [GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.en.html).
