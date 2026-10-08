# Google Play Console notes

The store listing lives in `fastlane/metadata/android/en-US/` (title, short and
full description, icon, feature graphic, phone screenshots), in the layout
read by `fastlane supply` and F-Droid. It can also be copied in by hand.
`km-KH/` holds the Khmer title and descriptions; without its own images, the
Khmer listing shows the English ones.

Answers for the *App content* section, from what the app actually does:

| Section | Answer |
|---------|--------|
| Privacy policy | Host `docs/privacy-policy.md` at a public URL and enter it. |
| Ads | No ads. |
| App access | All functionality is available without restrictions or login. |
| Data safety | No data collected, no data shared. The release APK requests only `VIBRATE`, with no network permission. Progress and settings stay on the device and are included in the user's own Android backup (`data_extraction_rules.xml`). |
| Content rating | No violence, fear, language, gambling, user interaction, data sharing, location or purchases. |
| Target audience | Your choice. Including children under 13 brings in the Families policy, which the app already meets (no ads, no data). |

Before each upload: raise `versionCode` in `app/build.gradle.kts`, then run
`./gradlew bundleRelease` with the upload key configured (see the README).
