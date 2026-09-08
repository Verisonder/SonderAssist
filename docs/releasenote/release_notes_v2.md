## SonderAssist 2

### The power menu can be closed during a theft

Android decides what the power button does by reading two settings, so the menu does not
have to be hidden once it appears — it can be stopped from being built at all. With this
turned on, both routes into it (power and volume up, and holding power) stop working the
moment the phone locks itself, and both come back when you unlock.

**Off by default, and it needs Shizuku.** Writing those settings needs shell access, which
an ordinary app does not have. Without Shizuku running the feature does nothing, and the
settings screen says so rather than letting you believe otherwise. The screen still locks
and the alarm still sounds either way — this is an addition to the lock, never a part of
it.

Both settings are saved before either is changed, and put back exactly as they were. The
restore runs from three places: unlocking, restarting the phone, and opening the app. That
matters more than the feature itself — a phone left with no power menu and nothing running
to restore one would be worse than the problem being solved.

Holding power for about ten seconds still restarts the phone. That happens below Android
and no software can prevent it.

### Everything from 1.8

J.A.R.V.I.S mode: a dark screen carrying your lock-screen message, its own sound played
once, and a three-part gesture to clear it — two fingers tapped, two fingers up, then one
finger left to right.
