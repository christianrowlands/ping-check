# PingCheck Privacy Policy

**Developer:** Craxiom
**Effective Date:** March 17, 2026

## Introduction

PingCheck is a free, open-source Android application for running ping and traceroute diagnostics. The app is designed with privacy as a core principle — all data stays on your device and nothing is collected by the developer.

## Information Collection and Use

PingCheck does **not** collect, transmit, or share any personal information. No data ever leaves your device to the developer or any third party. There are no user accounts, no registration, and no tracking of any kind.

## Data Stored on Your Device

PingCheck stores the following data locally on your device only:

- **Ping results** — Target host, latency measurements, packet loss statistics, and timestamps for ping sessions you run.
- **Traceroute results** — Hop-by-hop data including IP addresses, hostnames, and round-trip times for traceroutes you run.
- **Favorites** — Hosts you save for quick access.
- **Preferences** — Your chosen theme, default ping/traceroute parameters, and data retention period.

This data is stored in a local database and local preferences on your device. You control how long results are retained through the app's settings.

## Network Activity

PingCheck only generates network traffic that you explicitly initiate:

- **ICMP ping packets** to the host you specify.
- **Traceroute packets** (UDP/ICMP) to the host you specify.
- **DNS lookups** to resolve hostnames you enter.

No other network connections are made. The app does not contact any remote servers, APIs, or services in the background.

## IP Geolocation

PingCheck includes local MaxMind GeoLite2 databases bundled within the app to display approximate geographic information for IP addresses. These lookups happen entirely on your device — no data is sent to MaxMind or any other service.

This product includes GeoLite2 data created by MaxMind, available from [https://www.maxmind.com](https://www.maxmind.com).

## Analytics and Crash Reporting

PingCheck includes **no** analytics, crash reporting, telemetry, or any other form of usage tracking. There is no Firebase, no third-party SDKs that collect data, and no phone-home behavior of any kind.

## Permissions

PingCheck requests the following Android permissions:

| Permission | Purpose |
|---|---|
| `INTERNET` | Send ping and traceroute packets to hosts you specify |
| `ACCESS_NETWORK_STATE` | Detect whether a network connection is available |
| `ACCESS_WIFI_STATE` | Detect Wi-Fi connectivity status |
| `FOREGROUND_SERVICE` | Keep long-running ping/traceroute sessions active |
| `WAKE_LOCK` | Prevent the device from sleeping during active sessions |
| `POST_NOTIFICATIONS` | Show notifications for background ping/traceroute sessions |

No location, camera, microphone, contacts, or storage permissions are requested.

## Data Backup

Cloud backup and device-to-device transfer of app data are explicitly disabled. Your PingCheck data is not included in Android's automatic backup system.

## Children's Privacy

PingCheck does not collect personal information from anyone, including children under the age of 13. The app contains no advertising, in-app purchases, or social features.

## Changes to This Privacy Policy

This privacy policy may be updated from time to time. Changes will be posted to the app's GitHub repository. Continued use of the app after changes constitutes acceptance of the updated policy.

## Contact

If you have questions about this privacy policy, contact:

**Email:** craxiomdev@gmail.com
