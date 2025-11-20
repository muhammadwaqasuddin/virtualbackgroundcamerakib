package com.example.virtualbackgroundcamerakib

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.net.Uri
import android.provider.Settings
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import kotlin.math.max
import kotlin.math.min
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.mutableIntStateOf
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.get
import kotlin.math.sqrt
import kotlin.text.get


private const val TAG = "SegRecord"
private const val VIDEO_WIDTH = 480
private const val VIDEO_HEIGHT = 640
private const val FRAME_RATE = 30
private const val BITRATE = 2_500_000

@ExperimentalGetImage
@Composable
fun VirtualBackgroundApp(activity: CameraActivity, cameraViewModel: CameraViewModel = hiltViewModel()) {
    val context = LocalContext.current

    val cameraPermission = Manifest.permission.CAMERA
    val micPermission = Manifest.permission.RECORD_AUDIO

    var permissionsGranted by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showRationaleDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val camGranted = permissions[cameraPermission] == true
        val micGranted = permissions[micPermission] == true
        when {
            camGranted && micGranted -> permissionsGranted = true
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, cameraPermission) ||
                    !ActivityCompat.shouldShowRequestPermissionRationale(activity, micPermission) -> {
                showSettingsDialog = true
            }
            else -> {
                showRationaleDialog = true
            }
        }
    }

    LaunchedEffect(Unit) {
        val camGranted = ContextCompat.checkSelfPermission(context, cameraPermission) == PackageManager.PERMISSION_GRANTED
        val micGranted = ContextCompat.checkSelfPermission(context, micPermission) == PackageManager.PERMISSION_GRANTED

        if (camGranted && micGranted) {
            permissionsGranted = true
        } else {
            permissionLauncher.launch(arrayOf(cameraPermission, micPermission))
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            showSettingsDialog -> {
               PermissionSettingsDialog(
                    onDismiss = {
                        showSettingsDialog = false
                        activity.finishWithCancel("")
                    },
                    context = context
                )
            }

            showRationaleDialog -> {
                PermissionRationaleDialog(
                    onRetry = {
                        showRationaleDialog = false
                        permissionLauncher.launch(arrayOf(cameraPermission, micPermission))
                    },
                    onDismiss = { showRationaleDialog = false }
                )
            }

            permissionsGranted -> {
                EnhancedCameraSegAndRecordView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(480.dp),
                    onSuccess = { saved ->
                        (context as? CameraActivity)?.setContent {
                            VideoPlaybackScreen(
                                videoUri = Uri.fromFile(saved),
                                onRetake = {
                                    activity.setContent {
                                        VirtualBackgroundApp(activity, cameraViewModel)
                                    }
                                },
                                onConfirm = {
                                    (activity.finishWithResult(Uri.fromFile(saved)))
                                }
                            )
                        }
                    },
                    onCameraXError = { it ->
                        activity.finishWithCancel(it)
                    }, cameraViewModel
                )
            }

            else -> {
                Text("Requesting permissions...", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}


@Composable
fun PermissionSettingsDialog(onDismiss: () -> Unit, context: Context) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permission Required") },
        text = { Text("Camera and Microphone permissions are permanently denied. Please enable them in Settings.") },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }) {
                Text("Go to Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun PermissionRationaleDialog(onRetry: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permission Needed") },
        text = { Text("Camera and Microphone permissions are required to use this feature. Please allow them.") },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text("Try Again")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


