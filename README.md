# Hackenza-26
A software only solution for underwater communication using smartphone flashlight for data transfer, a prototype aimed at reducing the bit error rate by encoding and decoding algorithms as suggested in the U-Flash research article. 

Based on the provided code in MainActivity.kt, you are building an Android application that performs real-time Visible Light Communication (VLC) decoding using the device's camera.
Specifically, you are using the camera sensor as a receiver to capture data encoded in light (likely from a flickering LED) and translating that light into text.
Here is a breakdown of what the code is doing:
1. High-Speed Camera Configuration
•
Manual Control: You are using Camera2Interop to bypass the camera's auto-exposure and auto-focus.
•
Fixed Exposure & ISO: You’ve set a very short exposure time (1ms) and high sensitivity (ISO 1600) to detect rapid changes in light intensity without motion blur or automatic brightness compensation.
•
60 FPS: You are requesting a constant 60 frames per second to maximize the "sampling rate" of the light signals.
2. Signal Acquisition (Image Analysis)
•
YUV Strip Processing: Instead of looking at the whole image, the processYUVStrip function analyzes the Luminance (Y) channel (brightness) along a horizontal line in the middle of the frame.
•
Sub-frame Sampling: You are dividing the horizontal width into segments (steps of 10 pixels), averaging the brightness, and converting it to a bit stream (1 if bright, 0 if dark). This effectively uses the camera's rolling shutter or spatial scanning to capture high-frequency data.
3. Bitstream Decoding & Synchronization
•
Self-Clocking Logic: The decodeBits function attempts to figure out the "clock" of the transmission. It looks at the first sequence of 1s and 0s to determine the baseLen (how many samples represent a single bit) and then normalizes the entire stream based on those lengths.
•
Transmission Splitting: Since signals might be sent multiple times, splitTransmissions looks for a specific "gap" pattern (111+ followed by several 0s) to separate different data packets.
4. Data Extraction and Redundancy
•
Payload Extraction: It looks for a start sequence (either the first 8 bits or after a 1111 marker) to find where the actual message begins.
•
Redundancy Handling:
◦
If two transmissions are detected, bitsToTextRedundant compares them byte-by-byte.
◦
It uses a Hamming-distance-like approach (predictBestChar) to find the closest valid character from a VALID_CHARS set. If a bit is flipped due to noise, the code picks the character that requires the fewest bit changes to match.
•
Termination: It looks for a specific bit pattern (00001111) to signal the end of a text string.
5. User Interaction & Feedback
•
Touch-to-Record: The process starts when the user touches the screen (ACTION_DOWN) and stops when they release (ACTION_UP).
•
Simultaneous Video: While analyzing the light bits, it also triggers a standard VideoCapture to save the recording to the gallery.
•
Real-time Results: The final decoded text, the raw bit string, and the total bit count are displayed in a resultsTextView.
Summary of the Flow:
1.
Light Source flickers binary data.
2.
Camera captures frames at high speed with fixed exposure.
3.
Analyzer converts brightness levels into a raw string of "1"s and "0"s.
4.
Decoder cleans up the timing and extracts the intended binary payload.
5.
Error Correction logic compares multiple captures to find the most likely intended characters.
6.
UI displays the resulting text message
