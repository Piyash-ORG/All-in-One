package com.nexaplay.tv

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.view.KeyEvent as AndroidKeyEvent
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.CaptionStyleCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.math.abs

// 🔥 ডাইনামিক কনফিগ মডেল
data class StreamConfig(
    val domain: String = "default",
    val userAgent: String = "ExoPlayer",
    val headers: Map<String, String> = emptyMap(),
    val isDrm: Boolean = false,
    val drmType: String = "",
    val drmLicenseUrl: String = "",
    val drmKey: String = ""
)

fun getConfigForUrl(videoUrl: String, rulesMap: Map<String, StreamConfig>): StreamConfig {
    val host = Uri.parse(videoUrl).host ?: return StreamConfig()

    rulesMap[host]?.let { return it }

    val parts = host.split(".")
    if (parts.size >= 2) {
        val baseDomain = "${parts[parts.size - 2]}.${parts[parts.size - 1]}"
        rulesMap[baseDomain]?.let { return it }
        
        if (parts.size >= 3) {
            val subBaseDomain = "${parts[parts.size - 3]}.${parts[parts.size - 2]}.${parts[parts.size - 1]}"
            rulesMap[subBaseDomain]?.let { return it }
        }
    }
    return rulesMap["default"] ?: StreamConfig()
}

data class VideoQuality(
    val trackGroup: TrackGroup,
    val trackIndex: Int,
    val height: Int,
    val bitrate: Int
)

@androidx.annotation.OptIn(UnstableApi::class)
class VolumeBoostProcessor : BaseAudioProcessor() {
    @Volatile
    var volumeMultiplier: Float = 1.0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (volumeMultiplier == 1.0f) {
            replaceOutputBuffer(remaining).put(inputBuffer).flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)
        while (inputBuffer.hasRemaining()) {
            val sample = inputBuffer.short
            var boosted = (sample * volumeMultiplier).toInt()
            
            if (boosted > Short.MAX_VALUE) boosted = Short.MAX_VALUE.toInt()
            else if (boosted < Short.MIN_VALUE) boosted = Short.MIN_VALUE.toInt()
            
            buffer.putShort(boosted.toShort())
        }
        buffer.flip()
    }
}