@SuppressLint("MissingPermission")
@ExperimentalGetImage
@Composable
fun EnhancedCameraSegAndRecordView(
    modifier: Modifier = Modifier,
    onSuccess: (File) -> Unit = {},
    onCameraXError: (String) -> Unit = {},
    cameraViewModel: CameraViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableStateOf(cameraViewModel.lensFacing.value) }
    var elapsedTime by remember { mutableStateOf(0L) }

    var selectedBackgroundIndex by remember { mutableIntStateOf(cameraViewModel.selectedBackgroundIndex.value) }

    val segmentationProcessor = remember { EnhancedSegmentationProcessor() }
    val recorderRef = remember { EncoderMuxerHolder() }

    val thumbSizeDp = 50
    val thumbSizePx = with(LocalDensity.current) { (thumbSizeDp.dp).roundToPx() }

    val gradientThumbCache = remember {
        mutableStateMapOf<Int, Bitmap>().apply {
            val presets = cameraViewModel.getThumbnailBackground(thumbSizeDp, thumbSizePx, context)
            presets.forEachIndexed { index, bitmap ->
                this[index] = bitmap
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            gradientThumbCache.values.forEach { bmp ->
                if (!bmp.isRecycled) bmp.recycle()
            }
            gradientThumbCache.clear()
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            elapsedTime = 0
            while (isRecording) {
                kotlinx.coroutines.delay(1000)
                elapsedTime++
            }
        }
    }

    LaunchedEffect(selectedBackgroundIndex) {
        cameraViewModel.selectedBackgroundIndex.value = selectedBackgroundIndex
    }

    LaunchedEffect(lensFacing) {
        cameraViewModel.lensFacing.value = lensFacing
        // Clear preview immediately when switching
        previewBitmap?.recycle()
        previewBitmap = null
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val segmenter = Segmentation.getClient(
                SelfieSegmenterOptions.Builder()
                    .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
                    .enableRawSizeMask()
                    .build()
            )

            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(VIDEO_WIDTH, VIDEO_HEIGHT))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val currentLensFacing = lensFacing

            analysis.setAnalyzer(Dispatchers.Default.asExecutor()) { imageProxy ->
                if (currentLensFacing != cameraViewModel.lensFacing.value) {
                    imageProxy.close()
                    return@setAnalyzer
                }
                coroutineScope.launch(Dispatchers.Default) {
                    try {
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val input = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )

                            segmenter.process(input)
                                .addOnSuccessListener { result ->
                                    try {
                                        if (currentLensFacing != cameraViewModel.lensFacing.value) {
                                            return@addOnSuccessListener
                                        }
                                        val frameBmp = imageProxyToBitmap(
                                            imageProxy,
                                            lensFacing == CameraSelector.LENS_FACING_FRONT
                                        )
                                        if (frameBmp != null) {
                                            val processed = segmentationProcessor.processFrame(
                                                frameBmp,
                                                result,
                                                context,
                                                selectedBackgroundIndex,
                                                lensFacing == CameraSelector.LENS_FACING_FRONT,
                                                cameraViewModel
                                            )

                                            val resized = processed.scale(VIDEO_WIDTH, VIDEO_HEIGHT)
                                            previewBitmap = resized

                                            if (recorderRef.isRecording.get()) {
                                                recorderRef.queueFrame(resized)
                                            }
                                            processed.recycle()
                                        }
                                    } catch (e: Exception) {
                                        Timber.e("Segmentation processing error $e")
                                    }
                                }
                                .addOnFailureListener {
                                    Timber.e("Segmentation failed $it")
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        } else {
                            imageProxy.close()
                        }
                    } catch (e: Exception) {
                        Timber.e("Analysis error $e")
                        imageProxy.close()
                    }
                }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    context as ComponentActivity,
                    CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                    analysis
                )
            } catch (e: Exception) {
                Timber.e("Camera binding failed $e")
                Toast.makeText(context, "Camera bind failed: ${e.message}", Toast.LENGTH_LONG)
                    .show()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = Color.Black,
                )
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isRecording) {
                Text(
                    text = formatTime(elapsedTime),
                    color = Color.Red,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            } else {
                IconButton(
                    onClick = {
                        onCameraXError("close")
                        isRecording = false
                        elapsedTime = 0
                    },
                    modifier = Modifier
                        .padding(end = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Camera",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f).background(Color.Black)
        ) {
            previewBitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "segmented preview",
                    modifier = Modifier.fillMaxSize(),//.aspectRatio(VIDEO_WIDTH.toFloat() / VIDEO_HEIGHT.toFloat()),  // ✅ Maintain aspect
                    contentScale = ContentScale.Crop
                )
            }
            Column(
                modifier = Modifier.align(Alignment.BottomCenter)
            ){
                if (!isRecording) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.2f))
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        itemsIndexed(gradientThumbCache.toList()) { index, (_, thumbBitmap) ->
                            Box(
                                modifier = Modifier
                                    .size(thumbSizeDp.dp)
                                    .clip(CircleShape)
                                    .clickable { selectedBackgroundIndex = index }
                                    .border(
                                        width = if (selectedBackgroundIndex == index) 3.dp else 1.dp,
                                        color = if (selectedBackgroundIndex == index) Color.White else Color.Gray,
                                        shape = CircleShape
                                    )
                            ) {
                                Image(
                                    bitmap = thumbBitmap.asImageBitmap(),
                                    contentDescription = "background preview $index",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                if (selectedBackgroundIndex == index) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth().padding(vertical =8.dp),
                    verticalAlignment = Alignment.CenterVertically
                )
                {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            modifier= Modifier.size(70.dp),
                            onClick = {
                                if (isRecording) {
                                    try {
                                        val saved = recorderRef.stopEncoding()
                                        if (saved != null) {
                                            onSuccess(saved)
                                        } else {
                                            onCameraXError("")
                                        }
                                    } catch (e: Exception) {
                                        Timber.e("Stop recording error $e")
                                        onCameraXError("CameraX not Supported")
                                        isRecording = false
                                    }
                                } else {
                                    val outFile = createOutputFile(context)
                                    try {
                                        recorderRef.startEncoding(
                                            outFile,
                                            VIDEO_WIDTH,
                                            VIDEO_HEIGHT,
                                            FRAME_RATE,
                                            BITRATE
                                        )
                                        isRecording = true
                                    } catch (e: Exception) {
                                        Timber.e("Encoder start failed $e")
                                        onCameraXError("CameraX not Supported")
                                    }
                                }
                            }
                        ) {
                            Image(
                                painter = painterResource(id = if (isRecording) R.drawable.stop_video_icon else R.drawable.start_video_icon),
                                contentDescription = "Record",
                            )
                        }

                        if (!isRecording) {
                            IconButton(
                                onClick = {
                                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT)
                                        CameraSelector.LENS_FACING_BACK
                                    else CameraSelector.LENS_FACING_FRONT
                                },
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 16.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.switch_camera_icon),
                                    contentDescription = "Switch Camera",
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(60.dp)
                                )
                            }
                        }
                    }
                }

            }

        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.1f)
                .background(Color.Black)
        ) {

        }
    }
}

