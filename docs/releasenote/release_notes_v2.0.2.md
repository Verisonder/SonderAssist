## SonderAssist 2.0.2

### The app now says what it needs, and what happened

The alert screen needs two permissions under **Other permissions**, and it needs both:
**Open new windows while running in the background** and **Show on Lock screen**. One lets
the window be created at all, the other lets it sit over the keyguard.

Without either, the phone still locks and the alarm still sounds — but the screen never
appears, which makes it look half working rather than broken. **Restarting the phone can
turn them off again.** Both are now named on the main screen, with a button that opens the
permission editor.

### A step-by-step record of the last alert

Every stage is written down as it happens: the grab, the lock, the power menu, the
notification, the request for the screen — and a final line written by the alert screen
itself when it opens.

That last line is the only proof the screen actually appeared. A background window the
system refuses is dropped in silence rather than reported as an error, so nothing else in
the app can tell the difference. If the line is missing, the screen never opened.

### Full-screen alerts are checked properly

Android 14 and above can withhold permission for full-screen alerts, and does not grant it
by default to a newly installed app. The main screen now says whether it is allowed and
offers to open the setting.
