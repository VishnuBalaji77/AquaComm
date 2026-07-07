package com.example.flashtest

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var durationNanos: Long = 100_000_000 // Default 100ms in nanos

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etInput = findViewById<EditText>(R.id.etInput)
        val btnSend = findViewById<Button>(R.id.btnSend)
        val tvBinary = findViewById<TextView>(R.id.tvBinary)
        val etSpeed = findViewById<EditText>(R.id.etSpeed)
        val sbSpeed = findViewById<SeekBar>(R.id.sbSpeed)
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // Slider 0 to 999 representing 0.1ms to 100.0ms (in steps of 0.1ms)
        sbSpeed.max = 999
        sbSpeed.progress = 999 // Default to 100ms
        etSpeed.setText("100.0")

        sbSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val msValue = (progress.toDouble() / 10.0) + 0.1
                    durationNanos = (msValue * 1_000_000).toLong()
                    etSpeed.setText(String.format(Locale.US, "%.1f", msValue))
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        etSpeed.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString().toDoubleOrNull()
                if (input != null) {
                    val clamped = input.coerceAtLeast(0.1)
                    durationNanos = (clamped * 1_000_000).toLong()
                    
                    if (clamped <= 100.0) {
                        sbSpeed.progress = Math.round((clamped - 0.1) * 10).toInt()
                    }
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        btnSend.setOnClickListener {
            val text = etInput.text.toString()
            if (text.isEmpty()) {
                Toast.makeText(this, "Please enter some text", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 1. Convert text to binary string directly
            val binaryBits = textToBinary(text)

            // 2. Assemble full sequence
            val startSequence = "10101111"
            val endSequence = "1111"
            val binaryData = startSequence + binaryBits + endSequence
            
            tvBinary.text = "Sending twice: $binaryData\nTotal Bits (per send): ${binaryData.length}"

            lifecycleScope.launch {
                try {
                    val cameraId = cameraManager.cameraIdList[0]
                    
                    // First transmission
                    sendBinaryData(cameraManager, cameraId, binaryData)
                    
                    // 50ms delay between transmissions
                    delay(50)
                    
                    // Second transmission
                    sendBinaryData(cameraManager, cameraId, binaryData)

                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun textToBinary(text: String): String {
        return text.toByteArray().joinToString("") { byte ->
            String.format("%8s", Integer.toBinaryString(byte.toInt() and 0xFF)).replace(' ', '0')
        }
    }

    private suspend fun sendBinaryData(manager: CameraManager, id: String, bits: String) {
        for (bit in bits) {
            manager.setTorchMode(id, bit == '1')
            
            val totalNanos = durationNanos
            val ms = totalNanos / 1_000_000
            val nanos = (totalNanos % 1_000_000).toInt()
            
            if (ms > 0) {
                delay(ms)
            }
            if (nanos > 0) {
               busyWaitNanos(nanos.toLong())
            }
        }
        manager.setTorchMode(id, false)
    }

    private fun busyWaitNanos(nanos: Long) {
        val startTime = System.nanoTime()
        while (System.nanoTime() - startTime < nanos) {
            // Busy wait
        }
    }
}
