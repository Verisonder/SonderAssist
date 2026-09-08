## SonderAssist 2.2

All of this is the alert screen guard, which is off by default. With the switch off,
nothing here changes.

### Covered means dark and silent, and it comes back

The assistant can open over the lock screen on a long press of the power button, and it
lands on top of the alert. That is a way to clear the screen without knowing the PIN.

When it happens now, the phone looks switched off: the display goes out and the sound
stops. Whoever is holding it sees nothing to work with. Turn the screen back on and the
alert is there again, with the sound. It can be repeated as many times as they try.

The alert is no longer closed when it goes dark — it waits behind the display. In 2.1.1 it
closed, so the screen came back to nothing at all.

### It no longer fires while the alert is arriving

The guard watches for the alert being covered, but the same signal arrives during the
launch itself, while the lock, the keyguard and the display are still settling against
each other. It now ignores the first second after the alert appears, and ignores anything
that happens while the display is already off — that is the screen going to sleep, not
something covering it.

Both of those produced the same symptom in earlier builds: a phone that went dark and
stayed dark, with only the alarm running.
