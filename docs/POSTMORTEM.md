# Postmortem: Why Reed–Solomon Integration Failed



## Summary

We attempted to add Reed–Solomon (RS) forward error correction on top of the
rolling-shutter optical link to push accuracy beyond the ~50% we got with simple
dual-transmission + Hamming-distance correction. The integration did not produce
usable results, and we shelved it. This document explains why, because we think
the failure mode is more instructive than the working parts of the prototype.

## What we assumed going in

Reed–Solomon corrects errors at the level of **symbols** (groups of bits, typically
bytes), not individual bits. Given a codeword of `n` symbols with `k` data symbols,
RS can correct up to `(n-k)/2` symbol errors — *as long as the decoder knows which
positions in the received stream correspond to which symbol in the codeword.*

We treated RS as a drop-in layer: take the bitstream we were already recovering
from the rolling-shutter stripes, chunk it into symbols, run it through an RS
decoder (we looked at `ZXing`'s implementation), and expect a clean output.

## What actually went wrong

RS's error-correcting guarantees only hold if the **mapping between received
stripe positions and codeword symbol indices is exact**. Our rolling-shutter
decode pipeline does not preserve that mapping reliably:

- Each frame gives us a set of bright/dark stripes whose count and spacing
  depend on exposure timing, water turbulence, and how well the two phones'
  frame rate and flash timing stay synchronized over the transfer.
- Stripe count and boundaries **drift** frame to frame — a stripe that maps to
  RS symbol index `i` in one frame can shift by one or more positions in the
  next, especially after any dropped or partially-captured frame.
- RS is designed to correct **content** errors at known symbol positions
  (or erasures at known positions, which correct even more errors per symbol).
  What we had instead was **positional/alignment drift** — the decoder
  couldn't reliably tell *which* symbol a given chunk of recovered bits was
  supposed to be, because the coordinate mapping between transmitted stripes
  and codeword indices wasn't preserved through the underwater channel.
- Feeding misaligned data into an RS decoder doesn't produce "mostly correct
  with a few fixed errors" — it produces a decode that looks superficially
  valid but is fundamentally the wrong codeword, or an outright decode failure,
  because RS has no way to tell "content is wrong" apart from "content is in
  the wrong place."

In short: **RS corrects errors in content, not errors in coordinates.** We
needed a synchronization/alignment mechanism first (a preamble, sync marker,
or timing recovery scheme) to guarantee that received bit N always maps to
codeword symbol position N, and we didn't have one. Adding RS on top of an
already-unsynchronized bitstream made the failure harder to diagnose, not
easier.

## What we'd do differently

1. **Fix synchronization before adding FEC.** Add an explicit preamble/sync
   pattern (e.g. a known bit sequence at the start of each frame or block)
   so the receiver can re-anchor its symbol-boundary alignment every block,
   rather than assuming it stays locked for the whole transfer.
2. **Add line coding first.** Manchester or RLL encoding bounds the maximum
   run length of identical bits, which (a) prevents long strobe bursts from
   saturating/blinding the sensor and (b) makes stripe-boundary detection much
   more robust, since the receiver can use transitions themselves as a timing
   reference.
3. **Add interleaving.** Even with alignment fixed, water-induced disturbances
   tend to cause burst errors (several consecutive corrupted stripes). A block
   interleaver spreads a burst across multiple RS codewords so each individual
   codeword only sees isolated single-symbol errors — which is the error
   pattern RS is actually good at correcting.
4. **Only then, re-attempt RS**, on a bitstream where symbol boundaries are
   trustworthy.

## Why we're documenting this instead of hiding it

The physical-layer half of this project — using rolling shutter as a
software-defined high-speed optical receiver on unmodified phone hardware —
worked. The FEC layer didn't, and the reason isn't "we didn't have time," it's
a specific, identifiable design gap (missing synchronization) that any future
contributor to this repo should fix *first*, before touching Reed–Solomon
again.
