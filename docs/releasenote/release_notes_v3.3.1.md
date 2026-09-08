## SonderAssist 3.3.1

### The alert no longer opens twice

Two things were causing it, and both are fixed.

The intent that opens the alert carried a flag that tears the task down and builds a new one
on every start. That worked directly against the launch mode the screen was given, which
exists so a second start is handed to the screen already showing rather than making another
one.

And when the alert is put back after being covered or swiped away, it now takes one route
in rather than two. The notification and the direct start are both kept for the first
alert, because either can be refused and the app may not be in front — but by the time the
alert is coming back the screen is already on, which is exactly when the direct start is the
one that works.