@Composable
fun TvFocusableIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    tint: Color
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (isFocused) Color.White.copy(alpha = 0.3f) else Color.Transparent)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
                    if (event.type == KeyEventType.KeyUp) {
                        onClick()
                        true
                    } else false
                } else false
            }
            .clickable { onClick() }
            .focusable(), 
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(28.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ExoPlayerView(
    playlist: List<Channel>, 
    initialIndex: Int, 
    isTv: Boolean,
    streamRules: Map<String, StreamConfig> = emptyMap(), 
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()
    val prefs = context.getSharedPreferences("NexaPlayPrefs", Context.MODE_PRIVATE)

    var isChannelListVisible by remember { mutableStateOf(false) }
    var showDialer by remember { mutableStateOf(false) }
    var isOkLongPressed by remember { mutableStateOf(false) }
    var currentIndex by remember { mutableStateOf(initialIndex) }
    var currentServerIndex by remember(currentIndex) { mutableStateOf(0) } 
    
    var isControllerVisible by remember { mutableStateOf(false) } 
    var hideTimeout by remember { mutableStateOf(8000L) }
    
    var isPlaying by remember { mutableStateOf(true) }
    var playbackState by remember { mutableStateOf(Player.STATE_IDLE) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    
    var isLandscape by remember { mutableStateOf(true) }
    
    val resizeModes = listOf(
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT,
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
    )
    var currentResizeMode by remember { 
        mutableStateOf(prefs.getInt("saved_resize_mode", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT)) 
    }
    var savedQualityHeight by remember { mutableStateOf(prefs.getInt("saved_quality_height", 0)) }
    
    // 🔥 সাবটাইটেলের জন্য ডাইনামিক স্টেট (রিয়েল-টাইম কন্ট্রোলের জন্য)
    var isSubtitleEnabled by remember { mutableStateOf(prefs.getBoolean("saved_subtitle_enabled", true)) }
    var subtitleSize by remember { mutableStateOf(prefs.getFloat("saved_sub_size", 0.0533f)) }
        // 🔥 পজিশনের জন্য নতুন স্টেট (translationY)
    var subtitleOffset by remember { mutableStateOf(prefs.getFloat("saved_sub_offset_y", 0f)) } 
    var showSubtitleDialog by remember { mutableStateOf(false) }
    
    val rootFocusRequester = remember { FocusRequester() }
    val server1FocusRequester = remember { FocusRequester() }
    val qualityFocusRequester = remember { FocusRequester() }
    val subtitleFocusRequester = remember { FocusRequester() } // 🔥 সাবটাইটেল ফোকাস

    var softwareVolumePercent by remember { mutableStateOf(100) } 
    val volumeProcessor = remember { VolumeBoostProcessor() }
    
    var toastMessage by remember { mutableStateOf("") }
    var showActionToast by remember { mutableStateOf(false) }
    var toastTrigger by remember { mutableStateOf(0L) }
    
    var showChannelToast by remember { mutableStateOf(false) }
    
    var isUpLongPressed by remember { mutableStateOf(false) }
    var isDownLongPressed by remember { mutableStateOf(false) }
    var isSliderFocused by remember { mutableStateOf(false) }
    var seekJob by remember { mutableStateOf<Job?>(null) }

    val trackSelector = remember { DefaultTrackSelector(context) }
 // 🔥 অডিও ট্র্যাকের জন্য নতুন স্টেটগুলো এখানে বসান
    var availableAudioTracks by remember { mutableStateOf<List<Pair<TrackGroup, Int>>>(emptyList()) }
    var currentAudioTrackIndex by remember { mutableStateOf(0) }
    var availableQualities by remember { mutableStateOf<List<VideoQuality>>(emptyList()) }
    var selectedQuality by remember { mutableStateOf<VideoQuality?>(null) } 
    var showQualityDialog by remember { mutableStateOf(false) }

    // 🔥 মুছে যাওয়া ভেরিয়েবলগুলো আবার দেওয়া হলো (যাতে কোডের কোথাও এরর না আসে)
    var isSwDecoder by remember { mutableStateOf(false) } 
    var lastLoadedUrl by remember { mutableStateOf("") }

    val exoPlayer = remember<ExoPlayer> {
        val renderersFactory = object : androidx.media3.exoplayer.DefaultRenderersFactory(context) {
            override fun buildAudioRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                audioSink: androidx.media3.exoplayer.audio.AudioSink,
                eventHandler: android.os.Handler,
                eventListener: androidx.media3.exoplayer.audio.AudioRendererEventListener,
                out: java.util.ArrayList<Renderer>
            ) {
                val customAudioSink = DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(volumeProcessor))
                    .build()
                
                super.buildAudioRenderers(
                    context,
                    extensionRendererMode,
                    mediaCodecSelector,
                    enableDecoderFallback, 
                    customAudioSink,
                    eventHandler,
                    eventListener,
                    out
                )
            }
        }.setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON) // 🔥 PREFER এর বদলে ON দেওয়া হলো
        
        // 🔥 লাইভ টিভি ও ডিরেক্ট মিডিয়ার জন্য বাফার সাইজ কমানো হলো (দ্রুত প্লে হবে)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15000, 50000, 1000, 2000)
            .build()

        ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl) 
            .build().apply { playWhenReady = true }
    }

    LaunchedEffect(currentIndex) {
        // 🔥 বুট অন লঞ্চের জন্য লাস্ট চ্যানেল সেভ রাখা হচ্ছে (সাবটাইটেল সহ)
        val currentChannel = playlist.getOrNull(currentIndex)
        if (currentChannel != null) {
            prefs.edit()
                .putInt("last_played_channel_index", currentIndex)
                .putString("last_played_channel_url", currentChannel.url)
                .putString("last_played_channel_name", currentChannel.name)
                .putString("last_played_channel_logo", currentChannel.logo)
                .putString("last_played_channel_sub", currentChannel.subtitleUrl ?: "") // 🔥 সাবটাইটেল লিংক সেভ করা হলো
                .apply()
        }
        
        showChannelToast = true
        delay(2000L)
        showChannelToast = false
    }

    fun handleIncrementalSeek(isForward: Boolean) {
        if (duration <= 0L || duration == C.TIME_UNSET) return 
        
        val seekStep = 10000L 
        val targetPosition = if (isForward) {
            (exoPlayer.currentPosition + seekStep).coerceAtMost(duration)
        } else {
            (exoPlayer.currentPosition - seekStep).coerceAtLeast(0)
        }
        
        exoPlayer.seekTo(targetPosition)

        seekJob?.cancel()
        seekJob = coroutineScope.launch {
            delay(1000) 
            if (!isControllerVisible) {
                isControllerVisible = true
                hideTimeout = 8000L
                delay(100)
                try { server1FocusRequester.requestFocus() } catch (e: Exception) {}
            } else {
                hideTimeout = 8000L
            }
        }
    }

    fun showToast(message: String) {
        toastMessage = message
        toastTrigger = System.currentTimeMillis()
    }

    val updateVolume = { newVol: Int ->
        softwareVolumePercent = newVol.coerceIn(0, 300)
        if (softwareVolumePercent <= 100) {
            exoPlayer.volume = softwareVolumePercent / 100f
            volumeProcessor.volumeMultiplier = 1.0f
        } else {
            exoPlayer.volume = 1.0f
            volumeProcessor.volumeMultiplier = (softwareVolumePercent / 100f) * 1.8f 
        }
        showToast("Volume: $softwareVolumePercent%")
    }

    LaunchedEffect(toastTrigger) {
        if (toastTrigger > 0) {
            showActionToast = true
            delay(1500) 
            showActionToast = false
        }
    }

    LaunchedEffect(isControllerVisible) {
        if (isControllerVisible) {
            delay(100) 
            try { 
                if (!isSliderFocused) server1FocusRequester.requestFocus() 
            } catch (e: Exception) {}
        } else {
            try { rootFocusRequester.requestFocus() } catch (e: Exception) {}
        }
    }

    LaunchedEffect(showQualityDialog, showSubtitleDialog) {
        if (showQualityDialog) {
            delay(100)
            try { qualityFocusRequester.requestFocus() } catch (e: Exception) {}
        }
        if (showSubtitleDialog) {
            delay(100)
            try { subtitleFocusRequester.requestFocus() } catch (e: Exception) {}
        }
    }

    DisposableEffect(Unit) {
        val window = activity?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }

        if (!isTv && window != null && insetsController != null) {
            window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val attrs = window.attributes
                attrs.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                window.attributes = attrs 
            }
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }

        onDispose {
            if (!isTv && window != null && insetsController != null) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                window.decorView.setBackgroundColor(android.graphics.Color.parseColor("#181623")) 
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    // 🔥 বাফারিং টাইমআউটের জন্য নতুন জব
    var bufferingJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            
            override fun onIsPlayingChanged(isPlayingState: Boolean) {
                isPlaying = isPlayingState
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                
                if (state == Player.STATE_READY) {
                    // প্লে শুরু হলে বাফারিং জব বাতিল করে দিন
                    bufferingJob?.cancel()
                    
                    val realDuration = exoPlayer.duration
                    duration = if (realDuration > 0 && realDuration != C.TIME_UNSET) realDuration else 0L
                }

                if (state == Player.STATE_BUFFERING) {
                    // বাফারিং শুরু হলেই 10 সেকেন্ডের টাইমার চালু হবে
                    bufferingJob?.cancel()
                    bufferingJob = coroutineScope.launch {
                        delay(20000L) // 10 সেকেন্ড অপেক্ষা
                        if (isActive && playbackState == Player.STATE_BUFFERING) {
                            // 10 সেকেন্ড পার হয়ে গেছে, এখনো বাফারিং! এবার নেক্সট চ্যানেলে যাও
                            showToast("Channel timeout! Skipping to next channel...")
                            currentServerIndex = 0 
                            currentIndex = if (currentIndex < playlist.size - 1) currentIndex + 1 else 0
                        }
                    }
                }

                if (state == Player.STATE_ENDED) {
                    currentIndex = if (currentIndex < playlist.size - 1) currentIndex + 1 else 0
                    isControllerVisible = false
                }
            }
                       
            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                val currentChannel = playlist[currentIndex]
                
                if (currentChannel.urls.isNotEmpty() && currentServerIndex < currentChannel.urls.size - 1) {
                    showToast("Server ${currentServerIndex + 1} Failed! Switching to Server ${currentServerIndex + 2}...")
                    coroutineScope.launch {
                        delay(1500) 
                        currentServerIndex += 1
                    }
                } else {
                    showToast("Channel Failed! Skipping to next channel...")
                    coroutineScope.launch {
                        delay(2000) 
                        currentServerIndex = 0 
                        currentIndex = if (currentIndex < playlist.size - 1) currentIndex + 1 else 0 
                    }
                }
            }
            
            override fun onTracksChanged(tracks: Tracks) {
                val qualities = mutableListOf<VideoQuality>()
                val aTracks = mutableListOf<Pair<TrackGroup, Int>>() 
                
                tracks.groups.forEach { trackGroupInfo ->
                    if (trackGroupInfo.type == C.TRACK_TYPE_VIDEO) {
                        val group = trackGroupInfo.mediaTrackGroup
                        for (i in 0 until group.length) {
                            val format = group.getFormat(i)
                            if (format.height > 0) qualities.add(VideoQuality(group, i, format.height, format.bitrate))
                        }
                    }
                    else if (trackGroupInfo.type == C.TRACK_TYPE_AUDIO) {
                        val group = trackGroupInfo.mediaTrackGroup
                        for (i in 0 until group.length) {
                            aTracks.add(Pair(group, i))
                        }
                    }
                }
                
                availableQualities = qualities.distinctBy { it.height }.sortedByDescending { it.height }
                availableAudioTracks = aTracks 

                if (savedQualityHeight > 0 && availableQualities.isNotEmpty()) {
                    val bestMatch = availableQualities.minByOrNull { abs(it.height - savedQualityHeight) }
                    if (bestMatch != null) {
                        val params = trackSelector.buildUponParameters()
                        params.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                        params.addOverride(TrackSelectionOverride(bestMatch.trackGroup, listOf(bestMatch.trackIndex)))
                        trackSelector.setParameters(params.build())
                        selectedQuality = bestMatch
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { 
            bufferingJob?.cancel() // মেমরি লিক এড়াতে
            exoPlayer.removeListener(listener)
            exoPlayer.release() 
        }
    }

     LaunchedEffect(isPlaying, isControllerVisible) {
        while (isPlaying && isControllerVisible) {
            currentPosition = exoPlayer.currentPosition
            
            // 🔥 প্লে চলাকালীন সময়ও ডিউরেশন ক্রস-চেক করে আপডেট রাখা হচ্ছে
            val realDuration = exoPlayer.duration
            if (realDuration > 0 && realDuration != C.TIME_UNSET) {
                duration = realDuration
            }
            
            delay(500) // ৫০০ms পর পর স্মুথ রিফ্রেশ
        }
    }
    LaunchedEffect(currentIndex, currentServerIndex, streamRules, exoPlayer) {
        currentAudioTrackIndex = 0 
        
        val channel = playlist[currentIndex]
        val url = channel.urls.getOrNull(currentServerIndex) ?: channel.url
        
        val config = getConfigForUrl(url, streamRules)

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(config.userAgent)
            .setAllowCrossProtocolRedirects(true)
            
        if (config.headers.isNotEmpty()) {
            dataSourceFactory.setDefaultRequestProperties(config.headers)
        }

        val mediaItemBuilder = MediaItem.Builder().setUri(url)

        // 🔥 কাস্টম সাবটাইটেল থাকলে সেটা যুক্ত করা
        if (!channel.subtitleUrl.isNullOrEmpty()) {
            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(channel.subtitleUrl))
                .setMimeType(MimeTypes.TEXT_SSA) 
                .setLanguage("bn") // কাস্টম সাবটাইটেলের ভাষা 'bn' সেট করা হলো
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            mediaItemBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
        }

        // 🔥 Embedded CC পুরোপুরি বন্ধ করার ম্যাজিক লজিক
        val paramsBuilder = trackSelector.buildUponParameters()
        if (channel.subtitleUrl.isNullOrEmpty() || !isSubtitleEnabled) {
            // কাস্টম সাবটাইটেল না থাকলে বা অফ থাকলে সব টেক্সট ট্র্যাক (CC) সম্পূর্ণ ব্লক করে দিবে
            paramsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            // কাস্টম সাবটাইটেল থাকলে শুধু 'bn' ভাষার (কাস্টম) সাবটাইটেলটিই পারমিশন পাবে
            paramsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            paramsBuilder.setPreferredTextLanguage("bn") 
            paramsBuilder.setIgnoredTextSelectionFlags(C.SELECTION_FLAG_FORCED or C.SELECTION_FLAG_AUTOSELECT)
        }
        trackSelector.parameters = paramsBuilder.build()

        if (config.isDrm) {
            val drmBuilder = when (config.drmType.lowercase(Locale.getDefault())) {
                "widevine" -> MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                    .setLicenseUri(config.drmLicenseUrl)
                    .apply { if (config.headers.isNotEmpty()) setLicenseRequestHeaders(config.headers) }
                "clearkey" -> MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID)
                    .setLicenseUri(config.drmLicenseUrl)
                else -> null
            }
            drmBuilder?.let { mediaItemBuilder.setDrmConfiguration(it.build()) }
        }

        val mediaItem = mediaItemBuilder.build()
        val mediaSource = DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem)

        exoPlayer.setMediaSource(mediaSource)
        
        if (url == lastLoadedUrl && currentPosition > 0L) {
            exoPlayer.seekTo(currentPosition)
        } else {
            currentPosition = 0L 
        }
        lastLoadedUrl = url

        exoPlayer.prepare()
        exoPlayer.play()
        
        isControllerVisible = false 
    }

    // 🔥 আপডেট করা অটো-হাইড লজিক
    LaunchedEffect(isControllerVisible, playbackState, isPlaying, hideTimeout, isChannelListVisible) {
        if (isControllerVisible && isPlaying && playbackState == Player.STATE_READY && !isChannelListVisible) {
            delay(hideTimeout)
            isControllerVisible = false
        }
    }
    
    LaunchedEffect(Unit) { rootFocusRequester.requestFocus() }

    // 🔥 Subtitle Settings Dialog
    if (showSubtitleDialog) {
        Dialog(
            onDismissRequest = { showSubtitleDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { showSubtitleDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(if (isTv) 0.5f else 0.8f) 
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF181623)) 
                        .padding(24.dp)
                        .clickable(enabled = false) {} 
                ) {
                    Text("Subtitle Settings", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    // 🔥 সরাসরি ডায়ালগ থেকে Subtitle অন/অফ করার সুইচ
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Subtitles", color = Color.White, fontSize = 16.sp)
                        var isSwitchFocused by remember { mutableStateOf(false) }
                        Switch(
                            checked = isSubtitleEnabled,
                            onCheckedChange = { isEnabled ->
                                isSubtitleEnabled = isEnabled
                                prefs.edit().putBoolean("saved_subtitle_enabled", isSubtitleEnabled).apply()
                                
                                val paramsBuilder = trackSelector.buildUponParameters()
                                if (!isSubtitleEnabled) {
                                    paramsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                } else {
                                    paramsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                    paramsBuilder.setPreferredTextLanguage("bn")
                                }
                                trackSelector.parameters = paramsBuilder.build()
                            },
                            modifier = Modifier
                                .focusRequester(subtitleFocusRequester)
                                .onFocusChanged { isSwitchFocused = it.isFocused }
                                .border(if (isSwitchFocused) 2.dp else 0.dp, if (isSwitchFocused) Color.White else Color.Transparent, CircleShape)
                                // 🔥 টিভির রিমোটের OK বাটনের জন্য এক্সট্রা সাপোর্ট
                                .onKeyEvent { event ->
                                    if (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
                                        if (event.type == KeyEventType.KeyUp) {
                                            isSubtitleEnabled = !isSubtitleEnabled
                                            prefs.edit().putBoolean("saved_subtitle_enabled", isSubtitleEnabled).apply()
                                            
                                            val paramsBuilder = trackSelector.buildUponParameters()
                                            if (!isSubtitleEnabled) {
                                                paramsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                            } else {
                                                paramsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                paramsBuilder.setPreferredTextLanguage("bn")
                                            }
                                            trackSelector.parameters = paramsBuilder.build()
                                            true
                                        } else false
                                    } else false
                                },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black, 
                                checkedTrackColor = Color.Yellow,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            )
                        )
                    }
                    Text("Subtitle Size: ${(subtitleSize * 1000).toInt()}", color = Color.Yellow)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        var isLargerFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = { 
                                subtitleSize = (subtitleSize + 0.005f).coerceAtMost(0.2f)
                                prefs.edit().putFloat("saved_sub_size", subtitleSize).apply() 
                            },
                            enabled = isSubtitleEnabled, // 🔥 অফ থাকলে বাটন কাজ করবে না
                            modifier = Modifier
                                .onFocusChanged { isLargerFocused = it.isFocused }
                                .border(if (isLargerFocused) 2.dp else 0.dp, if (isLargerFocused) Color.White else Color.Transparent, RoundedCornerShape(50)),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isLargerFocused) Color.Yellow else Color.DarkGray)
                        ) { Text("Larger (+)", color = if (isLargerFocused) Color.Black else Color.White) }
                        
                        var isSmallerFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = { 
                                subtitleSize = (subtitleSize - 0.005f).coerceAtLeast(0.01f)
                                prefs.edit().putFloat("saved_sub_size", subtitleSize).apply() 
                            },
                            enabled = isSubtitleEnabled, // 🔥 অফ থাকলে বাটন কাজ করবে না
                            modifier = Modifier
                                .onFocusChanged { isSmallerFocused = it.isFocused }
                                .border(if (isSmallerFocused) 2.dp else 0.dp, if (isSmallerFocused) Color.White else Color.Transparent, RoundedCornerShape(50)),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isSmallerFocused) Color.Yellow else Color.DarkGray)
                        ) { Text("Smaller (-)", color = if (isSmallerFocused) Color.Black else Color.White) }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Position Offset: ${subtitleOffset.toInt()}", color = Color.Yellow)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        var isUpFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = { 
                                subtitleOffset = (subtitleOffset + 20f).coerceAtMost(800f) 
                                prefs.edit().putFloat("saved_sub_offset_y", subtitleOffset).apply() 
                            },
                            enabled = isSubtitleEnabled,
                            modifier = Modifier
                                .onFocusChanged { isUpFocused = it.isFocused }
                                .border(if (isUpFocused) 2.dp else 0.dp, if (isUpFocused) Color.White else Color.Transparent, RoundedCornerShape(50)),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isUpFocused) Color.Yellow else Color.DarkGray)
                        ) { Text("Move UP", color = if (isUpFocused) Color.Black else Color.White) }
                        
                        var isDownFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = { 
                                subtitleOffset = (subtitleOffset - 20f).coerceAtLeast(0f)
                                prefs.edit().putFloat("saved_sub_offset_y", subtitleOffset).apply() 
                            },
                            enabled = isSubtitleEnabled,
                            modifier = Modifier
                                .onFocusChanged { isDownFocused = it.isFocused }
                                .border(if (isDownFocused) 2.dp else 0.dp, if (isDownFocused) Color.White else Color.Transparent, RoundedCornerShape(50)),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isDownFocused) Color.Yellow else Color.DarkGray)
                        ) { Text("Move DOWN", color = if (isDownFocused) Color.Black else Color.White) }
                    }
                }
            }
        }
    }
    if (showQualityDialog) {
        Dialog(
            onDismissRequest = { showQualityDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { showQualityDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(if (isTv) 0.6f else 0.8f) 
                        .fillMaxHeight(0.7f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF181623)) 
                        .padding(24.dp)
                        .clickable(enabled = false) {} 
                ) {
                    Text(
                        text = "Select Video Quality", 
                        color = Color.White, 
                        fontSize = 20.sp, 
                        fontWeight = FontWeight.Bold, 
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        item(span = { GridItemSpan(2) }) {
                            var isAutoFocused by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            isAutoFocused -> Color.White.copy(alpha = 0.2f)
                                            selectedQuality == null -> Color.Yellow
                                            else -> Color.DarkGray.copy(alpha = 0.5f)
                                        }
                                    )
                                    .focusRequester(qualityFocusRequester) 
                                    .onFocusChanged { isAutoFocused = it.isFocused }
                                    .clickable {
                                        prefs.edit().putInt("saved_quality_height", 0).apply()
                                        savedQualityHeight = 0
                                        val params = trackSelector.buildUponParameters()
                                        params.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                        trackSelector.setParameters(params.build())
                                        selectedQuality = null
                                        showQualityDialog = false
                                        showToast("Quality set to Auto")
                                    }
                                    .focusable() 
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Auto Quality", 
                                    color = if (selectedQuality == null && !isAutoFocused) Color.Black else Color.White, 
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }

                        items(availableQualities) { quality ->
                            val isSelected = selectedQuality?.height == quality.height
                            var isItemFocused by remember { mutableStateOf(false) }
                            
                            val bitrateText = if (quality.bitrate > 0) {
                                if (quality.bitrate >= 1_000_000) String.format(Locale.US, "%.1f Mbps", quality.bitrate / 1_000_000f)
                                else String.format(Locale.US, "%d Kbps", quality.bitrate / 1000)
                            } else ""

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            isItemFocused -> Color.White.copy(alpha = 0.2f)
                                            isSelected -> Color.Yellow
                                            else -> Color.DarkGray.copy(alpha = 0.5f)
                                        }
                                    )
                                    .onFocusChanged { isItemFocused = it.isFocused }
                                    .clickable {
                                        prefs.edit().putInt("saved_quality_height", quality.height).apply()
                                        savedQualityHeight = quality.height
                                        val params = trackSelector.buildUponParameters()
                                        params.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                        params.addOverride(TrackSelectionOverride(quality.trackGroup, listOf(quality.trackIndex)))
                                        trackSelector.setParameters(params.build())
                                        selectedQuality = quality
                                        showQualityDialog = false
                                        showToast("Quality saved: ${quality.height}p")
                                    }
                                    .focusable() 
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "${quality.height}p",
                                    color = if (isSelected && !isItemFocused) Color.Black else Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp
                                )
                                if (bitrateText.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = bitrateText,
                                        color = if (isSelected && !isItemFocused) Color.DarkGray else Color.LightGray,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val isDown = event.type == KeyEventType.KeyDown
                val isUp = event.type == KeyEventType.KeyUp
                val keyCode = event.nativeKeyEvent.keyCode

                // 🔥 ডায়াল UI খোলা থাকলে প্লেয়ার সিগন্যাল ব্লক না করে ডায়ালপ্যাডকে কাজ করতে দেবে
                if (showDialer) return@onPreviewKeyEvent false

                // 🔥 আপডেট করা OK বাটন লজিক (Long press for TV)
                if (keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER || keyCode == AndroidKeyEvent.KEYCODE_ENTER || keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER) {
                    if (isDown && event.nativeKeyEvent.repeatCount > 0) {
                        // লং প্রেস হলে ডায়ালার ওপেন হবে (শুধুমাত্র টিভিতে)
                        if (isTv && !isControllerVisible && !isChannelListVisible) {
                            isOkLongPressed = true
                            showDialer = true
                        }
                        return@onPreviewKeyEvent true
                    } else if (isUp) {
                        val wasLongPress = isOkLongPressed
                        isOkLongPressed = false
                        
                        // 🔥 যদি কন্ট্রোলার বা চ্যানেল লিস্ট ওপেন থাকে, তবে OK সিগন্যালটিকে ভেতরের বাটনগুলোতে যেতে দিন (false রিটার্ন করে)
                        if (isControllerVisible || isChannelListVisible) {
                            hideTimeout = 8000L
                            return@onPreviewKeyEvent false 
                        }
                        
                        // শর্ট প্রেস হলে কন্ট্রোলার ওপেন হবে
                        if (!wasLongPress) {
                            hideTimeout = 8000L 
                            isControllerVisible = true
                            return@onPreviewKeyEvent true 
                        }
                        return@onPreviewKeyEvent true
                    }
                }

                if (isControllerVisible) {
                    if (isSliderFocused && (keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT || keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT)) {
                        if (isDown) {
                            handleIncrementalSeek(isForward = (keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT))
                        }
                        return@onPreviewKeyEvent true
                    }

                    // 🔥 আপডেট করা ব্যাক বাটন লজিক
                    if (keyCode == AndroidKeyEvent.KEYCODE_BACK || keyCode == AndroidKeyEvent.KEYCODE_ESCAPE) {
                        if (isUp) {
                            if (isChannelListVisible) {
                                isChannelListVisible = false
                                hideTimeout = 8000L
                                try { rootFocusRequester.requestFocus() } catch (e: Exception) {}
                                return@onPreviewKeyEvent true
                            } else if (isControllerVisible) {
                                isControllerVisible = false
                                return@onPreviewKeyEvent true
                            }
                        }
                        if (isChannelListVisible || isControllerVisible) return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                } else {
                    when (keyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                            if (isDown && event.nativeKeyEvent.repeatCount > 0) {
                                isUpLongPressed = true
                                updateVolume(softwareVolumePercent + 5) 
                            } else if (isUp) {
                                if (!isUpLongPressed) {
                                    currentIndex = if (currentIndex > 0) currentIndex - 1 else playlist.size - 1
                                    isControllerVisible = false 
                                }
                                isUpLongPressed = false 
                            }
                            return@onPreviewKeyEvent true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (isDown && event.nativeKeyEvent.repeatCount > 0) {
                                isDownLongPressed = true
                                updateVolume(softwareVolumePercent - 5) 
                            } else if (isUp) {
                                if (!isDownLongPressed) {
                                    currentIndex = if (currentIndex < playlist.size - 1) currentIndex + 1 else 0
                                    isControllerVisible = false 
                                }
                                isDownLongPressed = false 
                            }
                            return@onPreviewKeyEvent true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (isDown) {
                                isControllerVisible = true
                                hideTimeout = 8000L
                                handleIncrementalSeek(isForward = (keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT))
                            }
                            return@onPreviewKeyEvent true
                        }
                    }
                }
                false
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { 
                        hideTimeout = 8000L 
                        isControllerVisible = !isControllerVisible 
                    }
                )
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                androidx.media3.ui.PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false 
                    keepScreenOn = true 
                    isFocusable = false
                    isFocusableInTouchMode = false
                }
            },
            // 🔥 রিয়েল টাইম সাবটাইটেল আপডেটের জন্য লজিক এখানে আনা হয়েছে
            update = { view ->
                view.resizeMode = currentResizeMode 
                
                try {
                    val customTypeface = ResourcesCompat.getFont(view.context, R.font.noto_serif_bengali)
                    
                    view.subtitleView?.apply {
                        // ASS এর ডিফল্ট কনফিগার ওভাররাইড করে নিজেদের কন্ট্রোল নেওয়ার জন্য false
                        setApplyEmbeddedStyles(false)
                        setApplyEmbeddedFontSizes(false)
                        
                        // ডাইনামিক সাইজ ও পজিশন (যা ইউজার ডায়ালগ থেকে কন্ট্রোল করবে)
                        setFractionalTextSize(subtitleSize)
                        
                        // 🔥 ExoPlayer এর ডিফল্ট প্যাডিং বাদ দিয়ে সরাসরি translationY ব্যবহার করে ভিউটিকে উপরে ওঠানো হলো
                        translationY = -subtitleOffset 
                        
                        if (customTypeface != null) {
                            setStyle(
                                CaptionStyleCompat(
                                    android.graphics.Color.WHITE, 
                                    android.graphics.Color.TRANSPARENT, 
                                    android.graphics.Color.TRANSPARENT, 
                                    CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, 
                                    android.graphics.Color.BLACK, 
                                    customTypeface
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        )

        if (playbackState == Player.STATE_BUFFERING) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.Yellow, strokeWidth = 4.dp, modifier = Modifier.size(64.dp))
            }
        }

        AnimatedVisibility(
            visible = showActionToast, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp)
        ) {
            Box(modifier = Modifier.background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(16.dp)).padding(horizontal = 24.dp, vertical = 12.dp)) {
                Text(text = toastMessage, color = Color.Yellow, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }

        AnimatedVisibility(
            visible = showChannelToast, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 32.dp)
        ) {
            Box(modifier = Modifier.background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(12.dp)).padding(horizontal = 24.dp, vertical = 12.dp)) {
                // 🔥 JSON-এ নাম্বার না থাকলে লিস্টের ইনডেক্স থেকে নাম্বার তৈরি করবে (+1 কারণ ইনডেক্স ০ থেকে শুরু হয়)
                val currentCh = playlist.getOrNull(currentIndex)
                val chNum = currentCh?.channelNumber ?: (currentIndex + 1).toString()
                val chName = currentCh?.name ?: ""
                
                Text(
                    text = "$chNum. $chName", 
                    color = Color.White, 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 20.sp
                )
            }
        }

        if (isControllerVisible) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))) {
                if (!isTv) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { currentIndex = if (currentIndex > 0) currentIndex - 1 else playlist.size - 1 }, modifier = Modifier.size(64.dp)) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(48.dp))
                        }
                        Spacer(modifier = Modifier.width(32.dp))
                        Box(
                            modifier = Modifier.size(72.dp).clip(CircleShape).background(Color.Yellow)
                                .clickable { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", tint = Color.Black, modifier = Modifier.size(48.dp))
                        }
                        Spacer(modifier = Modifier.width(32.dp))
                        IconButton(onClick = { currentIndex = if (currentIndex < playlist.size - 1) currentIndex + 1 else 0 }, modifier = Modifier.size(64.dp)) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(48.dp))
                        }
                    }
                }

                val currentChannel = playlist[currentIndex]

                Column(modifier = Modifier.fillMaxWidth().align(Alignment.BottomStart).padding(horizontal = 32.dp, vertical = 24.dp)) {
                    Column(modifier = Modifier.fillMaxWidth(0.6f)) {
                        Text(text = "Now Playing", color = Color.Yellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = currentChannel.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "▲/▼ Click: Change Channel | ▲/▼ Hold: 300% Boost! | ◄/► Seek", color = Color.LightGray, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (currentChannel.urls.isNotEmpty()) {
                            Row(
                                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                currentChannel.urls.forEachIndexed { index, _ ->
                                    val isSelected = currentServerIndex == index
                                    var isFocused by remember { mutableStateOf(false) }
                                    
                                    Button(
                                        onClick = { currentServerIndex = index },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isSelected) Color.Yellow else Color.DarkGray, 
                                            contentColor = if (isSelected) Color.Black else Color.White
                                        ),
                                        border = if (isFocused) BorderStroke(2.dp, Color.White) else null,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .then(if (index == 0) Modifier.focusRequester(server1FocusRequester) else Modifier)
                                            .onFocusChanged { isFocused = it.isFocused }
                                            .focusable() 
                                    ) {
                                        Text(text = "Server ${index + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {

                            // 🔥 অডিও ট্র্যাক বাটন (শুধুমাত্র যদি ১ টার বেশি অডিও থাকে তবেই দেখাবে)
                            if (availableAudioTracks.size > 1) {
                                var isAudioFocused by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(if (isAudioFocused) Color.White.copy(alpha = 0.3f) else Color.Transparent)
                                        .onFocusChanged { isAudioFocused = it.isFocused }
                                        .onKeyEvent { event ->
                                            if (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
                                                if (event.type == KeyEventType.KeyUp) {
                                                    currentAudioTrackIndex = (currentAudioTrackIndex + 1) % availableAudioTracks.size
                                                    val selectedAudio = availableAudioTracks[currentAudioTrackIndex]
                                                    val params = trackSelector.buildUponParameters()
                                                    params.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                                    params.addOverride(TrackSelectionOverride(selectedAudio.first, listOf(selectedAudio.second)))
                                                    trackSelector.setParameters(params.build())
                                                    true
                                                } else false
                                            } else false
                                        }
                                        .clickable { 
                                            currentAudioTrackIndex = (currentAudioTrackIndex + 1) % availableAudioTracks.size
                                            val selectedAudio = availableAudioTracks[currentAudioTrackIndex]
                                            val params = trackSelector.buildUponParameters()
                                            params.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                            params.addOverride(TrackSelectionOverride(selectedAudio.first, listOf(selectedAudio.second)))
                                            trackSelector.setParameters(params.build())
                                        }
                                        .focusable(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "TRK ${currentAudioTrackIndex + 1}",
                                        color = Color.Yellow, // 🔥 অডিও ট্র্যাকের টেক্সট হলুদ থাকবে
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            // 🔥 HW/SW টগল বাটন (Toast বাদ দেওয়া হয়েছে)
                            var isHwSwFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(if (isHwSwFocused) Color.White.copy(alpha = 0.3f) else Color.Transparent)
                                    .onFocusChanged { isHwSwFocused = it.isFocused }
                                    .onKeyEvent { event ->
                                        if (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
                                            if (event.type == KeyEventType.KeyUp) {
                                                isSwDecoder = !isSwDecoder
                                                true
                                            } else false
                                        } else false
                                    }
                                    .clickable { isSwDecoder = !isSwDecoder }
                                    .focusable(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isSwDecoder) "SW" else "HW",
                                    color = if (isSwDecoder) Color.Yellow else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }

                            // 🔥 Subtitle টগল এবং সেটিং বাটন (Toast বাদ দেওয়া হয়েছে)
                            if (!currentChannel.subtitleUrl.isNullOrEmpty()) {
                                TvFocusableIconButton(
                                    onClick = { 
                                        isSubtitleEnabled = !isSubtitleEnabled
                                        prefs.edit().putBoolean("saved_subtitle_enabled", isSubtitleEnabled).apply()
                                        trackSelector.parameters = trackSelector.buildUponParameters()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !isSubtitleEnabled)
                                            .build()
                                    },
                                    icon = Icons.Default.Subtitles,
                                    contentDescription = "Toggle Subtitle",
                                    tint = if (isSubtitleEnabled) Color.Yellow else Color.White
                                )
                                
                                if (isSubtitleEnabled) {
                                    TvFocusableIconButton(
                                        onClick = { showSubtitleDialog = true },
                                        icon = Icons.Default.Edit,
                                        contentDescription = "Subtitle Settings",
                                        tint = Color.White
                                    )
                                }
                            }
                            
                            TvFocusableIconButton(
                                onClick = { 
                                    isChannelListVisible = !isChannelListVisible 
                                    hideTimeout = if (isChannelListVisible) Long.MAX_VALUE else 8000L
                                },
                                icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                                contentDescription = "Channel List",
                                tint = if (isChannelListVisible) Color.Yellow else Color.White
                            )

                            // 🔥 Aspect Ratio বাটন (Toast বাদ এবং ডায়নামিক আইকন যুক্ত)
                            val resizeIcon = when(currentResizeMode) {
                                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT -> Icons.Default.FullscreenExit
                                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> Icons.Default.ZoomInMap
                                else -> Icons.Default.Fullscreen
                            }
                            TvFocusableIconButton(
                                onClick = { 
                                    val nextIndex = (resizeModes.indexOf(currentResizeMode) + 1) % resizeModes.size
                                    currentResizeMode = resizeModes[nextIndex]
                                    prefs.edit().putInt("saved_resize_mode", currentResizeMode).apply()
                                },
                                icon = resizeIcon,
                                contentDescription = "Resize Screen",
                                tint = Color.White
                            )
                            
                            // 🔥 Rotation বাটন (Toast বাদ)
                            if (!isTv) {
                                val rotationIcon = if (isLandscape) Icons.Default.Smartphone else Icons.Default.ScreenRotation
                                TvFocusableIconButton(
                                    onClick = {
                                        isLandscape = !isLandscape
                                        activity?.requestedOrientation = if (isLandscape) {
                                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                        } else {
                                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        }
                                    },
                                    icon = rotationIcon,
                                    contentDescription = "Rotation",
                                    tint = Color.White
                                )
                            }
                            
                            // Quality Settings (এটির ডায়ালগ আছে তাই সমস্যা নেই)
                            if (availableQualities.isNotEmpty()) {
                                TvFocusableIconButton(
                                    onClick = { showQualityDialog = true },
                                    icon = Icons.Default.Settings,
                                    contentDescription = "Quality Settings",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                     val isLive = duration <= 0L || duration == C.TIME_UNSET

                    Row(
                        modifier = Modifier.fillMaxWidth(), 
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isLive) "LIVE" else formatTime(currentPosition), 
                            color = Color.White, 
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSliderFocused) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.5f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .onFocusChanged { isSliderFocused = it.isFocused }
                                .focusable(!isLive) 
                        ) {
                            Slider(
                                value = currentPosition.coerceIn(0L, duration.coerceAtLeast(1L)).toFloat(),
                                onValueChange = { currentPosition = it.toLong() },
                                onValueChangeFinished = { exoPlayer.seekTo(currentPosition) },
                                valueRange = 0f..(if (isLive) 1f else duration.coerceAtLeast(1L).toFloat()),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.Yellow, 
                                    activeTrackColor = Color.Yellow, 
                                    inactiveTrackColor = Color.Gray.copy(alpha = 0.6f),
                                    disabledThumbColor = Color.Gray,
                                    disabledActiveTrackColor = Color.DarkGray,
                                    disabledInactiveTrackColor = Color.DarkGray.copy(alpha = 0.3f)
                                ),
                                enabled = !isLive 
                            )
                        }
                        
                        Text(
                            text = if (isLive) "LIVE" else formatTime(duration), 
                            color = Color.White, 
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } 
                } 
            } 
        } 

        // 🔥 ইন-প্লেয়ার চ্যানেল লিস্ট ওভারলে
        AnimatedVisibility(
            visible = isChannelListVisible,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            OverlayChannelList(
                playlist = playlist,
                currentIndex = currentIndex,
                onChannelSelected = { selectedIndex ->
                    currentIndex = selectedIndex
                    isChannelListVisible = false // চ্যানেল সিলেক্ট হলে লিস্ট বন্ধ হয়ে যাবে
                    hideTimeout = 8000L // আবার অটো হাইড টাইমার চালু হবে
                }
            )
        }
        
        // 🔥 ডায়াল UI ওভারলে (Feature 3)
        if (showDialer) {
            TVDialerUI(
                playlist = playlist,
                onChannelFound = { foundIndex ->
                    currentIndex = foundIndex
                    showDialer = false
                    hideTimeout = 8000L
                    try { rootFocusRequester.requestFocus() } catch (e: Exception) {}
                },
                onDismiss = {
                    showDialer = false
                    try { rootFocusRequester.requestFocus() } catch (e: Exception) {}
                }
            )
        }
    } // <-- এটি মূল Box-এর শেষ ব্র্যাকেট
} // <-- এটি ExoPlayerView ফাংশনের শেষ ব্র্যাকেট

fun formatTime(ms: Long): String {
    if (ms < 0) return "00:00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
}
