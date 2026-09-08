## SonderAssist 1.7

### J.A.R.V.I.S mode has its sound back

Version 1.6 shipped without the audio file in it. The release build shrinks unused
resources, and it decided the alarm clip was unused and removed it — while leaving its id
behind, so the app looked it up, found nothing, and went quiet. The clip is now held
against the shrinker explicitly.

If you installed 1.6, J.A.R.V.I.S mode could not have made a sound. It can now.

### The tile says what it did

Tapping the quick settings tile has been doing nothing on at least one phone, without an
error to explain it. The tile now records every step it takes, and the main screen shows
the last one under the status.

It records the state it painted as well as the taps it received. That distinction is the
point: a tile painted as unavailable is not interactive, and the system will not deliver a
tap to it at all — so a line that still reads "painted" after a tap means the tap never
arrived, which is a different problem from the watch failing to start.

This is a diagnostic, not a fix. It exists to say which of the two is happening.
