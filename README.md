# Hackenza-26: AquaComm (Underwater Smartphone OCC)

## The Problem
Underwater optical camera communication (OCC) using smartphones faces severe limitations. Ambient sunlight interference, water turbulence, and hardware constraints cause massive inter-symbol interference and frame decoding errors. 

## Our Solution & Core Logic
We built a software-only prototype for underwater data transfer using a smartphone flashlight and camera, heavily inspired by the U-Flash research article. To bypass low hardware strobe frequencies, we exploit the CMOS camera's **Rolling Shutter effect** to capture high-speed light pulses as spatial bright and dark stripes.


### Camera & Hardware Optimization
To maximize contrast and isolate the region of interest (RoI) underwater, we locked the receiver camera parameters to:
* **Frame Rate:** 60 FPS
* **Shutter Speed:** 1/1000s 
* **ISO:** 1600

### Signal Processing Pipeline
1. **YUV Strip Processing:** Instead of processing heavy RGB frames, we extract only the Luminance (Y) channel from the YUV format for blisteringly fast processing.
2. **Camera Caching & Subframe Sampling:** We utilize memory caching and subframe sampling to process the rolling shutter stripes in real-time without dropping frames.
3. **Dynamic Thresholding:** The app dynamically calculates local signal mean/variance to adjust the decision threshold for bright (1) and dark (0) stripes, adapting to changing underwater ambient light.

### Error Correction (Current Implementation)
* **Dual Transmission & Hamming Distance:** To combat bit errors without complex encoding overhead, the transmitter sends the bit sequence twice. The receiver compares the received sequences and resolves errors using the least Hamming distance.

## Current Project Status
* **Accuracy:** The prototype currently successfully reads transmitted bytes with **~50% accuracy** in real-time.
* **Working Features:** Real-time YUV luminance extraction, dynamic thresholding, and rolling shutter stripe decoding.

## Future Roadmap (To-Do)
To reach 100% accuracy, we are architecting the following advanced encoding layers:
* **Line Coding:** Implementing Manchester or Run-Length Limited (RLL) encoding to prevent long strobe bursts and camera blinding.
* **Forward Error Correction:** Integrating the `ZXing` Reed-Solomon encoder to provide byte-level armor against massive burst errors caused by water splashes.
* **Interleaving:** Adding a block interleaver matrix to scatter burst errors into single-bit errors.

## How to Set Up and Run
1. Clone this repository.
2. Open the project in Android Studio.
3. Build and install the APK on two Android devices.
4. Set the receiving device camera to Pro/Manual mode (if supported) to lock 60fps and 1/1000s shutter speed. 
5. Align the transmitter's flashlight with the receiver's camera and initiate transfer.

## Demo Video
[**Link to Google Drive Demo Video Here**] 
*(Note: Ensure this is set to 'Anyone with the link can view')*
