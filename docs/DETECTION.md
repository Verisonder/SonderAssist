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

## The signature

A phone leaving a hand against resistance is not shaped like a phone being put down,
handed over, or pocketed.

1. **A jerk transient.** Jerk — the derivative of acceleration — is the discriminator, not
   acceleration, which any brisk normal movement matches. Fingers grip, the phone is
   pulled, the grip fails, and acceleration changes almost discontinuously.
2. **A rotation burst.** A grab is never on the centre of mass, so the phone pivots out of
   the grip and the gyroscope spikes alongside. A phone set down flat produces jerk with
   almost no rotation.
3. **Motion that does not settle.** A put-down comes to rest inside about a second. A
   phone in someone else's hand does not.

## What is rejected

| Case | How it is told apart |
|---|---|
| Put down on a table | Settles inside the confirmation window |
| Dropped | Free fall reads near zero g, which no hand-to-hand grab does |
| Straight shove | Jerk without rotation |
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

The gyroscope is optional. Without one the detector runs on jerk alone, which is worse, and
`Tuning.requireRotation` is set to false so the app says so rather than quietly changing
behaviour.

## Still to decide

- **Whether to use a wearable.** A paired watch or earbuds give a Bluetooth RSSI collapse
  when the phone moves away from the body — a stronger and cheaper signal than anything the
  accelerometer can produce on its own.
- **What happens after the lock.** Vibrate? Nothing? A sound is worth considering and worth
  arguing about: it might make a thief drop the phone, or make them run.
- **An undo.** A false lock the owner can dismiss quickly is a different experience from one
  that just happened, and the aggressive tuning makes that matter more than it usually would.
