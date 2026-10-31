# Opening Droidcast

Droidcast provides three independent launch routes. Android owns system gestures and key mappings; configuring a custom chord does not automatically configure the system assistant.

| Route | Setup | Keeps the current assistant? |
| --- | --- | --- |
| App icon / Quick Settings tile | Open the app, or add its tile | Yes |
| Android assistant gesture/key | Choose Droidcast as the default digital assistant | No |
| Custom global chord, such as Alt+Q | Enable the optional keyboard accessibility service | Yes |

## Android assistant, without the keyboard service

Open **Droidcast Settings → General → Android assistant → Choose Droidcast**. Android opens its **Default digital assistant app** settings. Select Droidcast there. This replaces your current digital assistant, but does not require the keyboard accessibility service, microphone access or display-over-other-apps permission.

After selecting Droidcast, open **Navigation settings** in that same card:

- **Gesture navigation:** swipe diagonally inward from either bottom corner. Turn on **Swipe to invoke assistant** if Android offers that option.
- **Button navigation:** press and hold the on-screen **Home** button. Turn on **Hold Home for assistant** if offered.

The manual path is **Android Settings → System → Gestures or Navigation mode**. Droidcast tries the AOSP navigation page, then falls back to Android Settings with manual directions. It never changes navigation mode or assistant gesture switches itself.

Android controls these mappings. Some devices assign Home to Circle to Search or another system action; adjust that mapping in Android settings. Names and availability vary by manufacturer, including Lenovo. Choosing Droidcast does not guarantee that an OEM exposes both gestures. The existing app icon and tile remain available.

An assistant key also works where Android provides one. Stock Android maps **Meta+A** to the assistant; Lenovo/OEM mappings can differ. This is a system shortcut, independent of the custom global chord saved inside Droidcast. The default assistant can be changed back in Android's settings at any time.

The implementation uses system-bound VoiceInteractionService/VoiceInteractionSession components and routes into the existing translucent palette. Assist text and screenshot context are disabled. No microphone permission is declared or requested. The generic RoleManager request dialog is not used: the stock Android 16 emulator rejects ASSISTANT as a non-requestable role, while its default-assistant settings supports selection.

This follows the Android entry architecture of [Launchpad Search](https://github.com/jaxparrow07/launchpad-search/blob/67e10897b9d49e6d5aac39816e104a49947293dd/app/src/main/java/com/devrinth/launchpad/services/LaunchpadSessionService.kt); its code is not copied. See [Android's assistant-session API](https://developer.android.com/reference/android/service/voice/VoiceInteractionSession), [Google's corner-swipe instructions](https://support.google.com/pixelphone/answer/7443425?hl=en) and the [stock keyboard mapping test](https://android.googlesource.com/platform/frameworks/base/+/b492f92ec805ebb46f0296ff729acb76c7d91a45/services/tests/wmtests/src/com/android/server/policy/KeyboardSystemShortcutTests.java). The touch validation record is [assistant-touch.md](validation/assistant-touch.md).

## Custom global hotkeys

For an arbitrary chord such as Alt+Q from another app, Android must deliver the key events to the optional keyboard service. See [Keyboard shortcuts](SHORTCUTS.md), including the approval path for downloaded APKs. The app icon and tile work when both the assistant role and keyboard service are declined.

## Immediate search updates

Palette result scrolling is immediate. Typing does not trigger a smooth scroll to the first result. The search field has fixed height, matching placeholder/text line height, and the palette window has no entry/exit animation. The app does not change Android's global animation settings or override system keyboard animations.
