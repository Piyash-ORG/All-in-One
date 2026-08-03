package com.nexaplay.tv

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.SoundEffectConstants
import android.view.WindowManager
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import coil.Coil
import coil.ImageLoader
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

val AppBg = Color(0xFF181623)
val CardBg = Color(0xFF211F30)
val AccentYellow = Color(0xFFFACC15)

enum class Tab { MATCHES, CHANNELS, CATEGORIES, FAVORITES, MORE }

fun trustAllCertificates() {
    try {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
        })
        val sc = SSLContext.getInstance("SSL")
        sc.init(null, trustAllCerts, SecureRandom())
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
        HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun getUnsafeOkHttpClient(): OkHttpClient {
    return try {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())

        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    } catch (e: Exception) {
        throw RuntimeException(e)
    }
}

object StreamRuleFetcher {
    suspend fun fetchRules(jsonUrl: String): Map<String, StreamConfig> = withContext(Dispatchers.IO) {
        val rulesMap = mutableMapOf<String, StreamConfig>()
        try {
            val connection = URL(jsonUrl).openConnection() as HttpsURLConnection
            connection.connectTimeout = 10000
            val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val jsonArray = jsonObject.optJSONArray("rules") ?: return@withContext emptyMap()
            
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val headersJson = item.optJSONObject("headers")
                val headersMap = mutableMapOf<String, String>()
                
                if (headersJson != null) {
                    val keys = headersJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        headersMap[key] = headersJson.getString(key)
                    }
                }
                
                val domain = item.optString("domain", "default")
                rulesMap[domain] = StreamConfig(
                    domain = domain,
                    userAgent = item.optString("user_agent", "ExoPlayer"),
                    headers = headersMap,
                    isDrm = item.optBoolean("is_drm", false),
                    drmType = item.optString("drm_type", ""),
                    drmLicenseUrl = item.optString("drm_license_url", ""),
                    drmKey = item.optString("drm_key", "")
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext rulesMap
    }
}

// 🔥 ঠিক এখান থেকে নিচের নতুন কোডটুকু পেস্ট করবেন
data class Match(
    val id: String,
    val leagueName: String,
    val leagueLogo: String,
    val team1Name: String,
    val team1Flag: String,
    val team2Name: String,
    val team2Flag: String,
    val startTime: String, 
    val endTime: String,
    val streamUrls: List<String>
)

object MatchFetcher {
    suspend fun fetchMatches(jsonUrl: String): List<Match> = withContext(Dispatchers.IO) {
        val matches = mutableListOf<Match>()
        try {
            val connection = URL(jsonUrl).openConnection() as HttpsURLConnection
            connection.connectTimeout = 10000
            val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val jsonArray = jsonObject.optJSONArray("matches") ?: return@withContext emptyList()
            
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val urlsArray = item.optJSONArray("stream_urls")
                val urls = mutableListOf<String>()
                if (urlsArray != null) {
                    for (j in 0 until urlsArray.length()) {
                        urls.add(urlsArray.getString(j))
                    }
                }
                matches.add(
                    Match(
                        id = item.optString("id"), leagueName = item.optString("league_name"),
                        leagueLogo = item.optString("league_logo"), team1Name = item.optString("team1_name"),
                        team1Flag = item.optString("team1_flag"), team2Name = item.optString("team2_name"),
                        team2Flag = item.optString("team2_flag"), startTime = item.optString("start_time"),
                        endTime = item.optString("end_time"), streamUrls = urls
                    )
                )
            }
        } catch (e: Exception) { e.printStackTrace() }
        return@withContext matches
    }
}
// 🔥 নতুন কোড এখানে শেষ

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        trustAllCertificates()
        
        val fromBoot = intent.getBooleanExtra("from_boot", false)
        val prefs = getSharedPreferences("NexaPlayPrefs", Context.MODE_PRIVATE)
        val isAutoLaunchEnabled = prefs.getBoolean("auto_launch_on_boot", false)
        
        // 🔥 অটো-প্লে ট্রিগার: যদি রিসিভার থেকে সিগন্যাল আসে অথবা সেটিং অন থাকে
        val shouldAutoPlay = fromBoot || isAutoLaunchEnabled
        
        val imageLoader = ImageLoader.Builder(this)
            .okHttpClient { getUnsafeOkHttpClient() }
            .build()
        Coil.setImageLoader(imageLoader)
        
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        val isTv = uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

        if (!isTv) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = android.graphics.Color.parseColor("#181623")
            window.navigationBarColor = android.graphics.Color.parseColor("#13111C")
            
            WindowCompat.setDecorFitsSystemWindows(window, true)
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.isAppearanceLightStatusBars = false
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = AppBg) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        
                        AppScreen(isTv = isTv, shouldAutoPlay = shouldAutoPlay)
                        
                        // 🔥 যদি অটো-প্লে অন না থাকে, তবেই ব্যানার/পোস্টার দেখাবে (Glitch ফিক্স)
                        if (!shouldAutoPlay) {
                            AppBannerPopup(jsonUrl = "https://cdn.jsdelivr.net/gh/piyashltd/banners@main/banners.json")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppScreen(isTv: Boolean, shouldAutoPlay: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var streamRules by remember { mutableStateOf<Map<String, StreamConfig>>(emptyMap()) }
    var currentPlayingIndex by remember { mutableStateOf<Int?>(null) }
    var currentPlayingList by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    var currentTab by remember { mutableStateOf(Tab.CHANNELS) } 
    var searchQuery by remember { mutableStateOf("") }
    
    val prefs = context.getSharedPreferences("NexaPlayPrefs", Context.MODE_PRIVATE)
    var favoriteUrls by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet()) ?: emptySet()) }

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    var lastFocusedUrl by remember { mutableStateOf<String?>(null) }
    var focusRestoreTrigger by remember { mutableStateOf(false) }

    val defaultM3uUrl = "https://cdn-direct-henna.vercel.app/index.m3u"
    val streamRulesUrl = "https://raw.githubusercontent.com/piyashltd/all-in-one/main/rules.json"

    val adManager = remember { AdManager(context) }
    var adConfig by remember { mutableStateOf(AdConfig()) }
    val adConfigUrl = "https://cdn.jsdelivr.net/gh/piyashltd/all-in-one@main/ads.json"

    LaunchedEffect(Unit) {
        try {
            val configDeferred = scope.launch { adConfig = AdConfigFetcher.fetchConfig(adConfigUrl) }
            val channelsDeferred = scope.launch { channels = M3uParser.fetchChannels(defaultM3uUrl) }
            val rulesDeferred = scope.launch { streamRules = StreamRuleFetcher.fetchRules(streamRulesUrl) }
            
            configDeferred.join()
            channelsDeferred.join()
            rulesDeferred.join()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }
    // 🔥 আল্ট্রা-স্মার্ট অটো-প্লে লজিক: সাবটাইটেল সহ টেম্পোরারি চ্যানেল বানিয়ে প্লে করবে
    LaunchedEffect(channels) {
        if (shouldAutoPlay && channels.isNotEmpty() && currentPlayingIndex == null) {
            // 🔥 এখানে ?: "" ব্যবহার করা হয়েছে যাতে এগুলো কখনোই null না হয়
            val lastUrl = prefs.getString("last_played_channel_url", "") ?: ""
            val lastName = prefs.getString("last_played_channel_name", "Saved Channel") ?: "Saved Channel"
            val lastLogo = prefs.getString("last_played_channel_logo", "") ?: ""
            val lastSub = prefs.getString("last_played_channel_sub", "")

            if (lastUrl.isNotEmpty()) {
                val foundIndex = channels.indexOfFirst { it.url == lastUrl }
                if (foundIndex != -1) {
                    // চ্যানেলটি মেইন লিস্টে থাকলে সেখান থেকেই প্লে করবে
                    currentPlayingList = channels
                    currentPlayingIndex = foundIndex
                } else {
                    // 🔥 ক্যাটাগরি বা সার্চ থেকে আসা চ্যানেল, যা মেইন লিস্টে নেই
                    // তাই সাবটাইটেল সহ কাস্টম চ্যানেল বানিয়ে প্লে করবে
                    val customChannel = Channel(
                        name = lastName, 
                        group = "Saved", 
                        url = lastUrl, 
                        urls = mutableListOf(lastUrl), 
                        logo = lastLogo,
                        subtitleUrl = if (lastSub.isNullOrEmpty()) null else lastSub
                    )
                    currentPlayingList = listOf(customChannel)
                    currentPlayingIndex = 0
                }
            } else {
                val lastIndex = prefs.getInt("last_played_channel_index", -1)
                if (lastIndex != -1 && lastIndex < channels.size) {
                    currentPlayingList = channels
                    currentPlayingIndex = lastIndex
                }
            }
        }
    }
    LaunchedEffect(currentTab) { searchQuery = "" }

    val toggleFavorite: (String) -> Unit = { url ->
        val newFavs = favoriteUrls.toMutableSet()
        if (newFavs.contains(url)) newFavs.remove(url) else newFavs.add(url)
        favoriteUrls = newFavs
        prefs.edit().putStringSet("favorites", newFavs).apply()
    }

    val clearAllFavorites: () -> Unit = {
        favoriteUrls = emptySet()
        prefs.edit().putStringSet("favorites", emptySet()).apply()
    }

    val handlePlay: (List<Channel>, Int) -> Unit = { list, index ->
        currentPlayingList = list
        currentPlayingIndex = index

        if (adConfig.adsEnabled && adConfig.adUrl.isNotEmpty() && adManager.shouldShowAd(adConfig.maxAdsPerDay)) {
            adManager.markAdShown()
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(adConfig.adUrl))
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(AppBg)) {
        if (isTv) {
            Row(modifier = Modifier.fillMaxSize()) {
                TvSideNav(currentTab) { currentTab = it }
                MainContentArea(
                    isLoading = isLoading, currentTab = currentTab, channels = channels,
                    favoriteUrls = favoriteUrls, searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onPlay = handlePlay,
                    onToggleFav = toggleFavorite, onClearAllFavs = clearAllFavorites,
                    onRefresh = { 
                        isLoading = true
                        scope.launch { 
                            try { channels = withContext(Dispatchers.IO) { M3uParser.fetchChannels(defaultM3uUrl) } } 
                            finally { isLoading = false }
                        }
                    },
                    gridState = gridState, listState = listState,
                    lastFocusedUrl = lastFocusedUrl, onItemFocused = { lastFocusedUrl = it },
                    focusRestoreTrigger = focusRestoreTrigger, onFocusRestored = { focusRestoreTrigger = false },
                    isTv = true, modifier = Modifier.weight(1f)
                )
            }
        } else {
            Scaffold(
                containerColor = AppBg,
                bottomBar = { MobileBottomNav(currentTab) { currentTab = it } }
            ) { padding ->
                MainContentArea(
                    isLoading = isLoading, currentTab = currentTab, channels = channels,
                    favoriteUrls = favoriteUrls, searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onPlay = handlePlay,
                    onToggleFav = toggleFavorite, onClearAllFavs = clearAllFavorites,
                    onRefresh = { 
                        isLoading = true
                        scope.launch { 
                            try { channels = withContext(Dispatchers.IO) { M3uParser.fetchChannels(defaultM3uUrl) } } 
                            finally { isLoading = false }
                        }
                    },
                    gridState = gridState, listState = listState,
                    lastFocusedUrl = lastFocusedUrl, onItemFocused = { lastFocusedUrl = it },
                    focusRestoreTrigger = focusRestoreTrigger, onFocusRestored = { focusRestoreTrigger = false },
                    isTv = false, modifier = Modifier.padding(padding)
                )
            }
        }

        if (currentPlayingIndex != null && currentPlayingList.isNotEmpty()) {
            BackHandler { 
                currentPlayingIndex = null 
                focusRestoreTrigger = true 
            }
            ExoPlayerView(
                playlist = currentPlayingList,
                initialIndex = currentPlayingIndex!!,
                isTv = isTv,
                streamRules = streamRules, 
                onBack = { 
                    currentPlayingIndex = null 
                    focusRestoreTrigger = true
                },
                modifier = Modifier.fillMaxSize().background(Color.Black)
            )
        }
    }
}

