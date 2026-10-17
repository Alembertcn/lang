package com.moji.offline.data.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.moji.offline.AppContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual fun createAudioRecorder(): AudioRecorder = AndroidAudioRecorder()

private class AndroidAudioRecorder : AudioRecorder {
    private var recorder: MediaRecorder? = null
    private var file: File? = null

    override suspend fun start(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val context = AppContext.context
            val output = File(context.cacheDir, "shadow_${System.currentTimeMillis()}.m4a")
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(96_000)
            recorder.setAudioSamplingRate(44_100)
            recorder.setOutputFile(output.absolutePath)
            recorder.prepare()
            recorder.start()
            this@AndroidAudioRecorder.recorder = recorder
            file = output
            output.absolutePath
        }.getOrNull()
    }

    override suspend fun stop(): String? = withContext(Dispatchers.IO) {
        val recorder = recorder ?: return@withContext null
        this@AndroidAudioRecorder.recorder = null
        runCatching { recorder.stop() }
        runCatching { recorder.release() }
        file?.takeIf { it.exists() && it.length() > 0 }?.absolutePath
    }
}

@Composable
actual fun rememberMicPermission(onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) onGranted() }
    return {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) onGranted() else launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
}
