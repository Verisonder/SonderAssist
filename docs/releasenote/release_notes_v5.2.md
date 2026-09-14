## SonderAssist 5.2

The strap was switched on and did nothing. This is why, and how to see it.

### It needed the watch to be running, and never said so

The strap lives inside the same service that watches for a grab, so with the watch
switched off there was no listener at all — a switch on the main screen quietly depending
on a different switch, with nothing on screen admitting it.

The strap now starts and keeps that service by itself. Turning the watch off stops
watching for a grab and leaves the strap alone. It survives a reboot too, which it did not
before.

### The list now says which devices are actually connected

Only paired devices can be listed — nothing is scanned — but the app does see every
connection and disconnection as it happens, so a device is marked **connected** once it has
actually been seen.

### What the strap has seen

A new trail under the wait, showing the last six Bluetooth events: which device, whether it
came or went, and whether it was the one you chose.

This is the answer to "it did not fire". Turn a paired device off and come back:

- **Events appear** — the phone is telling the app about Bluetooth, and the trail says what
  happened after that.
- **Nothing appears at all** — the phone is not delivering these broadcasts to SonderAssist,
  and neither the wait nor the device you picked was ever the problem.

The strap also keeps its own trail rather than writing into the alert one, so a pair of
earbuds going flat no longer wipes the record of the last real alert.

No sensitivity thresholds changed. None have changed since 4.4.