private fun formatTime(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}

class EnhancedSegmentationProcessor {
    private var previousMask: FloatArray? = null
    private var maskBuffer: Array<FloatArray?> = arrayOfNulls(3)
    private var bufferIndex = 0
    private val temporalWeight = 0.7f
    private val edgeThreshold = 0.3f

    fun processFrame(
        frameBitmap: Bitmap,
        segmentationResult: SegmentationMask,
        context: Context,
        gradientIndex: Int = 0,
        isFrontCamera: Boolean,
        cameraViewModel: CameraViewModel,
    ): Bitmap {
        val maskBuffer = segmentationResult.buffer
        val maskWidth = segmentationResult.width
        val maskHeight = segmentationResult.height

        val currentMask = FloatArray(maskWidth * maskHeight)
        maskBuffer.rewind()
        for (i in currentMask.indices) {
            currentMask[i] = maskBuffer.float
        }

        val smoothedMask = applyTemporalSmoothing(currentMask, maskWidth, maskHeight)
        val refinedMask = applyEdgeRefinement(smoothedMask, maskWidth, maskHeight)

        return cameraViewModel.processSegmentationEnhanced(
            frameBitmap,
            refinedMask,
            maskWidth,
            maskHeight,
            context,
            gradientIndex,
            isFrontCamera
        )
    }

    private fun applyTemporalSmoothing(
        currentMask: FloatArray,
        width: Int,
        height: Int,
    ): FloatArray {
        val smoothedMask = currentMask.copyOf()

        maskBuffer[bufferIndex] = currentMask.copyOf()
        bufferIndex = (bufferIndex + 1) % maskBuffer.size

        previousMask?.let { prevMask ->
            for (i in smoothedMask.indices) {
                smoothedMask[i] = temporalWeight * smoothedMask[i] +
                        (1 - temporalWeight) * prevMask[i]

                var bufferSum = 0f
                var bufferCount = 0
                for (bufferedMask in maskBuffer) {
                    if (bufferedMask != null) {
                        bufferSum += bufferedMask[i]
                        bufferCount++
                    }
                }
                if (bufferCount > 0) {
                    val bufferAvg = bufferSum / bufferCount
                    smoothedMask[i] = 0.8f * smoothedMask[i] + 0.2f * bufferAvg
                }
            }
        }

        previousMask = smoothedMask.copyOf()
        return smoothedMask
    }

