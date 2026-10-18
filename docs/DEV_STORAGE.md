# Development Storage

Verified on 2026-09-25. Keep Windows and installed applications in their existing locations.

| Data | Canonical location |
| --- | --- |
| StudySpace source, backend and PostgreSQL | `D:\baitaplon` |
| Homestay source | `D:\LapTrinhMobile` |
| Gradle user cache | `D:\DevData\Gradle` |
| Android SDK | `D:\DevTools\Android\Sdk` |
| Android emulator data | `D:\DevData\Android\avd` |
| StudySpace temporary build files | `D:\DevData\Temp\StudySpace` |
| Previous report HTML | `D:\baitaplon\docs\reference\baocao-cu.html` |
| Flutter migrated from Downloads | `D:\DevTools\flutter-3.35.2` |
| Shared Pub cache | `D:\DevTools\PubCache` |

The former Gradle, SDK and AVD locations on C are junctions to D. Do not delete their targets or mistake the junctions for duplicate physical copies. Android `local.properties` now uses D directly.

Build StudySpace with `pwsh -File tools/build.ps1`. Start Medium Phone with `pwsh -File tools/run-medium-phone.ps1`. Restart Android Studio and terminals after changing user environment variables.

Flutter/Pub relocation completed and the junctions and user environment variables were verified on 2026-09-26. C had 43.24 GB free at verification. `tools/finish-shared-storage.ps1` verified every existing copy using SHA-256 before switching the source to a junction and removing the verified staging directory. The other task's existing `D:\flutter\flutter` installation was left untouched.

Docker virtual disks remain at their original location on C. They require a separate Docker-aware migration; do not move or delete VHDX files while Docker/WSL is using them. No factory reset was performed.
