# Security Policy

## Supported Versions

Only the latest release of Razdrop receives active security maintenance and updates.

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

---

## Reporting a Vulnerability

The Razdrop project takes the security and privacy of its users seriously. If you discover a potential vulnerability, please follow responsible disclosure guidelines.

### How to Report

1. **Do not disclose publicly:** Please avoid opening a public GitHub issue, forum post, or pull request discussing the vulnerability.
2. **Contact:** Submit a private report via **GitHub Private Vulnerability Reporting** directly on the repository (under the **Security** tab > **Report a vulnerability**).
3. **Include details:**
   - A detailed description of the vulnerability.
   - Steps to reproduce or proof-of-concept code.
   - Affected device configurations (Android OS version, hardware model).
   - Impact assessment (e.g. unauthorized file access, denial of service).

### What to Expect

- **Acknowledgment:** We will acknowledge receipt of your report within 48 to 72 hours.
- **Investigation:** We will evaluate the report, verify the issue, and determine its severity.
- **Resolution:** A patch will be developed, tested, and published in a subsequent release.
- **Credit:** Upon release, we are happy to credit you for the responsible disclosure if you desire.

---

## Security Model & Considerations

Razdrop is engineered as an **offline-first local peer-to-peer file transfer application**:
- **No Cloud Infrastructure:** Files are transmitted point-to-point over local TCP connections; no data is ever uploaded to external servers.
- **Local Network Scope:** Discovery and transfers occur exclusively on the local network (Wi-Fi Direct, Local-Only Hotspot, or the local subnet).
- **Pairing Protection:** Peer-to-peer connections require mutual confirmation via QR code handshake or a 4-digit pairing PIN displayed on the receiving device.
