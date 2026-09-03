## SonderAssist 1

Locks the screen when your phone is pulled out of your hand.

Android's own Theft Detection Lock already covers a snatch followed by the thief running,
biking or driving off. It waits for that getaway before acting, which leaves the ordinary
case open: someone takes the phone and simply walks away, or steps through a closing train
door. SonderAssist acts on the grab itself.

Leave Theft Detection Lock on. This covers a case it does not.

### How it decides

Your hand grips the bottom of the phone, so the grip opens toward the top and that is the
only direction the phone can leave it. The detector looks for a pull that starts suddenly,
runs toward the phone's top edge, has real force behind it rather than being a knock, and
does not settle within the next three-quarters of a second.

A drop is rejected — a falling phone reads near zero on the accelerometer, and a dropped
phone is not a stolen one. A put-down is rejected, because it comes to rest. A downward or
sideways yank is rejected, because a hand gripping the bottom does not open that way.

It watches only between unlocking the phone and the screen going off. With the screen off
there is nothing to protect, so nothing runs — it cannot drain the battery in your pocket.

### What happens when it fires

The screen locks immediately. Your message appears over the lock screen, on a plain colour
or a picture you choose. If you have turned the sound on, it waits a few seconds and then
plays through the alarm channel, so it is heard even on silent, a set number of times.

The wait before the sound is deliberate. The detector fires on thin evidence because a
wrong lock costs one fingerprint touch — a wrong lock that instantly blares in a quiet room
costs much more. Someone who knows the PIN stops it before it makes a noise. Someone who
does not, cannot.

### Settings

- **Sensitivity**, showing the actual figures it sets rather than an adjective.
- **A message** shown over the lock screen. Anyone holding the phone can read it, so it is
  a message to a stranger, not a private note.
- **A background picture** for that screen, or a plain colour.
- **A sound of your choosing**, and how many times it plays.
- **How long the sound waits** before it starts.

### Being honest about it

**It will sometimes lock when you did not mean it to.** That is the trade, made on purpose.

**It cannot tell a theft from any other motion of the same shape.** Yanking your own phone
off a table, standing up quickly, pulling it out of a tight pocket — these look the same to
an accelerometer, and no amount of tuning separates them. Distinguishing properly needs a
second signal that says the phone left *your* body, which is not in this version.

**The thresholds have not been tuned against recorded motion.** They come from reasoning
about the physics. The sensitivity slider exists because there is no known correct setting
yet.

### Permissions

Device Admin, declaring only the force-lock policy — not an accessibility service, which
several manufacturers kill silently. A "Remove permission" button is at the bottom of the
screen, because Android blocks uninstalling while the app can lock the screen.

There is **no internet permission**. Motion data describes how and where you carry your
phone, and the simplest way to promise it goes nowhere is to make sending it impossible.
