## SonderAssist 0.1

First build. **It is not finished and the detection is not tuned.**

SonderAssist locks the screen when your phone is pulled out of your hand.

Android's own Theft Detection Lock already covers a snatch followed by the thief running,
biking or driving off. It waits for that getaway before it acts, which leaves the ordinary
case open: someone takes the phone and simply walks away, or steps through a closing train
door. SonderAssist fires on the grab itself.

Leave Theft Detection Lock on. This covers a case it does not.

### How it decides

Your hand grips the bottom of the phone, so the grip opens toward the top and that is the
only direction the phone can leave. The detector watches for a sharp pull along the phone's
own long axis, with real movement behind it rather than just a knock, followed by motion
that does not settle. A drop, a put-down, and a downward yank are all rejected.

It only watches between unlocking the phone and the screen going off. With the screen off
there is nothing to protect, because the phone is already locked.

### What to expect

**It will sometimes lock when you did not want it to.** That is deliberate. A wrong lock
costs one fingerprint touch, so the tuning aims never to miss a real grab rather than never
to fire wrongly. If that is not the trade you want, this is not the app for you.

**The thresholds are guesses.** They come from reasoning about the physics, not from
recorded motion. Tuning against real recordings is the next piece of work, and until it is
done the accuracy is unknown rather than good.

### Permissions

SonderAssist uses Device Admin, declaring only the force-lock policy. It is not an
accessibility service — those are unreliable on several manufacturers' software, and a
protection that silently stops working is worse than none.

An active device admin cannot be uninstalled until it is turned off, so there is a "Turn
off protection" button in the app that hands it back in one tap.

There is **no internet permission**. Motion data describes how and where you carry your
phone, and the simplest way to promise it goes nowhere is to make sending it impossible.

### Known gaps

- Not tuned against real recordings.
- Assumes the phone is held upright and gripped low. Landscape use is not handled.
- Does not cover a phone taken from a table, a pocket, or a bag.
- No trace capture screen yet, which is what tuning needs.
