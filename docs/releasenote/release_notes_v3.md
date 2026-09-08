## SonderAssist 3

Everything here is the location report added in 2.5.

### The Telegram pin keeps moving until you stop it

Only the text messages are counted now, because each one is a real message to a real
number. Moving the pin costs nothing, so it carries on at your interval until it is
stopped, with the longest live period Telegram allows.

Unlocking the phone stops it, and takes the pin down rather than leaving it counting down
while no longer moving. There is also a Stop button in the app — though on a phone that has
been taken, that button is out of reach, and unlocking is the one that matters.

### It says, continuously, that the location is still going out

A live pin that has stopped updating looks exactly like one that has not. The group cannot
tell the difference; only the phone knows. So while the report is running the phone carries
a standing notification saying so, updated each time a position goes out, with a Stop
action on it.

### Check the setup before it matters

A new button tests every part and writes the result down: the bot token, the chat id — by
sending a real message to the group, which is the only thing that proves it — the phone
number, permission to send messages, permission to read the location, and whether the phone
has a position stored at all.

Every one of these can only fail at the worst possible moment, when nobody is watching. Now
they fail on a button instead.

### The chat id can be pasted in any shape

The bare number, the web.telegram.org link, or a markdown link carrying both. The number is
taken out of it, minus sign included, since group ids need it. A public channel's @name
still works.
