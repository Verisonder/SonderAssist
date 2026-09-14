## SonderAssist 5.1

The strap, where you can actually find it.

### It is on the main screen now

It was behind the settings button, down with the alert options. That was the wrong place.
It is the second of the two ways this app decides the phone has been taken, and the first
one — the sensitivity slider — is on the main screen. They now sit together.

### Choosing the device works

The list of devices only appeared once the switch was on *and* Bluetooth permission had
been granted, and if either was missing you got a paragraph of text and no way forward.

- The empty list now has an **Allow** button for the permission and a **Pair a device**
  button that opens the phone's own Bluetooth settings. Two different reasons the list can
  be empty, so both ways out are offered rather than leaving you to work out which one you
  are in.
- The list refreshes every time you come back to the app. Both things that change it
  happen outside SonderAssist, so it used to stay empty right after you had gone and fixed
  exactly what it asked for.
- It says outright that nothing happens until a device is chosen.

### The proximity sensor is no longer switched on for nothing

4.6 read the proximity sensor and 4.7 removed that, but left the sensor itself registered
on every unlock, feeding a stream nothing was listening to. It never affected detection —
nothing read it — it just cost battery quietly. Gone, along with the unused field it wrote
into every reading and the extra column it added to the trace format.

No sensitivity thresholds changed in this release, and none have changed since 4.4. Your
slider is where you left it.
