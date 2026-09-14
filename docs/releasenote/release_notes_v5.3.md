## SonderAssist 5.3

5.2 added a way to see why the strap was doing nothing, and then could not tell its own
two answers apart. This fixes that, and the most likely cause along with it.

### Opening the app now starts the strap

Only the switch started it. If the strap was already switched on from an earlier version,
nothing ever called for it, and with the watch off there was no listener at all — which
looked exactly like Bluetooth not working.

### It says whether it is listening

At the top of the trail, in red when it is not. An empty trail used to mean two completely
different things — the phone never said anything, or the app was never running to be told —
and they need opposite fixes.

The trail also writes a line the moment the service starts, so "listening for Bluetooth"
with nothing after it is now a real answer rather than an absence.

### The Bluetooth listener is now separate and exported

It shared a listener with the screen events, which is registered as private to the app.
That should not matter — these are protected broadcasts only the system can send — but it
could not be ruled out, and ruling it out was going to cost another release. Exporting it
gives no other app a way in, because nothing but the system is allowed to send these.

### What to look for

- **"Running. The strap is listening."** and events appear when a device goes off — it
  works.
- **Listening, but a device going off adds nothing** — the phone is not delivering these
  broadcasts to this app at all.
- **Still not running** — the service is being refused or killed on start.

No sensitivity thresholds changed. None have changed since 4.4.
