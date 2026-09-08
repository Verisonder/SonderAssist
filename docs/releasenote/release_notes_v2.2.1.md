## SonderAssist 2.2.1

The alert screen guard flickered instead of going dark. Off by default, so with the switch
off nothing here changes.

### The screen no longer turns straight back on

Locking the moment the assistant appeared raced its own launch: the display went out and
the window still coming up turned it back on inside a tenth of a second, leaving the lock
screen with the assistant over it.

Two things caused that, and both are fixed. The guard now waits a second for whatever
covered the screen to settle before locking, so the lock is the last thing to happen
rather than the first. And the alert screen no longer wakes the display on its own while
it is meant to be dark — that setting is right when the alert first arrives and exactly
wrong afterwards, because it undid the lock the moment the window came back.

Turning the screen on by hand still brings the alert straight back, with the sound.
