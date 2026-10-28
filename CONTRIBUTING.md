# Contributing to Razdrop

Thank you for your interest in contributing to Razdrop! We welcome bug reports, feature requests, documentation improvements, and code contributions from the community.

---

## Code of Conduct

Please help maintain a welcoming and inclusive environment for everyone. Treat all contributors and users with respect and professional courtesy.

---

## Getting Started

### Prerequisites

- **Java Development Kit (JDK):** Version 17
- **Android SDK:** API level 34 (`compileSdk 34`), minimum API level 26 (`minSdk 26`)
- **Android Build Tools:** 34.0.0
- **Gradle:** 8.9 (or use the included `./gradlew` wrapper)
- **Git**

### Building the Project

1. Fork and clone the repository:
   ```bash
   git clone https://github.com/<your-username>/razdrop.git
   cd razdrop
   ```
2. Build the project using the Gradle wrapper:
   ```bash
   ./gradlew assembleDebug
   ```
3. Run the unit test suite:
   ```bash
   ./gradlew test
   ```

---

## Development Guidelines

### Code Style & Architecture

- **Language:** Pure Java (JDK 17 compatibility).
- **Architecture:** Keep network protocols, foreground services, and UI decoupled.
  - Network logic belongs in `com.razdrop.app.network.*`
  - Background transfers run in `com.razdrop.app.service.TransferService`
  - Leanback TV components belong in `com.razdrop.app.ui.TvMainActivity`
  - Reusable storage and OS utilities belong in `com.razdrop.app.util.*`
- **TV Compatibility:** Always test UI changes on both mobile screens and 10-foot Android TV screens (verify D-pad navigation, focus highlights, and no hard touch requirements).
- **Backward Compatibility:** Maintain support down to Android 8.0 (API 26).

---

## Submitting Pull Requests

1. **Create a branch:**
   ```bash
   git checkout -b feature/your-feature-name
   ```
2. **Make focused commits:** Write clear, descriptive commit messages describing *what* changed and *why*.
3. **Verify tests pass:** Run `./gradlew test` and test on a real device or emulator before opening a PR.
4. **Open a Pull Request:**
   - Provide a clear title and description of the changes.
   - Reference any related issues (e.g., `Fixes #12`).
   - If UI changes were made, attach screenshots or a short GIF showing both phone and TV interfaces.