@Composable
fun MobileBottomNav(currentTab: Tab, onTabSelected: (Tab) -> Unit) {
    NavigationBar(containerColor = Color(0xFF13111C), contentColor = Color.Gray) {
        val tabs = Tab.entries
        tabs.forEach { tab ->
            val icon = when(tab) {
                Tab.MATCHES -> Icons.Default.EmojiEvents 
                Tab.CHANNELS -> Icons.Default.Tv 
                Tab.CATEGORIES -> Icons.Default.Category 
                Tab.FAVORITES -> Icons.Default.Favorite 
                Tab.MORE -> Icons.Default.MoreHoriz 
            }
            NavigationBarItem(
                selected = currentTab == tab,
                onClick = { onTabSelected(tab) },
                icon = { Icon(icon, contentDescription = tab.name) },
                label = { Text(tab.name, fontSize = 10.sp) },
                colors = navigationBarColors()
            )
        }
    }
}

@Composable
fun TvSideNav(currentTab: Tab, onTabSelected: (Tab) -> Unit) {
    NavigationRail(containerColor = Color(0xFF13111C), modifier = Modifier.fillMaxHeight().width(80.dp)) {
        Spacer(modifier = Modifier.height(24.dp))
        val tabs = Tab.entries
        tabs.forEach { tab ->
            val icon = when(tab) {
                Tab.MATCHES -> Icons.Default.EmojiEvents
                Tab.CHANNELS -> Icons.Default.Tv
                Tab.CATEGORIES -> Icons.Default.Category
                Tab.FAVORITES -> Icons.Default.Favorite
                Tab.MORE -> Icons.Default.MoreHoriz
            }
            val interactionSource = remember { MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()
            
            NavigationRailItem(
                selected = currentTab == tab,
                onClick = { onTabSelected(tab) },
                icon = { Icon(icon, contentDescription = tab.name) },
                interactionSource = interactionSource, 
                modifier = Modifier.border(2.dp, if(isFocused) AccentYellow else Color.Transparent, CircleShape),
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = AccentYellow, unselectedIconColor = Color.Gray, indicatorColor = Color(0xFF2A273F)
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun navigationBarColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = AccentYellow, selectedTextColor = AccentYellow,
    unselectedIconColor = Color.Gray, unselectedTextColor = Color.Gray, indicatorColor = Color.Transparent
)

@Composable
fun MainContentArea(
    isLoading: Boolean, currentTab: Tab, channels: List<Channel>, favoriteUrls: Set<String>,
    searchQuery: String, onSearchQueryChange: (String) -> Unit,
    onPlay: (List<Channel>, Int) -> Unit, onToggleFav: (String) -> Unit, onClearAllFavs: () -> Unit,
    onRefresh: () -> Unit, gridState: LazyGridState, listState: LazyListState,
    lastFocusedUrl: String?, onItemFocused: (String) -> Unit, focusRestoreTrigger: Boolean,
    onFocusRestored: () -> Unit, isTv: Boolean, modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    
    // 🔥 সেটিংসের ডেটাগুলো সবার ওপরে আনা হলো (যাতে সব ট্যাবে কাজ করে)
    val contextForPrefs = LocalContext.current
    val prefsForSettings = remember { contextForPrefs.getSharedPreferences("NexaPlayPrefs", Context.MODE_PRIVATE) }
    var isAutoLaunch by remember { mutableStateOf(prefsForSettings.getBoolean("auto_launch_on_boot", false)) }
    var isPremiumUiEnabled by remember { mutableStateOf(prefsForSettings.getBoolean("premium_ui_enabled", false)) }

    LaunchedEffect(focusRestoreTrigger) {
        if (focusRestoreTrigger) {
            delay(200) 
            try { focusRequester.requestFocus() } catch (e: Exception) {}
            onFocusRestored()
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(top = 16.dp, start = 16.dp, end = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.GppGood, contentDescription = "Verified", tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "NexaPlay", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            val refreshInteraction = remember { MutableInteractionSource() }
            val isRefreshFocused by refreshInteraction.collectIsFocusedAsState()
            Icon(
                imageVector = Icons.Default.Refresh, contentDescription = "Refresh",
                tint = if (isRefreshFocused) AccentYellow else Color.White,
                modifier = Modifier.size(24.dp).clickable(interactionSource = refreshInteraction, indication = null) { onRefresh() }
            )
        }

        when (currentTab) {
            Tab.MATCHES -> {
                MatchesScreen(isTv = isTv, onPlayMatch = onPlay)
                return@Column
            }
            Tab.CATEGORIES -> {
                CategoriesScreen(
                    isTv = isTv, 
                    onPlay = onPlay, 
                    favoriteUrls = favoriteUrls, 
                    onToggleFav = onToggleFav,
                    lastFocusedUrl = lastFocusedUrl,
                    focusRequester = focusRequester,
                    onItemFocused = onItemFocused
                )
                return@Column
            }
            Tab.MORE -> {
                val view = LocalView.current
                
                val interactionSource1 = remember { MutableInteractionSource() }
                val isFocused1 by interactionSource1.collectIsFocusedAsState()
                var wasFocused1 by remember { mutableStateOf(false) }

                val interactionSource2 = remember { MutableInteractionSource() }
                val isFocused2 by interactionSource2.collectIsFocusedAsState()
                var wasFocused2 by remember { mutableStateOf(false) }

                Column(
                    // 🔥 টিভিতে প্যাডিং বেশি থাকবে, মোবাইলে কম
                    modifier = Modifier.fillMaxSize().padding(if (isTv) 32.dp else 16.dp),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.Start
                ) {
                    Text("Settings", color = AccentYellow, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(24.dp))

                    // 🔥 Auto-Play Switch
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        // 🔥 টিভিতে 60% জায়গা নিবে, মোবাইলে 100% নিবে
                        modifier = Modifier
                            .fillMaxWidth(if (isTv) 0.6f else 1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isFocused1) Color.White.copy(alpha = 0.2f) else CardBg)
                            .border(2.dp, if (isFocused1) AccentYellow else Color.Transparent, RoundedCornerShape(8.dp))
                            .onFocusChanged { state ->
                                if (state.isFocused && !wasFocused1) view.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN)
                                wasFocused1 = state.isFocused
                            }
                            .clickable(interactionSource = interactionSource1, indication = null) {
                                isAutoLaunch = !isAutoLaunch
                                prefsForSettings.edit().putBoolean("auto_launch_on_boot", isAutoLaunch).apply()
                            }
                            .focusable()
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Play on Launch", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Directly play the last channel when app starts", color = Color.Gray, fontSize = 12.sp)
                        }
                        Switch(
                            checked = isAutoLaunch, onCheckedChange = null,
                            colors = SwitchDefaults.colors(checkedThumbColor = AccentYellow, checkedTrackColor = AccentYellow.copy(alpha = 0.5f))
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 🔥 Premium UI Switch
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth(if (isTv) 0.6f else 1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isFocused2) Color.White.copy(alpha = 0.2f) else CardBg)
                            .border(2.dp, if (isFocused2) AccentYellow else Color.Transparent, RoundedCornerShape(8.dp))
                            .onFocusChanged { state ->
                                if (state.isFocused && !wasFocused2) view.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN)
                                wasFocused2 = state.isFocused
                            }
                            .clickable(interactionSource = interactionSource2, indication = null) {
                                isPremiumUiEnabled = !isPremiumUiEnabled
                                prefsForSettings.edit().putBoolean("premium_ui_enabled", isPremiumUiEnabled).apply()
                            }
                            .focusable()
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Premium Channels UI", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Enable dynamic categories & circular channel cards", color = Color.Gray, fontSize = 12.sp)
                        }
                        Switch(
                            checked = isPremiumUiEnabled, onCheckedChange = null,
                            colors = SwitchDefaults.colors(checkedThumbColor = AccentYellow, checkedTrackColor = AccentYellow.copy(alpha = 0.5f))
                        )
                    }
                }
                return@Column
            }
            else -> {}
        }
        
        if (!isTv) {
            val searchInteractionSource = remember { MutableInteractionSource() }
            val isSearchFocused by searchInteractionSource.collectIsFocusedAsState()
            val searchBorderColor = if (isSearchFocused) AccentYellow else Color.Transparent

            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).background(CardBg, RoundedCornerShape(8.dp))
                    .border(2.dp, searchBorderColor, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(text = if (currentTab == Tab.CHANNELS) "Search channels by name..." else "Search your favorites...", color = Color.Gray, fontSize = 14.sp)
                        }
                        BasicTextField(
                            value = searchQuery, onValueChange = onSearchQueryChange,
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            cursorBrush = SolidColor(AccentYellow), interactionSource = searchInteractionSource,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear Search", tint = Color.Gray, modifier = Modifier.size(18.dp).clickable { onSearchQueryChange("") })
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).background(CardBg, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    AndroidView(
                        factory = { ctx ->
                            EditText(ctx).apply {
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                setTextColor(android.graphics.Color.WHITE)
                                textSize = 14f
                                hint = "Search channels by name..."
                                setHintTextColor(android.graphics.Color.GRAY)
                                isSingleLine = true
                                imeOptions = EditorInfo.IME_ACTION_SEARCH
                                addTextChangedListener(object : TextWatcher {
                                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { onSearchQueryChange(s?.toString() ?: "") }
                                    override fun afterTextChanged(s: Editable?) {}
                                })
                            }
                        }, modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 🔥 গ্রুপ ফিল্টারের স্টেট 
        var selectedGroup by remember { mutableStateOf("All") }
        LaunchedEffect(currentTab) { selectedGroup = "All" } 

        val tabChannels = if (currentTab == Tab.FAVORITES) channels.filter { favoriteUrls.contains(it.url) } else channels
        val searchChannels = if (searchQuery.isEmpty()) tabChannels else tabChannels.filter { it.name.contains(searchQuery, ignoreCase = true) }

        val uniqueGroups = remember(searchChannels) {
            listOf("All") + searchChannels.map { it.group }.filter { it.isNotBlank() && it.uppercase() != "UNCATEGORIZED" }.distinct().sorted()
        }

        // 🔥 যদি Premium UI অন থাকে, তবে গ্রুপ অনুযায়ী ফিল্টার হবে, নাহলে সব চ্যানেল দেখাবে।
        val displayChannels = if (isPremiumUiEnabled && selectedGroup != "All") {
            searchChannels.filter { it.group.equals(selectedGroup, ignoreCase = true) }
        } else {
            searchChannels
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AccentYellow) }
        } else if (displayChannels.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text = "No channels found.", color = Color.Gray) }
        } else {
            Spacer(modifier = Modifier.height(8.dp)) 
            
            // 🔥 ক্যাটাগরি টপ বার (শুধুমাত্র Premium UI অন থাকলে দেখাবে)
            if (isPremiumUiEnabled && uniqueGroups.size > 1 && (currentTab == Tab.CHANNELS || currentTab == Tab.FAVORITES)) {
                val view = LocalView.current
                
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(uniqueGroups) { groupName ->
                        val isSelected = selectedGroup == groupName
                        var isGroupFocused by remember { mutableStateOf(false) }
                        var wasFocused by remember { mutableStateOf(false) }
                        
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (isSelected) AccentYellow else if (isGroupFocused) Color.White.copy(alpha = 0.2f) else CardBg)
                                .border(2.dp, if (isGroupFocused) Color.White else Color.Transparent, RoundedCornerShape(50))
                                .onFocusChanged { state -> 
                                    isGroupFocused = state.isFocused 
                                    if (state.isFocused && !wasFocused) view.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN)
                                    wasFocused = state.isFocused
                                }
                                .onKeyEvent { event ->
                                    if (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
                                        if (event.type == KeyEventType.KeyUp) { selectedGroup = groupName; true } else false
                                    } else false
                                }
                                .clickable { selectedGroup = groupName }
                                .focusable()
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Text(text = groupName, color = if (isSelected) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
            
            Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                if (currentTab == Tab.CHANNELS) {
                    // 🔥 Premium UI অন থাকলে ৬ কলাম (গোল কার্ড), নইলে আগের ৫ কলাম (স্কয়ার কার্ড)
                    val columns = if (isTv) {
                        GridCells.Fixed(if (isPremiumUiEnabled) 6 else 5)
                    } else {
                        GridCells.Adaptive(minSize = if (isPremiumUiEnabled) 120.dp else 150.dp)
                    }

                    LazyVerticalGrid(
                        columns = columns, 
                        state = gridState, 
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        contentPadding = PaddingValues(top = 0.dp, bottom = 100.dp), 
                        modifier = Modifier.fillMaxSize() 
                    ) {
                        itemsIndexed(items = displayChannels, key = { index, channel -> channel.url + index }) { index, channel ->
                            val isLastFocused = channel.url == lastFocusedUrl
                            
                            // 🔥 সুইচের উপর ভিত্তি করে কার্ডের ডিজাইন পরিবর্তন হবে
                            if (isPremiumUiEnabled) {
                                ChannelCircleCard( 
                                    channel = channel, isFavorite = favoriteUrls.contains(channel.url),
                                    onPlay = { onPlay(displayChannels, index) }, onToggleFav = { onToggleFav(channel.url) },
                                    isLastFocused = isLastFocused, focusRequester = focusRequester, onFocus = { onItemFocused(channel.url) }
                                )
                            } else {
                                ChannelGridCard( // আপনার পুরনো স্কয়ার কার্ড 
                                    channel = channel, isFavorite = favoriteUrls.contains(channel.url),
                                    onPlay = { onPlay(displayChannels, index) }, onToggleFav = { onToggleFav(channel.url) },
                                    isLastFocused = isLastFocused, focusRequester = focusRequester, onFocus = { onItemFocused(channel.url) }
                                )
                            }
                        }
                    }
                } else {                            
                    LazyColumn(
                        state = listState, 
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        contentPadding = PaddingValues(top = 0.dp, bottom = 100.dp), 
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(items = displayChannels, key = { index, channel -> channel.url + index }) { index, channel ->
                            val isLastFocused = channel.url == lastFocusedUrl
                            ChannelListCard(
                                channel = channel, isFavorite = favoriteUrls.contains(channel.url),
                                onPlay = { onPlay(displayChannels, index) }, onToggleFav = { onToggleFav(channel.url) },
                                isLastFocused = isLastFocused, focusRequester = focusRequester, onFocus = { onItemFocused(channel.url) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoriesScreen(
    isTv: Boolean, 
    onPlay: (List<Channel>, Int) -> Unit, 
    favoriteUrls: Set<String>, 
    onToggleFav: (String) -> Unit,
    lastFocusedUrl: String?,
    focusRequester: FocusRequester,
    onItemFocused: (String) -> Unit
) {
    val categoriesJsonUrl = "https://playlist-dts.vercel.app/categories.json" 
    
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var isLoadingCategories by remember { mutableStateOf(true) }

    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var categoryChannels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var isChannelsLoading by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val categoryGridState = rememberLazyGridState()
    val channelGridState = rememberLazyGridState()
    
    var lastFocusedCategoryName by remember { mutableStateOf<String?>(null) }
    val categoryFocusRequester = remember { FocusRequester() }
    var categoryFocusTrigger by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (categories.isEmpty()) {
            categories = CategoryFetcher.fetchCategories(categoriesJsonUrl)
            isLoadingCategories = false
        }
    }

    BackHandler(enabled = selectedCategory != null) {
        selectedCategory = null
        categoryFocusTrigger = true 
    }

    LaunchedEffect(categoryFocusTrigger) {
        if (categoryFocusTrigger) {
            delay(200)
            try { categoryFocusRequester.requestFocus() } catch (e: Exception) {}
            categoryFocusTrigger = false
        }
    }

    if (selectedCategory == null) {
        if (isLoadingCategories) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentYellow, strokeWidth = 4.dp, modifier = Modifier.size(56.dp))
            }
        } else if (categories.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No categories found.", color = Color.Gray)
            }
        } else {
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                val columns = if (isTv) GridCells.Fixed(4) else GridCells.Adaptive(minSize = 140.dp)
                LazyVerticalGrid(
                    columns = columns,
                    state = categoryGridState,
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    contentPadding = PaddingValues(top = 0.dp, bottom = 100.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(categories.size) { index ->
                        val category = categories[index]
                        val isLastFocused = category.name == lastFocusedCategoryName
                        
                        CategoryCard(
                            category = category, 
                            isLastFocused = isLastFocused,
                            focusRequester = categoryFocusRequester,
                            onFocus = { lastFocusedCategoryName = category.name },
                            onClick = {
                                selectedCategory = category
                                isChannelsLoading = true 
                                scope.launch {
                                    try {
                                        categoryChannels = withContext(Dispatchers.IO) {
                                            M3uParser.fetchChannels(category.m3uUrl)
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    } finally {
                                        isChannelsLoading = false 
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedCategory!!.name,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            if (isChannelsLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentYellow, strokeWidth = 4.dp, modifier = Modifier.size(56.dp))
                }
            } else if (categoryChannels.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No channels found in this category.", color = Color.Gray)
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                
                // 🔥 ক্যাটাগরির ভেতরের সাব-গ্রুপ লজিক
                var selectedCatGroup by remember { mutableStateOf("All") }
                val catGroups = remember(categoryChannels) {
                    listOf("All") + categoryChannels.map { it.group }.filter { it.isNotBlank() && it.uppercase() != "UNCATEGORIZED" }.distinct().sorted()
                }
                val displayCatChannels = if (selectedCatGroup == "All") categoryChannels else categoryChannels.filter { it.group.equals(selectedCatGroup, ignoreCase = true) }

                // 🔥 সাব-গ্রুপ টপ বার (সাউন্ড সহ)
                if (catGroups.size > 1) {
                    val view = LocalView.current // 🔥 সাউন্ডের জন্য View নেওয়া হলো
                    
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(catGroups) { groupName ->
                            val isSelected = selectedCatGroup == groupName
                            var isGroupFocused by remember { mutableStateOf(false) }
                            var wasFocused by remember { mutableStateOf(false) } // 🔥 সাউন্ড ট্র্যাক করার জন্য
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (isSelected) AccentYellow else if (isGroupFocused) Color.White.copy(alpha = 0.2f) else CardBg)
                                    .border(2.dp, if (isGroupFocused) Color.White else Color.Transparent, RoundedCornerShape(50))
                                    .onFocusChanged { state -> 
                                        isGroupFocused = state.isFocused 
                                        // 🔥 ফোকাস আসলেই সাউন্ড হবে
                                        if (state.isFocused && !wasFocused) {
                                            view.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN)
                                        }
                                        wasFocused = state.isFocused
                                    }
                                    .onKeyEvent { event ->
                                        if (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
                                            if (event.type == KeyEventType.KeyUp) { selectedCatGroup = groupName; true } else false
                                        } else false
                                    }
                                    .clickable { selectedCatGroup = groupName }
                                    .focusable()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(text = groupName, color = if (isSelected) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                    val isThumb = selectedCategory!!.types == "thumb"
                    
                    val columns = if (isTv) {
                        GridCells.Fixed(if (isThumb) 4 else 6) 
                    } else {
                        if (isThumb) GridCells.Fixed(2) else GridCells.Adaptive(minSize = 120.dp)
                    }
                    
                    LazyVerticalGrid(
                        columns = columns,
                        state = channelGridState,
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        contentPadding = PaddingValues(top = 0.dp, bottom = 100.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(items = displayCatChannels, key = { index, channel -> channel.url + index }) { index, channel ->
                            val isLastFocusedChannel = channel.url == lastFocusedUrl
                            
                            if (isThumb) {
                                ChannelThumbCard(
                                    channel = channel,
                                    onPlay = { onPlay(displayCatChannels, index) },
                                    isLastFocused = isLastFocusedChannel,
                                    focusRequester = focusRequester,
                                    onFocus = { onItemFocused(channel.url) }
                                )
                            } else {
                                ChannelCircleCard( 
                                    channel = channel,
                                    isFavorite = favoriteUrls.contains(channel.url),
                                    onPlay = { onPlay(displayCatChannels, index) },
                                    onToggleFav = { onToggleFav(channel.url) },
                                    isLastFocused = isLastFocusedChannel,
                                    focusRequester = focusRequester,
                                    onFocus = { onItemFocused(channel.url) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}       
@Composable
fun CategoryCard(
    category: Category, 
    isLastFocused: Boolean,
    focusRequester: FocusRequester,
    onFocus: () -> Unit,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val view = LocalView.current
    var wasFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(targetValue = if (isFocused) 1.05f else 1f, label = "scale")
    LaunchedEffect(isFocused) { if (isFocused) onFocus() }
    val modifier = if (isLastFocused) Modifier.focusRequester(focusRequester) else Modifier

    Column(
        modifier = modifier
            .onFocusChanged { state ->
                if (state.isFocused && !wasFocused) {
                    view.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN)
                }
                wasFocused = state.isFocused
            }
            .onKeyEvent { event ->
                if (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
                    if (event.type == KeyEventType.KeyUp) {
                        onClick()
                        true
                    } else false
                } else false
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable()
            .padding(8.dp) 
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(if (isFocused) 3.dp else 0.dp, if (isFocused) AccentYellow else Color.Transparent, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = category.logo,
            contentDescription = category.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = category.name,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MatchesScreen(isTv: Boolean, onPlayMatch: (List<Channel>, Int) -> Unit) {
    // 🔥 আপনার আসল JSON লিংকটি এখানে দেবেন
    val matchesJsonUrl = "https://piyashltd.github.io/all-in-one/matches.json" 
    
    var matches by remember { mutableStateOf<List<Match>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        matches = MatchFetcher.fetchMatches(matchesJsonUrl)
        isLoading = false
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AccentYellow)
        }
    } else if (matches.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No upcoming matches found.", color = Color.Gray)
        }
    } else {
        val columns = if (isTv) GridCells.Fixed(3) else GridCells.Adaptive(minSize = 280.dp)
        LazyVerticalGrid(
            columns = columns,
            contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(matches.size) { index ->
                MatchClassicCard(match = matches[index]) {
                    // Match কে Channel মডেলে কনভার্ট করে প্লেয়ারে পাঠানো হচ্ছে (সংশোধিত অংশ)
                    val matchChannel = Channel(
                        name = "${matches[index].team1Name} vs ${matches[index].team2Name}",
                        group = "Matches",                                   // 'category'-এর বদলে 'group'
                        url = matches[index].streamUrls.firstOrNull() ?: "",
                        urls = matches[index].streamUrls.toMutableList(),  // '.toMutableList()' যুক্ত করা হয়েছে
                        logo = matches[index].leagueLogo
                    )
                    onPlayMatch(listOf(matchChannel), 0)
                }
            }
        }
    }
}

@Composable
fun MatchClassicCard(match: Match, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val view = LocalView.current // 🔥 সাউন্ডের জন্য View
    var wasFocused by remember { mutableStateOf(false) } // 🔥 সাউন্ড ট্র্যাক করার জন্য ভেরিয়েবল
    
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.05f else 1f, label = "scale")
    
    var statusText by remember { mutableStateOf("") }
    var isLive by remember { mutableStateOf(false) }
    var isEnded by remember { mutableStateOf(false) }

    LaunchedEffect(match.startTime, match.endTime) {
        try {
            val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault())
            format.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val start = format.parse(match.startTime)?.time ?: 0L
            val end = format.parse(match.endTime)?.time ?: 0L
            val now = System.currentTimeMillis()

            when {
                now in start..end -> { isLive = true; statusText = "LIVE NOW" }
                now > end -> {
                    isEnded = true
                    val diffMins = (now - end) / (1000 * 60)
                    statusText = if (diffMins > 60) "Ended ${diffMins / 60}h ago" else "Ended ${diffMins}m ago"
                }
                else -> {
                    val diffMins = (start - now) / (1000 * 60)
                    statusText = if (diffMins > 60) "Starting in ${diffMins / 60}h ${diffMins % 60}m" else "Starting in ${diffMins}m"
                }
            }
        } catch (e: Exception) { statusText = "TBA" }
    }

    Card(
        modifier = Modifier
            .padding(12.dp)
            .fillMaxWidth()
            .scale(scale)
            .border(width = if (isFocused) 2.dp else 0.dp, color = if (isFocused) AccentYellow else Color.Transparent, shape = RoundedCornerShape(16.dp))
            .onFocusChanged { state -> // 🔥 ফোকাস সাউন্ড লজিক
                if (state.isFocused && !wasFocused) {
                    view.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN)
                }
                wasFocused = state.isFocused
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = match.leagueLogo, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = match.leagueName, color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    AsyncImage(model = match.team1Flag, contentDescription = match.team1Name, modifier = Modifier.size(48.dp).clip(CircleShape))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = match.team1Name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Text(text = "VS", color = AccentYellow, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    AsyncImage(model = match.team2Flag, contentDescription = match.team2Name, modifier = Modifier.size(48.dp).clip(CircleShape))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = match.team2Name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            
            val badgeBg = if (isLive) Color.Red.copy(alpha = 0.2f) else if (isEnded) Color.Gray.copy(alpha = 0.2f) else AccentYellow.copy(alpha = 0.1f)
            val badgeColor = if (isLive) Color.Red else if (isEnded) Color.Gray else AccentYellow
            
            Row(
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(badgeBg).border(1.dp, badgeColor.copy(alpha = 0.5f), RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLive) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Red))
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(text = statusText, color = badgeColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelThumbCard(
    channel: Channel, onPlay: () -> Unit,
    isLastFocused: Boolean, focusRequester: FocusRequester, onFocus: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val view = LocalView.current
    var wasFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(targetValue = if (isFocused) 1.05f else 1f, label = "scale")
    LaunchedEffect(isFocused) { if (isFocused) onFocus() }
    val modifier = if (isLastFocused) Modifier.focusRequester(focusRequester) else Modifier

    Card(
        modifier = modifier
            .onFocusChanged { state ->
                if (state.isFocused && !wasFocused) {
                    view.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN)
                }
                wasFocused = state.isFocused
            }
            .onKeyEvent { event ->
                if (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
                    if (event.type == KeyEventType.KeyUp) {
                        onPlay()
                        true
                    } else false
                } else false
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onPlay)
            .focusable() 
            .padding(8.dp) 
            .fillMaxWidth()
            .scale(scale)
            .aspectRatio(16f / 9f) 
            .clip(RoundedCornerShape(6.dp)) 
            .border(if (isFocused) 3.dp else 0.dp, if (isFocused) AccentYellow else Color.Transparent, RoundedCornerShape(6.dp)),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = channel.logo, 
                contentDescription = channel.name,
                contentScale = ContentScale.Crop, 
                modifier = Modifier.fillMaxSize()
            )
            
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.9f) 
                            )
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp) 
            ) {
                Text(
                    text = channel.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis, 
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelGridCard(
    channel: Channel, isFavorite: Boolean, onPlay: () -> Unit, onToggleFav: () -> Unit,
    isLastFocused: Boolean, focusRequester: FocusRequester, onFocus: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    var isLongPressHandled by remember { mutableStateOf(false) }
    val view = LocalView.current
    var wasFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        label = "focus_scale"
    )

    LaunchedEffect(isFocused) { if (isFocused) onFocus() }
    val modifier = if (isLastFocused) Modifier.focusRequester(focusRequester) else Modifier

    Column(
        modifier = modifier
            .onFocusChanged { state ->
                if (state.isFocused && !wasFocused) {
                    view.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN)
                }
                wasFocused = state.isFocused
            }
            .onKeyEvent { event ->
                if (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            if (event.nativeKeyEvent.repeatCount > 0) {
                                if (!isLongPressHandled) { isLongPressHandled = true; onToggleFav() }
                                true 
                            } else false
                        }
                        KeyEventType.KeyUp -> { 
                            if (isLongPressHandled) { isLongPressHandled = false; true } else { onPlay(); true }
                        }
                        else -> false
                    }
                } else false
            }
            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = onPlay, onLongClick = onToggleFav)
            .focusable() 
            .padding(8.dp) 
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(
                width = if (isFocused) 3.dp else 0.dp, 
                color = if (isFocused) AccentYellow else Color.Transparent, 
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Color.White)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = channel.logo, 
                contentDescription = channel.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp))
            )
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2A273F))
                .padding(vertical = 12.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = channel.name, 
                color = Color.White, 
                fontWeight = FontWeight.Bold, 
                fontSize = 14.sp, 
                maxLines = 1, 
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelListCard(
    channel: Channel, isFavorite: Boolean, onPlay: () -> Unit, onToggleFav: () -> Unit,
    isLastFocused: Boolean, focusRequester: FocusRequester, onFocus: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    var isLongPressHandled by remember { mutableStateOf(false) }
    val view = LocalView.current
    var wasFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        label = "focus_scale"
    )

    LaunchedEffect(isFocused) { if (isFocused) onFocus() }
    val modifier = if (isLastFocused) Modifier.focusRequester(focusRequester) else Modifier

    Row(
        modifier = modifier
            .onFocusChanged { state ->
                if (state.isFocused && !wasFocused) {
                    view.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN)
                }
                wasFocused = state.isFocused
            }
            .onKeyEvent { event ->
                if (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            if (event.nativeKeyEvent.repeatCount > 0) {
                                if (!isLongPressHandled) { isLongPressHandled = true; onToggleFav() }
                                true
                            } else false
                        }
                        KeyEventType.KeyUp -> { if (isLongPressHandled) { isLongPressHandled = false; true } else { onPlay(); true } }
                        else -> false
                    }
                } else false
            }
            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = onPlay, onLongClick = onToggleFav)
            .focusable()
            .padding(8.dp)
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(if (isFocused) 3.dp else 0.dp, if (isFocused) AccentYellow else Color.Transparent, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(Color.White, RoundedCornerShape(8.dp))
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = channel.logo, 
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp))
            )
        }
        
        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(
                text = channel.name, 
                color = Color.White, 
                fontWeight = FontWeight.Bold, 
                fontSize = 16.sp, 
                maxLines = 1, 
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelCircleCard(
    channel: Channel, isFavorite: Boolean, onPlay: () -> Unit, onToggleFav: () -> Unit,
    isLastFocused: Boolean, focusRequester: FocusRequester, onFocus: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    var isLongPressHandled by remember { mutableStateOf(false) }
    val view = LocalView.current
    var wasFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.12f else 1f, // ফোকাস হলে কার্ডটা সুন্দরভাবে বড় হবে
        label = "focus_scale"
    )

    LaunchedEffect(isFocused) { if (isFocused) onFocus() }
    val modifier = if (isLastFocused) Modifier.focusRequester(focusRequester) else Modifier

    Column(
        modifier = modifier
            .onFocusChanged { state ->
                if (state.isFocused && !wasFocused) {
                    view.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN)
                }
                wasFocused = state.isFocused
            }
            .onKeyEvent { event ->
                if (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            if (event.nativeKeyEvent.repeatCount > 0) {
                                if (!isLongPressHandled) { isLongPressHandled = true; onToggleFav() }
                                true 
                            } else false
                        }
                        KeyEventType.KeyUp -> { 
                            if (isLongPressHandled) { isLongPressHandled = false; true } else { onPlay(); true }
                        }
                        else -> false
                    }
                } else false
            }
            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = onPlay, onLongClick = onToggleFav)
            .focusable() 
            .padding(10.dp) 
            .fillMaxWidth()
            .scale(scale),
        horizontalAlignment = Alignment.CenterHorizontally 
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .aspectRatio(1f) // একদম পারফেক্ট গোল করার জন্য
                .clip(CircleShape)
                .background(Color.White)
                .border(
                    width = if (isFocused) 4.dp else 0.dp, 
                    color = if (isFocused) AccentYellow else Color.Transparent, 
                    shape = CircleShape
                )
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = channel.logo, 
                contentDescription = channel.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        Text(
            text = channel.name, 
            color = Color.White, 
            fontWeight = FontWeight.Bold, 
            fontSize = 13.sp, 
            maxLines = 1, 
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

data class BannerItem(
    val imageUrl: String,
    val redirectUrl: String,
    val durationSeconds: Int
)

object BannerFetcher {
    suspend fun getBanners(jsonUrl: String): List<BannerItem> = withContext(Dispatchers.IO) {
        val banners = mutableListOf<BannerItem>()
        try {
            val connection = URL(jsonUrl).openConnection() as HttpsURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val jsonArray = jsonObject.getJSONArray("banners")
            
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                banners.add(
                    BannerItem(
                        imageUrl = item.getString("imageUrl"),
                        redirectUrl = item.getString("redirectUrl"),
                        durationSeconds = item.getInt("durationSeconds")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext banners
    }
}

@Composable
fun AppBannerPopup(jsonUrl: String) {
    val context = LocalContext.current
    var banners by remember { mutableStateOf<List<BannerItem>>(emptyList()) }
    var currentBannerIndex by remember { mutableStateOf(-1) }
    var isVisible by remember { mutableStateOf(false) }
    var timeLeft by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        banners = BannerFetcher.getBanners(jsonUrl)
        if (banners.isNotEmpty()) {
            isVisible = true
            for (i in banners.indices) {
                if (!isVisible) break 
                currentBannerIndex = i
                timeLeft = banners[i].durationSeconds
                
                while (timeLeft > 0 && isVisible) {
                    delay(1000L)
                    timeLeft--
                }
            }
            isVisible = false 
        }
    }

    if (isVisible && currentBannerIndex in banners.indices) {
        val currentBanner = banners[currentBannerIndex]

        Dialog(
            onDismissRequest = { },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = false,    
                dismissOnClickOutside = false  
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(if (LocalContext.current.resources.configuration.screenWidthDp > 600) 0.6f else 0.9f)
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.Start 
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFACC15), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Wait for ${timeLeft}s",
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }                        

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (LocalContext.current.resources.configuration.screenWidthDp > 600) 0.6f else 0.9f)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF211F30))
                            .clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentBanner.redirectUrl))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                    ) {
                        AsyncImage(
                            model = currentBanner.imageUrl,
                            contentDescription = "Promo Banner",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// Ad System Classes
// ==========================================

data class AdConfig(
    val adsEnabled: Boolean = false,
    val adUrl: String = "",
    val maxAdsPerDay: Int = 3
)

object AdConfigFetcher {
    suspend fun fetchConfig(jsonUrl: String): AdConfig = withContext(Dispatchers.IO) {
        try {
            val connection = URL(jsonUrl).openConnection() as HttpsURLConnection
            connection.connectTimeout = 10000
            val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            return@withContext AdConfig(
                adsEnabled = jsonObject.optBoolean("adsEnabled", false),
                adUrl = jsonObject.optString("adUrl", ""),
                maxAdsPerDay = jsonObject.optInt("maxAdsPerDay", 3)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext AdConfig()
        }
    }
}

class AdManager(context: Context) {
    private val prefs = context.getSharedPreferences("kobra_ads_prefs", Context.MODE_PRIVATE)

    fun shouldShowAd(maxAds: Int): Boolean {
        val todayDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val lastAdDate = prefs.getString("last_ad_date", "")
        var currentCount = prefs.getInt("ad_click_count", 0)

        if (todayDate != lastAdDate) {
            currentCount = 0
            prefs.edit().putString("last_ad_date", todayDate).putInt("ad_click_count", 0).apply()
        }
        return currentCount < maxAds
    }

    fun markAdShown() {
        val currentCount = prefs.getInt("ad_click_count", 0)
        prefs.edit().putInt("ad_click_count", currentCount + 1).apply()
    }
}
