## SonderAssist 3.2

### The alert comes back when the screen does

Swiping up from the bottom sends the whole app to the background, not just behind another
window. So waking the phone landed on the ordinary lock screen with nothing left to return
— the alert had gone somewhere the screen could not come back from on its own.

The phone now remembers that an alert is still standing, and puts it up again whenever the
screen comes on. That covers being covered, being swiped away, and the screen being closed
for any other reason: as long as the phone stays locked, the alert keeps coming back.

Two things clear it, and only two: unlocking the phone, and the gesture that dismisses it
deliberately. Those are different intentions and are now treated differently — being
covered hides the alert and wants it back; the gesture ends it and does not.