    private fun applyEdgeRefinement(
        mask: FloatArray,
        width: Int,
        height: Int,
    ): FloatArray {
        val refinedMask = mask.copyOf()

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                val currentValue = mask[idx]

                val gx = mask[idx + 1] - mask[idx - 1]
                val gy = mask[idx + width] - mask[idx - width]
                val gradientMag = sqrt(gx * gx + gy * gy)

                if (gradientMag > edgeThreshold) {
                    refinedMask[idx] = if (currentValue > 0.5f) {
                        min(1.0f, currentValue + 0.1f * gradientMag)
                    } else {
                        max(0.0f, currentValue - 0.1f * gradientMag)
                    }
                } else {
                    var sum = 0f
                    var count = 0
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            val nx = x + dx
                            val ny = y + dy
                            if (nx < width && ny < height) {
                                sum += mask[ny * width + nx]
                                count++
                            }
                        }
                    }
                    refinedMask[idx] = 0.7f * currentValue + 0.3f * (sum / count)
                }
            }
        }
        return refinedMask
    }
}

class EncoderMuxerHolder {
    @Volatile
    private var videoCodec: MediaCodec? = null
    @Volatile
    private var audioCodec: MediaCodec? = null
    @Volatile
    private var mediaMuxer: MediaMuxer? = null
    @Volatile
    private var videoTrackIndex = -1
    @Volatile
    private var audioTrackIndex = -1
    @Volatile
    private var videoTrackReady = false
    @Volatile
    private var audioTrackReady = false
    @Volatile
    private var started = false
    val isRecording = AtomicBoolean(false)
    private var startNanoTime = 0L
    private var width = 0
    private var height = 0
    private var frameIntervalUs = 0L
    private var videoPts = 0L
    private var audioPts = 0L
    private var videoFrameCount = 0L
    private val videoFrameRate = 30
    private var lastPtsUs = 0L

