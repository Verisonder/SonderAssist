# SonderAssist

Locks the screen when your phone is taken out of your hand.

## Why this exists

Android already has **Theft Detection Lock**, and it is good. It uses motion sensors and
an on-device model to spot a phone being snatched, and it locks the screen. But it waits
for the getaway — it looks for the snatch *followed by* running, biking or driving.

That leaves a gap, and it is the ordinary case. Someone takes your phone and walks off.
Someone takes it as the train doors close. Neither produces a getaway the model
recognises, so the screen stays unlocked with everything on it.

SonderAssist fires on the grab itself.

**This is not a replacement for Theft Detection Lock.** Leave that on. This covers a case
it does not.

## What it does not do

- It does not get your phone back. It protects what is on the screen, not the hardware.
- It does not help if the phone was already locked, because there is nothing to protect.
- It does not currently cover a phone taken from a table, a pocket, or a bag. Those are
  different signatures and each needs its own work.
- It is not accurate yet. See below.

## Accuracy, honestly

A platform feature shipping to a billion phones has to tune for almost no false alarms,
because every wrong lock is a support burden. This does not. A wrong lock here costs one
fingerprint touch, so it fires on evidence that would be far too thin for Google — and
that asymmetry is the only reason a small app can catch what theirs misses.

Expect it to lock sometimes when you did not want it to. That is the trade being made on
purpose, and if it is not the trade you want, this app is not for you.

## The accessibility permission

To turn on the lock screen without Device Admin, the app needs an accessibility service.
Android's grant screen will tell you the app can observe everything you do.

It cannot. `ScreenLockService` has an empty `onAccessibilityEvent`, asks for no window
content in its configuration, and calls exactly one API — `GLOBAL_ACTION_LOCK_SCREEN`.
Android shows the same warning for every service of this kind because the platform cannot
tell them apart. Read the file; it is thirty lines.

There is **no `INTERNET` permission**. Motion data describes how and where you carry your
phone, and the simplest way to promise it goes nowhere is to make sending it impossible.

## Building

No Gradle wrapper is committed — CI installs Gradle 8.9, so no binary jar sits in source
control.

```
gradle testDebugUnitTest
gradle assembleDebug
```

## Licence

GPL-3.0-only.
