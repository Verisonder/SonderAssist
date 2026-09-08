## SonderAssist 3.1

### Swiping up no longer throws the alert away

Swiping up from the bottom of the screen is the home gesture, and no app can block it. But
the alert screen was marked to finish itself the moment it stopped being visible, so the
swipe did not merely navigate away from it — it destroyed it, leaving an ordinary lock
screen behind and no way back.

The alert now survives. With the guard switched on, swiping up is handled exactly like the
assistant covering the screen: the phone goes dark and silent, and waking it brings the
alert and the sound back.

That flag was also quietly at odds with the guard added in 2.2, which depends on the alert
staying alive behind a dark display. It worked, but only by luck.

It was there to keep the alert out of the recent apps list, and the other flag already
does that.
