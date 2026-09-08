## SonderAssist 1.1.1

A small release. Two fixes, no new behaviour in the detector.

### The tile answers immediately

Tapping the tile appeared to lag: it would show the state it had just been tapped out of,
and correct itself a moment later. Starting or stopping a service is queued rather than
immediate, so the tile was reading a value that had not changed yet and painting the old
state over the new one.

It now shows what was asked for as soon as it is tapped, and the service still corrects it
if it does not actually come up. If the service is refused or killed on startup — which
happens on some phones — the tile notices and goes back to Off instead of claiming to be
watching.

### Plainer wording on the sensitivity screen

The figures the slider moves were labelled *jerk*, which is the correct name for the
quantity and no help at all in reading the screen. They are called acceleration now.
