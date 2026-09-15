## SonderAssist 5.8

A second way to detect a grab.

A new **Detector** control on the home screen: **v1**, **v2** or **Both**. It starts on v1, so
nothing changes until you pick.

- **v1** is the detector every release so far has used. It looks at how sharply the phone
  jerks toward its top edge.
- **v2** measures how fast the phone is actually sent away. A knock or a tap moves nothing, so
  it ignores them, and it behaves the same whatever rate the phone's sensor runs at.
- **Both** runs them side by side. Either one firing locks.

"Right now" shows what each one thinks, and the alert trail says which one fired.

Changing the detector or the sensitivity now applies straight away, not at the next unlock.
