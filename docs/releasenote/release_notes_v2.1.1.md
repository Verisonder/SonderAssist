## SonderAssist 2.1.1

Two fixes to the alert screen guard added in 2.1. With the guard switched off, nothing
here changes.

### It no longer fires before the screen has been seen

The guard watches for the alert screen being covered. But that same moment happens during
the launch itself, while the display is still off from the lock — so it fired immediately,
locked again, and the alert never came up. The phone sat dark until the power button was
pressed, and only then did the screen and the sound arrive.

It now waits until the alert has actually been in front before it guards anything.

### Covered means dark, not a fight

Previously the guard locked and let the screen come back, up to five times. A screen that
reappears is a screen that can be covered again, and a phone that looks switched off is
the better outcome — so it now locks once and stays out of the way.

The alarm is unaffected either way. The service owns it, so silencing it still needs the
PIN.