    private var audioRecord: AudioRecord? = null
    private var videoThread: Thread? = null
    private var audioThread: Thread? = null
    private val audioSampleRate = 44100
    private val audioChannelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    // Fixed queue with proper management
    private val frameQueue = LinkedBlockingQueue<Bitmap>(100)
    private var outputFile: File? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startEncoding(file: File, w: Int, h: Int, fps: Int, bitrate: Int) {
        stopEncoding()
        startNanoTime = System.nanoTime()
        outputFile = file

        frameIntervalUs = 1_000_000L / fps
        videoPts = 0L
        audioPts = 0L

        width = w - (w % 2)
        height = h - (h % 2)

        val videoFormat =
            MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
                )
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(
                    MediaFormat.KEY_COMPLEXITY,
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
                )
                setInteger(
                    MediaFormat.KEY_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
                )
                setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31)
            }

        val audioFormat =
            MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, audioSampleRate, 1)
                .apply {
                    setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
                    setInteger(
                        MediaFormat.KEY_AAC_PROFILE,
                        MediaCodecInfo.CodecProfileLevel.AACObjectLC
                    )
                    setInteger(MediaFormat.KEY_BIT_RATE, 74000)
                    setInteger(MediaFormat.KEY_SAMPLE_RATE, audioSampleRate)
                    setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)
                }

        try {
            videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            mediaMuxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val bufferSize = AudioRecord.getMinBufferSize(
                audioSampleRate,
                audioChannelConfig,
                this.audioFormat
            ) * 4

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                audioSampleRate,
                audioChannelConfig,
                this.audioFormat,
                bufferSize
            ).apply {
                if (state != AudioRecord.STATE_INITIALIZED) {
                    throw RuntimeException("AudioRecord failed to initialize")
                }
                startRecording()
            }

            videoTrackIndex = -1
            audioTrackIndex = -1
            videoTrackReady = false
            audioTrackReady = false
            started = false
            isRecording.set(true)

            startEncodingThreads()
            Timber.d("Encoding started successfully")
        } catch (e: Exception) {
            Timber.e("Failed to start encoding $e")
            cleanup()
            throw e
        }
    }

    fun queueFrame(bitmap: Bitmap) {
        if (!isRecording.get()) return
        val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        if (!frameQueue.offer(copy)) {
            frameQueue.poll()?.recycle()
            frameQueue.offer(copy)
        }
    }

    fun stopEncoding(): File? {
        if (!isRecording.get()) return null
        Timber.d("Stopping encoding...")
        isRecording.set(false)

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Timber.e("Error stopping AudioRecord $e")
        }

        videoThread?.join(3000)
        audioThread?.join(3000)

        val result = outputFile
        cleanup()
        Timber.d("Encoding stopped")
        return result
    }

    private fun startEncodingThreads() {
        videoThread = Thread({
            val bufferInfo = MediaCodec.BufferInfo()
            var endOfStream = false

            try {
                while (isRecording.get() || !frameQueue.isEmpty()) {
                    val bitmap = frameQueue.poll(10, TimeUnit.MILLISECONDS)
                    if (bitmap != null) {
                        encodeVideoFrame(bitmap)
                    }

                    drainEncoder(videoCodec, bufferInfo, false) { format, buffer, info ->
                        if (videoTrackIndex < 0) {
                            videoTrackIndex = mediaMuxer?.addTrack(format) ?: -1
                            videoTrackReady = true
                            checkStartMuxer()
                        }
                        if (started && buffer != null) {
                            mediaMuxer?.writeSampleData(videoTrackIndex, buffer, info)
                        }
                    }
                }

                signalEndOfStream(videoCodec)
                while (!endOfStream) {
                    endOfStream =
                        drainEncoder(videoCodec, bufferInfo, true) { format, buffer, info ->
                            if (started && buffer != null && info.size > 0) {
                                mediaMuxer?.writeSampleData(videoTrackIndex, buffer, info)
                            }
                        }
                }
            } catch (e: Exception) {
                Timber.e("Video thread error $e")
            }
        }, "VideoThread").apply { start() }

        audioThread = Thread({
            val bufferInfo = MediaCodec.BufferInfo()
            val audioBuffer = ByteArray(1024)
            var endOfStream = false

            try {
                while (isRecording.get()) {
                    val bytesRead = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                    if (bytesRead > 0) {
                        encodeAudioFrame(audioBuffer, bytesRead)
                    }

                    drainEncoder(audioCodec, bufferInfo, false) { format, buffer, info ->
                        if (audioTrackIndex < 0) {
                            audioTrackIndex = mediaMuxer?.addTrack(format) ?: -1
                            audioTrackReady = true
                            checkStartMuxer()
                        }
                        if (started && buffer != null) {
                            mediaMuxer?.writeSampleData(audioTrackIndex, buffer, info)
                        }
                    }
                }

                signalEndOfStream(audioCodec)
                while (!endOfStream) {
                    endOfStream =
                        drainEncoder(audioCodec, bufferInfo, true) { format, buffer, info ->
                            if (started && buffer != null && info.size > 0) {
                                mediaMuxer?.writeSampleData(audioTrackIndex, buffer, info)
                            }
                        }
                }
            } catch (e: Exception) {
                Timber.e("Audio thread error $e")
            }
        }, "AudioThread").apply { start() }
    }

    private fun encodeVideoFrame(bitmap: Bitmap) {
        val codec = videoCodec ?: return
        try {
            val inputIndex = codec.dequeueInputBuffer(2000)
            if (inputIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputIndex)
                if (inputBuffer != null) {

                    val yuv = convertBitmapToYUV420(bitmap)
                    inputBuffer.clear()
                    inputBuffer.put(yuv)

                    val ptsUs = (1_000_000L * videoFrameCount / videoFrameRate).toLong()
                    videoFrameCount++

                    codec.queueInputBuffer(inputIndex, 0, yuv.size, ptsUs, 0)
                }
            }
        } catch (e: Exception) {
            Timber.e("Error encoding video frame $e")
        }
    }


    private fun encodeAudioFrame(buffer: ByteArray, size: Int) {
        val codec = audioCodec ?: return

        try {
            val inputIndex = codec.dequeueInputBuffer(2000)
            if (inputIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputIndex)
                if (inputBuffer != null) {
                    inputBuffer.clear()
                    inputBuffer.put(buffer, 0, size)

                    val samples = size / 2 // 16-bit PCM = 2 bytes per sample
                    val ptsUs = (1_000_000L * audioPts / audioSampleRate)

                    audioPts += samples

                    codec.queueInputBuffer(
                        inputIndex,
                        0,
                        size,
                        ptsUs,
                        0
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e("Error encoding audio frame $e")
        }
    }


    private fun drainEncoder(
        codec: MediaCodec?,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        onSampleAvailable: (MediaFormat, ByteBuffer?, MediaCodec.BufferInfo) -> Unit
    ): Boolean {
        if (codec == null) return false

        if (endOfStream) {
            codec.signalEndOfInputStream()
        }

        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 2000)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break
                }

                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newFormat = codec.outputFormat
                    onSampleAvailable(newFormat, null, bufferInfo)
                }

                outputIndex >= 0 -> {
                    val encodedBuffer = codec.getOutputBuffer(outputIndex)
                    if (encodedBuffer != null && bufferInfo.size > 0) {
                        encodedBuffer.position(bufferInfo.offset)
                        encodedBuffer.limit(bufferInfo.offset + bufferInfo.size)

                        if (bufferInfo.presentationTimeUs < lastPtsUs) {
                            bufferInfo.presentationTimeUs = lastPtsUs + 1
                        }
                        lastPtsUs = bufferInfo.presentationTimeUs

                        onSampleAvailable(codec.outputFormat, encodedBuffer, bufferInfo)
                    }

                    codec.releaseOutputBuffer(outputIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        return true
                    }
                }
            }
        }

        return false
    }


    private fun signalEndOfStream(codec: MediaCodec?) {
        codec ?: return
        try {
            val inputIndex = codec.dequeueInputBuffer(1000)
            if (inputIndex >= 0) {
                codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
        } catch (e: Exception) {
            Timber.e("Error signaling EOS $e")
        }
    }

    @Synchronized
    private fun checkStartMuxer() {
        if (videoTrackReady && audioTrackReady && !started) {
            try {
                mediaMuxer?.start()
                started = true
                Timber.d("Muxer started")
            } catch (e: Exception) {
                Timber.e("Error starting muxer $e")
            }
        }
    }

    private fun cleanup() {
        while (frameQueue.isNotEmpty()) {
            frameQueue.poll()?.recycle()
        }

        try {
            videoCodec?.stop(); videoCodec?.release()
        } catch (e: Exception) {
            Timber.e("Video codec cleanup error $e")
        }
        try {
            audioCodec?.stop(); audioCodec?.release()
        } catch (e: Exception) {
            Timber.e("Audio codec cleanup error $e")
        }
        try {
            audioRecord?.stop(); audioRecord?.release()
        } catch (e: Exception) {
            Timber.e("AudioRecord cleanup error $e")
        }
        try {
            if (started) mediaMuxer?.stop(); mediaMuxer?.release()
        } catch (e: Exception) {
            Timber.e("Muxer cleanup error $e")
        }

        videoCodec = null
        audioCodec = null
        audioRecord = null
        mediaMuxer = null
        videoThread = null
        audioThread = null
        started = false
        videoTrackReady = false
        audioTrackReady = false
    }
    private fun convertBitmapToYUV420(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)

        val ySize = width * height
        val uSize = ySize / 4
        val vSize = ySize / 4
        val yuv = ByteArray(ySize + uSize + vSize)

        var yIndex = 0
        var uIndex = ySize
        var vIndex = ySize + uSize

        for (j in 0 until height) {
            for (i in 0 until width) {
                val pixel = argb[j * width + i]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                val y = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                yuv[yIndex++] = y.coerceIn(0, 255).toByte()
            }
        }

        for (j in 0 until height step 2) {
            for (i in 0 until width step 2) {
                // Sample 2x2 block for chroma
                var rSum = 0
                var gSum = 0
                var bSum = 0
                var count = 0

                for (dy in 0..1) {
                    for (dx in 0..1) {
                        val x = i + dx
                        val y = j + dy
                        if (x < width && y < height) {
                            val pixel = argb[y * width + x]
                            rSum += (pixel shr 16) and 0xFF
                            gSum += (pixel shr 8) and 0xFF
                            bSum += pixel and 0xFF
                            count++
                        }
                    }
                }

                val avgR = rSum / count
                val avgG = gSum / count
                val avgB = bSum / count

                val u = (-0.169 * avgR - 0.331 * avgG + 0.5 * avgB + 128).toInt()
                val v = (0.5 * avgR - 0.419 * avgG - 0.081 * avgB + 128).toInt()

                yuv[uIndex++] = u.coerceIn(0, 255).toByte()
                yuv[vIndex++] = v.coerceIn(0, 255).toByte()
            }
        }

        return yuv
    }
}

private fun createOutputFile(context: Context): File {
    val outputDir = context.externalCacheDir
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    return File(outputDir, "segmented_video_$timeStamp.mp4")
}

@ExperimentalGetImage
private fun imageProxyToBitmap(imageProxy: ImageProxy, isFrontCamera: Boolean): Bitmap? {
    return try {
        val image = imageProxy.image ?: return null
        val planes = image.planes
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 90, out)
        val imageBytes = out.toByteArray()
        val bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        val matrix = Matrix()
        when (imageProxy.imageInfo.rotationDegrees) {
            90 -> matrix.postRotate(90f)
            180 -> matrix.postRotate(180f)
            270 -> matrix.postRotate(270f)
        }
        if (isFrontCamera) {
            matrix.postScale(-1f, 1f)
        }
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        if (rotated != bmp) bmp.recycle()
        rotated
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}