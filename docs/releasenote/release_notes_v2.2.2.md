## SonderAssist 2.2.2

The alert screen guard missed one case. Off by default, so with the switch off nothing
here changes.

### Covering the screen right after it wakes is caught now

The guard ignores being paused for a second after the alert first appears, because the
lock, the keyguard and the display all settle against each other while it is arriving and
that looks the same as being covered.

That quiet second was being restarted every time the alert came back, which left the guard
deaf for a second on each return — long enough for something to cover the screen the
moment it woke and not be noticed. Triggering the assistant while the display was off did
exactly that: the screen came on with both the assistant and the alert, and stayed on.

The quiet second now applies only to the first appearance. Returns are guarded straight
away.
