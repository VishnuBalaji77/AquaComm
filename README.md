<div align="center">

# Hackenza-26: AquaComm (Underwater Smartphone OCC)

![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![Language](https://img.shields.io/badge/language-Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Status](https://img.shields.io/badge/status-experimental%20prototype-orange)
![License](https://img.shields.io/badge/license-MIT-blue)

*A software-only underwater data link built from two unmodified smartphones — a flashlight, a camera, and a lot of signal processing.*

</div>

---

## What is this?

AquaComm sends digital data (bytes) between two smartphones **through water**, using nothing but the phone's flashlight as a transmitter and the phone's camera as a receiver — no custom hardware, no LEDs, no waterproof modems.

It's a working demonstration that a phone's **rolling-shutter camera** can be turned into a low-rate optical demodulator, inspired by the *U-Flash* research on smartphone-based optical camera communication (OCC).

## Why underwater optical communication?

Underwater environments are hostile to conventional communication:
- **RF (WiFi/Bluetooth)** is absorbed almost immediately by water.
- **Acoustic modems** work, but are expensive, low-bandwidth, and add latency.
- **Optical (visible light)** communication is cheap and fast, but underwater sunlight, turbidity, and turbulence make reliable decoding hard.

AquaComm explores whether two stock Android phones — with zero extra hardware — can close that gap for short-range, low-bitrate signaling (think: diver-to-diver status codes, not video streaming).

## How it works

```
┌──────────────────┐        underwater optical channel        ┌──────────────────┐
│   TRANSMITTER     │   ── flashlight ON/OFF pulses ──►        │     RECEIVER      │
│  (transmitter-app) │        (through water)                   │  (receiver-app)   │
│                    │                                          │                    │
│  Bit sequence      │                                          │  Rolling-shutter   │
│  → flash pulses    │                                          │  camera captures   │
│                    │                                          │  pulses as stripes │
└──────────────────┘                                          └──────────────────┘
                                                                          │
                                                                          ▼
                                                          ┌───────────────────────────┐
                                                          │  YUV luminance extraction  │
                                                          │  → dynamic thresholding    │
                                                          │  → bit sequence recovery   │
                                                          │  → Hamming-distance repair │
                                                          └───────────────────────────┘
```

**1. Encoding & transmission (`transmitter-app`)**
The transmitting phone toggles its flashlight on/off to encode a bit sequence, submerged and aimed at the receiver.

**2. Rolling-shutter capture (`receiver-app`)**
Instead of needing a strobe frequency faster than the human eye (which phone flash hardware can't do reliably), we exploit the CMOS **rolling shutter effect**: because each row of the camera sensor is exposed at a slightly different instant, a fast-blinking light source appears as alternating **bright and dark horizontal stripes** in a single still frame — effectively turning the sensor into a high-speed sampler.

**3. Signal processing pipeline**
| Stage | What it does |
|---|---|
| YUV extraction | Only the Y (luminance) plane is processed — no RGB conversion — for speed |
| Subframe sampling & caching | Rolling-shutter stripes are sampled and cached in real time without dropping frames |
| Dynamic thresholding | The bright/dark (1/0) decision threshold is recalculated locally from signal mean/variance, adapting to shifting underwater ambient light |
| Error handling (current) | The transmitter sends every bit sequence **twice**; the receiver compares both copies and resolves mismatches using **Hamming distance**, rather than a full FEC scheme |

### Camera configuration (receiver)
To get usable contrast in stripe capture underwater, the receiver camera is manually locked to:
- **Frame rate:** 60 FPS
- **Shutter speed:** 1/1000s
- **ISO:** 1600

## Current status

| Metric | Result |
|---|---|
| Bit-level accuracy | ~50% (real-time, underwater) |
| Working | Real-time YUV luminance extraction, dynamic thresholding, rolling-shutter stripe decoding, dual-transmission Hamming correction |
| Not working | Reliable end-to-end byte recovery — see [`docs/POSTMORTEM.md`](docs/POSTMORTEM.md) |

**This prototype does not yet achieve reliable communication**, and we think that's worth saying plainly rather than burying it. We got the physical layer working (flash → rolling shutter → bitstream) but stalled on the error-correction layer. The full technical breakdown of *why* is in [`docs/POSTMORTEM.md`](docs/POSTMORTEM.md) — we think it's the most useful part of this repo for anyone attempting something similar.

## Roadmap

- [ ] Fix the symbol/coordinate alignment problem described in the postmortem before re-attempting Reed–Solomon
- [ ] Line coding (Manchester or RLL) to prevent long strobe bursts and sensor blinding
- [ ] Block interleaving to convert burst errors into isolated single-bit errors (which Hamming/RS can actually handle)
- [ ] Automated bit-error-rate (BER) test harness instead of manual accuracy checks

## Project structure

```
AquaComm/
├── transmitter-app/     # Android app: encodes data as flashlight pulses
├── receiver-app/        # Android app: rolling-shutter capture + decoding pipeline
├── docs/
│   └── POSTMORTEM.md    # Why Reed-Solomon integration failed, and what we'd do differently
├── LICENSE
└── README.md
```
> Note: folders were renamed from the original hackathon names (`FlashTest` → `transmitter-app`, `camtest2` → `receiver-app`) for clarity. See the setup instructions below if you're pulling a fresh clone.

## Getting started

**Requirements:** Android Studio (Hedgehog or newer), 2 physical Android phones (this cannot be tested on emulators — you need a real flashlight and a real camera), a body of water.

1. Clone the repo:
   ```bash
   git clone https://github.com/VishnuBalaji77/AquaComm.git
   ```
2. Open `transmitter-app/` in Android Studio and build/install it on phone A.
3. Open `receiver-app/` in Android Studio and build/install it on phone B.
4. On phone B, switch the camera to Pro/Manual mode (if supported) and lock: 60 FPS, 1/1000s shutter, ISO 1600.
5. Submerge both phones, aim the transmitter's flash at the receiver's camera, and start the transfer.

## Demo

[View Demo Videos & Assets](https://drive.google.com/drive/u/0/folders/1Yh2vl9YmZRXxGNUQ0kZ4qN4ZDQ6W_Yuj)

## Background reading

This project was inspired by the **U-Flash** line of research on smartphone-based optical camera communication. This was originally built during the Hackenza hackathon.

## Contributors

- [Vishnu Balaji](https://github.com/VishnuBalaji77)
- Siddharth
- Siddhratha Guha
- Malay

## License

MIT — see [`LICENSE`](LICENSE).
