## SonderAssist 3.3

### Swiping away no longer darkens the phone or stops the sound

Being covered and being navigated away from are not the same thing, and they are now
treated differently.

The assistant opening over the alert covers it: the phone goes dark and silent, as before.
Swiping up from the bottom does not cover anything — the display is still on and nothing is
hidden — so the alert simply goes back in front, with the alarm still playing. Nothing
stops and nothing has to start again.

The alarm was being silenced for a moment on a swipe, because the two cases shared one
signal. They no longer do.

### The alarm comes back with the alert

When the alert is put back after the screen has been dark, the sound now comes from the
service rather than from the screen. Swiping away destroys the alert screen, so the new one
has no memory of having gone quiet — asking it to remember was asking the wrong part. The
service is the only piece that survives all of this, and it already owns the alarm.

### The location keeps going

Nothing here interrupts a location report. It stops when the phone is unlocked, or when it
is stopped by hand — not when the alert is covered, swiped away, silenced, or dismissed.
