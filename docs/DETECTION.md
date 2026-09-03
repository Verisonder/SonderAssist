# How the detection works

## Scope

**A grab from the hand, with no getaway.** Nothing else, for now.

Android's Theft Detection Lock covers snatch-then-run with a model trained on far more
data than this will ever see. It waits for running, biking or driving before acting, and
that wait is the gap: a thief who walks away, or steps off a train, never triggers it.

## The window

The detector only runs between `ACTION_USER_PRESENT` and `ACTION_SCREEN_OFF`.

This is not battery tuning that happens to be convenient. When the screen is off the phone
is already locked and there is nothing left to protect, so the window where a grab costs
anything is exactly the window where the app listens. Everything else follows from that.

## The geometry, which is the whole idea

The hand grips the **bottom** of the phone. The thief takes the **top** and pulls up and
away. That is not one possibility among several — it is how the grip works. A hand wrapped
around the lower half opens toward the top, so the only direction the phone can leave is
along its own long axis, out through the top edge. Real pulls spread perhaps forty degrees
either side of it and never reverse it.

So the trigger is not the size of the jerk but **its direction in the phone's own frame**:
a sharp positive transient along +Y, from the bottom edge toward the top. That is a far
narrower target than raw magnitude, and most of what a phone does in an ordinary day does
not produce it.

## The signature

1. **A positive axial jerk.** The derivative of the linear acceleration along +Y. Jerk
   rather than acceleration, because any brisk normal movement matches acceleration.
2. **A real pull, not just a spike.** The acceleration itself must also be sustained along
   +Y. A knock against the phone spikes the derivative without the phone going anywhere.
3. **Rotation, as evidence rather than a gate.** An off-axis grab pivots the phone about
   the hand and the gyroscope sees it — but a pull straight up the long axis runs through
   that pivot, produces almost no torque, and is the middle of the three directions. So
   rotation *lowers the jerk needed* instead of being required.
4. **Motion that does not settle.** A put-down comes to rest inside about a second. A phone
   in someone else's hand does not.

## Gravity

Removed with a low-pass estimate, not `TYPE_LINEAR_ACCELERATION`, whose fusion smooths
away the transient this exists to find. The estimate also gives the tilt, which is what
keeps "+Y in the phone's frame" meaningful at any holding angle.

**`gravityAlpha` is not a free parameter.** The time constant is roughly
`sampleInterval / (1 - alpha)`. At 0.85 and 100 Hz that is 67 ms — the same order as the
transient itself, so the filter tracks the pull, subtracts it as though it were gravity,
and the straight axial grab silently stops firing. It was set to 0.85 first and the
failure was found by replaying the fixtures. 0.98 gives about half a second.

## What is rejected

| Case | How it is told apart |
|---|---|
| Put down on a table | Settles inside the confirmation window |
| Dropped | Free fall reads near zero g, which no hand-to-hand grab does |
| Yanked downward | Negative axial transient — the grip does not open that way |
| Knocked | Derivative spikes but the acceleration is not sustained |
| Phone on a table | No tremor — a held phone always carries some |
| Already being waved about | Tremor above the upper bound, where a grab is indistinguishable |

## Thresholds

**Every default in `SnatchDetector.Tuning` is a guess.** They come from reasoning about the
physics, not from data, and none of them should be trusted until traces say so.

The way to fix that: record real motion with the capture screen, replay it through the
detector in a unit test, and move the numbers until every grab fires and no put-down does.
Traces belong in `app/src/test/resources/traces/` and get asserted by name.

Traces worth recording, at minimum:

- a grab from the hand, several times, by someone else
- putting the phone down on a table, gently and carelessly
- dropping it onto a sofa
- handing it to someone
- pulling it out of a pocket and putting it back
- walking, sitting down, getting out of a car
- the phone going into a bag

The last four matter more than the first. Anything can be made to fire; the work is making
it not fire during a normal day.

## Sensors

`TYPE_ACCELEROMETER`, not `TYPE_LINEAR_ACCELERATION`. The latter is a fused, smoothed
estimate, and the smoothing removes precisely the sharp transient this looks for.

`SENSOR_DELAY_GAME`, about 50 Hz. `NORMAL` is roughly 5 Hz and would step straight over a
transient lasting tens of milliseconds. `FASTEST` buys nothing at this scale and costs
battery for the whole session.

The gyroscope is optional. Without one the axial channel carries the decision alone and
every grab has to clear the higher bar, which is worse but still works.

## Still to decide

- **Whether to use a wearable.** A paired watch or earbuds give a Bluetooth RSSI collapse
  when the phone moves away from the body — a stronger and cheaper signal than anything the
  accelerometer can produce on its own.
- **What happens after the lock.** Vibrate? Nothing? A sound is worth considering and worth
  arguing about: it might make a thief drop the phone, or make them run.
- **An undo.** A false lock the owner can dismiss quickly is a different experience from one
  that just happened, and the aggressive tuning makes that matter more than it usually would.
