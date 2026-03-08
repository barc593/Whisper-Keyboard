package com.whisperkey.keyboard

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Records audio from the microphone and saves as WAV file.
 * WAV is well-supported by Whisper/Groq API.
 */
class AudioRecorder {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null

    companion object {
        private const val SAMPLE_RATE = 16000 // 16kHz — optimal for Whisper
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    fun startRecording(outputFile: File, scope: CoroutineScope) {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize * 2
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("AudioRecord failed to initialize")
        }

        isRecording = true
        audioRecord?.startRecording()

        recordingJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(bufferSize)
            val pcmStream = ByteArrayOutputStream()

            while (isRecording && isActive) {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (bytesRead > 0) {
                    pcmStream.write(buffer, 0, bytesRead)
                }
            }

            // Write WAV file
            val pcmData = pcmStream.toByteArray()
            writeWavFile(outputFile, pcmData)
        }
    }

    suspend fun stopRecording(): Boolean {
        isRecording = false
        recordingJob?.join()

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        return true
    }

    fun isCurrentlyRecording(): Boolean = isRecording

    private fun writeWavFile(file: File, pcmData: ByteArray) {
        val totalDataLen = pcmData.size + 36
        val channels = 1
        val byteRate = SAMPLE_RATE * channels * 2 // 16-bit = 2 bytes

        FileOutputStream(file).use { fos ->
            // RIFF header
            fos.write("RIFF".toByteArray())
            fos.write(intToByteArray(totalDataLen))
            fos.write("WAVE".toByteArray())

            // fmt sub-chunk
            fos.write("fmt ".toByteArray())
            fos.write(intToByteArray(16)) // Sub-chunk size
            fos.write(shortToByteArray(1)) // PCM format
            fos.write(shortToByteArray(channels.toShort()))
            fos.write(intToByteArray(SAMPLE_RATE))
            fos.write(intToByteArray(byteRate))
            fos.write(shortToByteArray((channels * 2).toShort())) // Block align
            fos.write(shortToByteArray(16)) // Bits per sample

            // data sub-chunk
            fos.write("data".toByteArray())
            fos.write(intToByteArray(pcmData.size))
            fos.write(pcmData)
        }
    }

    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }

    private fun shortToByteArray(value: Short): ByteArray {
        return byteArrayOf(
            (value.toInt() and 0xFF).toByte(),
            ((value.toInt() shr 8) and 0xFF).toByte()
        )
    }
}
