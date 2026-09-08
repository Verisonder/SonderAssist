## SonderAssist 2.5

### The phone can tell someone where it is

If it locks itself and is not unlocked in time, its position goes out two ways: a text
message to a number you choose, and a live pin in a Telegram group through a bot.

The Telegram pin is a real live location — it moves in place as further positions arrive,
rather than filling the group with messages. If the pin cannot be updated it sends a new
one, and if that fails it sends the coordinates as text. Something gets through.

The two channels fail differently, which is the point of having both. SMS needs no account
and no internet, only a signal. Telegram needs data but is free, carries a proper map pin,
and reaches a group rather than one handset.

**The wait is deliberately short.** Holding the power button for about ten seconds switches
the phone off, and nothing in software can prevent that — so anything sent has to be sent
before then. A minute by default: long enough to unlock a false alarm, short enough to
usually beat someone who knows about the hold.

The position sent is the last one the phone already knew, not a fresh satellite fix. A cold
fix takes a minute outdoors and may never arrive indoors, and by then the phone may be off.

Off by default. The bot token and chat id are typed on the phone and stored only there.

### About the network

This app carried no internet permission for its first two versions, on the grounds that
motion data describes where a phone has been and how it is carried, and the simplest way to
promise it goes nowhere was to make it impossible.

Reaching a Telegram group needs the network, so that promise no longer holds, and the app
says so rather than leaving the old claim in place. What is narrower is the traffic:
nothing is sent unless this feature is switched on, and the only thing ever sent is a
position after a theft. Motion data still goes nowhere.

### Known limits

Location must be allowed **all the time**, granted by hand — a theft is never while the app
is open. And pulling the SIM stops both channels; that is not fixable from inside the
phone.
