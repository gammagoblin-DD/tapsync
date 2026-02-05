# Apply instructions (repo root)

## Option A: git apply

1) Copy `fft_gain_knob.patch` to your repo root.
2) Run:

   git apply fft_gain_knob.patch

If it fails due to context mismatch, use Option B.

## Option B: overwrite files

Copy the `app/` directory from this bundle into your repo, preserving paths.

---

## What you get
- New FFT Input Gain page with center-zero ring knob.
- RIGHT EDGE paging on TapScreen + FFT page (swipe left/right).
- OSC input parsing for `/audiodevicemanager/params/fftinputgain`.
- OSC output helpers + reset to default.

