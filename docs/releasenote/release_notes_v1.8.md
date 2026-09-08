## SonderAssist 1.8

### The J.A.R.V.I.S gesture has a third leg

Clearing the screen now takes three deliberate movements instead of two:

1. Two fingers tapped once
2. Two fingers swiped up
3. One finger swiped left to right

Each leg is measured from where the fingers are when that leg begins, not from where they
first landed — so the swipes work whether or not you lift between them. A single diagonal
drag cannot satisfy two legs at once, and a half-finished gesture is forgotten after four
seconds rather than waiting to be completed by an accident later.

The rings brighten a step further at the second stage, so the screen shows which leg it
thinks it is on.

### Your message shows in J.A.R.V.I.S mode

The message you set for the lock screen now appears on the J.A.R.V.I.S screen as well,
over the field and under the rings. The screen is no longer blank.

### The tile keeps its whole trail

The diagnostic added in 1.7 recorded each step the tile took, but every step overwrote the
one before it — and the tile paints itself last, so the line always ended up reading
"painted" and the tap that caused it was gone. It now keeps the last few steps in order.
