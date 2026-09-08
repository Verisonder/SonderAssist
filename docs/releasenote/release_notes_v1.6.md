## SonderAssist 1.6

A small release, all of it around J.A.R.V.I.S mode.

### The mode turns the sound on with it

The mode describes a sound of its own, but the sound was still gated behind the separate
"Play a sound" switch underneath it. Turned on with that switch off, the mode promised
something it could not do. Turning the mode on now turns the sound on as well, and the
switch moves where you can see it.

### A sound that fails now says so

If the clip could not be played at all, the app went quiet and recorded nothing — which
looks exactly like the sound being switched off. The failure is now written to the report
on the main screen.

### The mode no longer names itself twice

The settings section carried its own title twice over and never said what the switch
actually did. It says "Replace the alert screen" now.

---

**Testing the gesture:** the screen appears immediately and the sound waits out the delay
below it, five seconds by default. The gesture stops a sound that has not started yet, so
clearing the screen quickly will always be silent. Set the wait to zero while testing, or
leave the phone alone until the clip starts.
