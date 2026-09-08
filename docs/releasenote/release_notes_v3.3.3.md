## SonderAssist 3.3.3

### The battery goes out with the location

Every message now carries the battery level and whether it is charging. Over a long run
that is worth as much as the position: it says how long the phone can be found at all, and
a phone that has *started* charging has been taken somewhere rather than dropped in a
street.

The Telegram pin has no message body, so the battery cannot ride on it. Instead a written
update goes to the same group alongside the moving pin, carrying the position, the accuracy,
the battery and the map link.

How often that written update goes out is now a slider, from five minutes to four hours,
set to an hour by default. It is separate from the interval the pin moves on.

The check button sends the battery line too, so it proves that part reads as well as
everything else.
