package app.mindcore.data

import android.content.Context
import android.media.MediaRecorder
import java.io.File

/** Records a small voice note: AAC in .m4a, mono 16 kHz at 32 kbps (≈ 240 KB per minute). */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    var file: File? = null
        private set
    private var startedAt = 0L

    val isRecording get() = recorder != null

    fun start() {
        stop()
        val f = File(context.cacheDir, "voice-${System.currentTimeMillis()}.m4a")
        recorder = MediaRecorder(context).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioChannels(1)
            setAudioSamplingRate(16_000)
            setAudioEncodingBitRate(32_000)
            setMaxDuration(5 * 60 * 1000) // notes, not podcasts
            setOutputFile(f.absolutePath)
            prepare()
            start()
        }
        file = f
        startedAt = System.currentTimeMillis()
    }

    /** Stops and returns the recording length in seconds (0 if it was too short to keep). */
    fun stop(): Int {
        val r = recorder ?: return 0
        recorder = null
        val seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
        runCatching { r.stop() }.onFailure { file?.delete(); file = null } // stop() throws if nothing was recorded
        r.release()
        if (seconds < 1) { file?.delete(); file = null; return 0 }
        return seconds
    }

    /** 0..1 loudness, for the little level meter while recording. */
    fun level(): Float = (recorder?.maxAmplitude ?: 0) / 32767f

    fun discard() {
        stop()
        file?.delete()
        file = null
    }
}
