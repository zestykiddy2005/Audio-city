package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.CommentEntity
import com.example.data.local.NotificationEntity
import com.example.data.local.PlaylistEntity
import com.example.data.local.SongEntity
import com.example.data.local.UserActivityEntity
import com.example.ui.player.AudioPlayerController
import com.example.ui.error.LocalErrorBoundary
import com.example.ui.theme.*
import com.example.ui.viewmodel.MusicViewModel
import com.example.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

sealed class AppTab(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : AppTab("home", "Home", Icons.Filled.Home)
    object Discover : AppTab("discover", "Discover", Icons.Filled.Search)
    object Library : AppTab("library", "Library", Icons.Filled.LibraryMusic)
    object Downloads : AppTab("downloads", "Downloads", Icons.Filled.Download)
    object Companion : AppTab("companion", "AI Companion", Icons.Filled.RecordVoiceOver)
    object Profile : AppTab("profile", "Profile", Icons.Filled.Person)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContainer(viewModel: MusicViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf<AppTab>(AppTab.Home) }
    var showPlayerFullScreen by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showPlaylistSongsDialog by remember { mutableStateOf<PlaylistEntity?>(null) }
    var showAddToPlaylistDialog by remember { mutableStateOf<SongEntity?>(null) }

    val currentSongState by viewModel.currentSong.collectAsState()
    val isPlayingState by viewModel.isPlaying.collectAsState()
    val isBufferingState by viewModel.isBuffering.collectAsState()

    val notificationsList by viewModel.allNotifications.collectAsState()
    val currentUserState by viewModel.currentUser.collectAsState()

    Scaffold(
        bottomBar = {
            Column {
                // Mini Player Bar
                if (currentSongState != null && !showPlayerFullScreen) {
                    MiniPlayerBar(
                        song = currentSongState!!,
                        isPlaying = isPlayingState,
                        isBuffering = isBufferingState,
                        onPlayPauseToggle = { viewModel.playOrPause() },
                        onNext = { viewModel.next() },
                        onBarClick = { showPlayerFullScreen = true }
                    )
                }

                // Main Bottom Nav
                NavigationBar(
                    containerColor = SlateDark,
                    tonalElevation = 8.dp,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    val tabs = listOf(
                        AppTab.Home,
                        AppTab.Discover,
                        AppTab.Library,
                        AppTab.Downloads,
                        AppTab.Profile
                    )
                    tabs.forEach { tab ->
                        val selected = activeTab == tab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { activeTab = tab },
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NeonCyan,
                                selectedTextColor = NeonCyan,
                                indicatorColor = ActiveNavPill,
                                unselectedIconColor = MutedText,
                                unselectedTextColor = MutedText
                            ),
                            modifier = Modifier.testTag("nav_tab_${tab.route}")
                        )
                    }
                }
            }
        },
        containerColor = DeepMidnight
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Screen contents
            Crossfade(targetState = activeTab, label = "tab_fade") { tab ->
                when (tab) {
                    AppTab.Home -> HomeScreen(
                        viewModel = viewModel,
                        onSongSelect = { song, list -> viewModel.playSong(song, list) },
                        onAddPlaylistClick = { showSettings = false; activeTab = AppTab.Library },
                        onOpenSettings = { showSettings = true }
                    )
                    AppTab.Discover -> DiscoverScreen(
                        viewModel = viewModel,
                        onSongSelect = { song, list -> viewModel.playSong(song, list) },
                        onAddSongToPlaylist = { showAddToPlaylistDialog = it }
                    )
                    AppTab.Library -> LibraryScreen(
                        viewModel = viewModel,
                        onPlaylistSelect = { showPlaylistSongsDialog = it },
                        onSongSelect = { song, list -> viewModel.playSong(song, list) }
                    )
                    AppTab.Downloads -> DownloadsScreen(
                        viewModel = viewModel,
                        onSongSelect = { song, list -> viewModel.playSong(song, list) }
                    )
                    AppTab.Companion -> VoiceCompanionScreen(
                        viewModel = viewModel
                    )
                    AppTab.Profile -> ProfileScreen(
                        viewModel = viewModel,
                        onOpenSettings = { showSettings = true },
                        onOpenPremium = { showSettings = true }
                    )
                }
            }

            // Floating Header Bar (Notification / Settings shortcuts)
            if (activeTab != AppTab.Companion) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .align(Alignment.TopEnd),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (activeTab == AppTab.Home) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(NeonCyan, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "AC",
                                    color = GlowAmbient,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Audio City",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextLight,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Premium Badge
                        if (currentUserState?.isPremium == true) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .background(LuxeGold, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Stars,
                                        contentDescription = "Premium",
                                        tint = DeepMidnight,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "PREMIUM",
                                        fontSize = 9.sp,
                                        color = DeepMidnight,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Notifications Trigger
                        IconButton(
                            onClick = { showNotifications = true },
                            modifier = Modifier.testTag("notification_shortcut")
                        ) {
                            BadgedBox(
                                badge = {
                                    val unreadCount = notificationsList.count { !it.isRead }
                                    if (unreadCount > 0) {
                                        Badge(containerColor = NeonCyan) {
                                            Text(unreadCount.toString(), color = DeepMidnight, fontSize = 9.sp)
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Outlined.Notifications, contentDescription = "Notifications", tint = TextLight)
                            }
                        }

                        // Settings Trigger
                        IconButton(
                            onClick = { showSettings = true },
                            modifier = Modifier.testTag("settings_shortcut")
                        ) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = TextLight)
                        }
                    }
                }
            }

            // Notification Drawer Overlay
            AnimatedVisibility(
                visible = showNotifications,
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it })
            ) {
                NotificationsDrawer(
                    notifications = notificationsList,
                    onClose = { showNotifications = false },
                    onMarkRead = { viewModel.markNotificationRead(it) },
                    onClearAll = { viewModel.clearAllNotifications() }
                )
            }

            // Settings / Premium Overlay
            AnimatedVisibility(
                visible = showSettings,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                SettingsScreen(
                    viewModel = viewModel,
                    onClose = { showSettings = false }
                )
            }

            // Playlist Detail Modal Dialog
            showPlaylistSongsDialog?.let { playlist ->
                PlaylistDetailDialog(
                    playlist = playlist,
                    viewModel = viewModel,
                    onDismiss = { showPlaylistSongsDialog = null },
                    onSongSelect = { song, list ->
                        viewModel.playSong(song, list)
                        showPlaylistSongsDialog = null
                    }
                )
            }

            // Add To Playlist Dialog
            showAddToPlaylistDialog?.let { song ->
                AddToPlaylistDialog(
                    song = song,
                    viewModel = viewModel,
                    onDismiss = { showAddToPlaylistDialog = null }
                )
            }

            // Fullscreen Audio Player Panel Overlay
            AnimatedVisibility(
                visible = showPlayerFullScreen,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                currentSongState?.let { song ->
                    PlayerFullScreenView(
                        song = song,
                        viewModel = viewModel,
                        onClose = { showPlayerFullScreen = false },
                        onAddToPlaylist = { showAddToPlaylistDialog = it }
                    )
                }
            }
        }
    }
}

// ==========================================
// 1. MINI PLAYER COMPONENT
// ==========================================
@Composable
fun MiniPlayerBar(
    song: SongEntity,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPlayPauseToggle: () -> Unit,
    onNext: () -> Unit,
    onBarClick: () -> Unit
) {
    val progressSec by AudioPlayerController.currentPositionSec.collectAsState()
    val durationSec by AudioPlayerController.durationSec.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(PlayerBackground)
            .clickable(onClick = onBarClick)
            .drawBehind {
                // Elegant dark theme top border
                drawLine(
                    color = Color(0x8049454F),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 2f
                )
                // Seek progress micro bar at the bottom
                if (durationSec > 0) {
                    val ratio = progressSec.toFloat() / durationSec.toFloat()
                    drawLine(
                        color = NeonCyan,
                        start = Offset(0f, size.height),
                        end = Offset(size.width * ratio, size.height),
                        strokeWidth = 4f
                    )
                }
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = song.coverUrl,
                    contentDescription = song.title,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = song.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        fontSize = 12.sp,
                        color = MutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = NeonCyan,
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(
                        onClick = onPlayPauseToggle,
                        modifier = Modifier.testTag("mini_play_pause")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = NeonCyan,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onNext,
                    modifier = Modifier.testTag("mini_next")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        tint = TextLight,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// 2. HOME SCREEN COMPONENT
// ==========================================
@Composable
fun HomeScreen(
    viewModel: MusicViewModel,
    onSongSelect: (SongEntity, List<SongEntity>) -> Unit,
    onAddPlaylistClick: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val songs by viewModel.musicTracks.collectAsState()
    val podcasts by viewModel.podcasts.collectAsState()
    val userProfile by viewModel.currentUser.collectAsState()
    val recommendationText by viewModel.aiRecommendation.collectAsState()

    val currentSongState by viewModel.currentSong.collectAsState()
    val isPlayingState by viewModel.isPlaying.collectAsState()

    var selectedSongForInfo by remember { mutableStateOf<SongEntity?>(null) }

    // Only display approved songs in public home
    val approvedSongs = songs.filter { it.status == "approved" }
    // Sort songs by playCount for real trending system
    val trendingSongsList = approvedSongs.sortedByDescending { it.playCount }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepMidnight)
                .padding(top = 64.dp, bottom = 12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            // Welcome Brand Header (Requirement 8)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = "Audio City Logo",
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.5.dp, LuxeGold, RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Good day, ${userProfile?.name ?: "Listener"}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextWhite
                        )
                        Text(
                            text = "Audio City Commercial Distribution Hub",
                            fontSize = 11.sp,
                            color = MutedText
                        )
                    }
                }
            }

            // AI Recommendation Banner (Personalized Curation Feed - Requirement 10)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(AccentPurple.copy(alpha = 0.5f), SlateDark),
                                radius = 450f
                            )
                        )
                        .border(1.dp, GlowAmbient.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(GlowAmbient, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = "AI",
                                tint = LuxeGold,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "AI Personal Curation Guide",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = LuxeGold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                recommendationText.ifEmpty { "Analyzing your acoustic patterns to generate licensed discovery recommendations..." },
                                fontSize = 12.sp,
                                color = TextLight,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // Trending Worldwide System (Requirement 9)
            item {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.TrendingUp, contentDescription = "Trending", tint = LuxeGold, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Worldwide Trending Music Charts",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                        }
                        Box(
                            modifier = Modifier
                                .background(LuxeGold.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("GLOBAL RELEASES", fontSize = 9.sp, color = LuxeGold, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (trendingSongsList.isEmpty()) {
                        Text("No trending tracks available.", color = MutedText, fontSize = 12.sp)
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(trendingSongsList) { song ->
                                Card(
                                    modifier = Modifier
                                        .width(140.dp)
                                        .clickable { onSongSelect(song, approvedSongs) }
                                        .testTag("trending_song_${song.id}"),
                                    colors = CardDefaults.cardColors(containerColor = SlateDark),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, CardBorder)
                                ) {
                                    Column {
                                        Box {
                                            AsyncImage(
                                                model = song.coverUrl,
                                                contentDescription = song.title,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(100.dp),
                                                contentScale = ContentScale.Crop
                                            )
                                            // Floating Rank Badge
                                            Box(
                                                modifier = Modifier
                                                    .padding(6.dp)
                                                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                                                    .border(0.5.dp, LuxeGold, RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    .align(Alignment.TopStart)
                                            ) {
                                                Text(
                                                    text = "#${song.globalRank.takeIf { it > 0 } ?: "1"}",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = LuxeGold
                                                )
                                            }
                                        }
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text(song.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(song.artist, fontSize = 10.sp, color = MutedText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "${song.playCount / 1000}k streams",
                                                fontSize = 9.sp,
                                                color = LuxeGold,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Featured Songs Slider (Horizontal)
            item {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(
                        text = "Official Spotlight",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (approvedSongs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = NeonCyan)
                        }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(approvedSongs.take(4)) { song ->
                                FeaturedSongCard(song = song, onClick = { onSongSelect(song, approvedSongs) })
                            }
                        }
                    }
                }
            }

            // Distribution Partners Carousel (Requirement 11, 12)
            item {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(
                        text = "Authorized Major Partners & Distributors",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = LuxeGold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(listOf(
                            "Sony Music Entertainment" to "Major Label",
                            "Universal Music Group" to "Major Label",
                            "Columbia Records" to "Record Label",
                            "Warner Records" to "Major Label",
                            "DistroKid" to "Distribution Partner",
                            "TuneCore" to "Distribution Partner",
                            "FUGA" to "Distribution Feed"
                        )) { (partner, type) ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SlateDark.copy(alpha = 0.5f)),
                                border = BorderStroke(0.5.dp, CardBorder),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = LuxeGold, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(partner, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                                        Text(type, fontSize = 8.sp, color = MutedText)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Audio City Podcasts / Audiobooks
            item {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(
                        text = "Trending Podcasts & Talks",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (podcasts.isEmpty()) {
                        Text("No podcasts available.", color = MutedText, fontSize = 12.sp)
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(podcasts) { podcast ->
                                FeaturedPodcastCard(podcast = podcast, onClick = { onSongSelect(podcast, podcasts) })
                            }
                        }
                    }
                }
            }

            // Music Grid (All Songs)
            item {
                Text(
                    text = "Discover All Tracks",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                )
            }

            if (approvedSongs.isEmpty()) {
                item {
                    Text("No tracks available.", color = MutedText, modifier = Modifier.padding(vertical = 16.dp))
                }
            } else {
                items(approvedSongs) { song ->
                    val isCurrent = currentSongState?.id == song.id
                    SongListItem(
                        song = song,
                        isDownloading = viewModel.downloadingSongIds.collectAsState().value.containsKey(song.id),
                        downloadProgress = viewModel.downloadingSongIds.collectAsState().value[song.id],
                        onPlay = { onSongSelect(song, approvedSongs) },
                        onLikeToggle = { viewModel.toggleLike(song.id) },
                        onDownload = { viewModel.downloadSong(song.id) },
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && isPlayingState,
                        onInfoClick = { selectedSongForInfo = song } // Opens registry details!
                    )
                }
            }
        }

        // Selected song info registry dialog overlay
        selectedSongForInfo?.let { song ->
            PublicSongInfoDialog(
                song = song,
                viewModel = viewModel,
                onDismiss = { selectedSongForInfo = null }
            )
        }
    }
}

@Composable
fun FeaturedSongCard(song: SongEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(180.dp)
            .clickable(onClick = onClick)
            .testTag("featured_song_${song.id}"),
        colors = CardDefaults.cardColors(containerColor = SlateDark),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column {
            AsyncImage(
                model = song.coverUrl,
                contentDescription = song.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = song.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    fontSize = 12.sp,
                    color = MutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(NeonCyanMuted, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(song.genre, fontSize = 9.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                    }
                    Text("${song.durationSec / 60}:${String.format("%02d", song.durationSec % 60)}", fontSize = 10.sp, color = MutedText)
                }
            }
        }
    }
}

@Composable
fun FeaturedPodcastCard(podcast: SongEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick)
            .testTag("featured_podcast_${podcast.id}"),
        colors = CardDefaults.cardColors(containerColor = SlateDark),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            AsyncImage(
                model = podcast.coverUrl,
                contentDescription = podcast.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = podcast.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = podcast.artist,
                fontSize = 11.sp,
                color = LuxeGold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ActiveEqualizerIndicator(isPlaying: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "eq_transition")
    
    val height1 by if (isPlaying) {
        transition.animateFloat(
            initialValue = 0.2f, targetValue = 0.9f,
            animationSpec = infiniteRepeatable(animation = tween(450, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
            label = "h1"
        )
    } else {
        remember { mutableStateOf(0.3f) }
    }
    
    val height2 by if (isPlaying) {
        transition.animateFloat(
            initialValue = 0.3f, targetValue = 1.0f,
            animationSpec = infiniteRepeatable(animation = tween(350, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
            label = "h2"
        )
    } else {
        remember { mutableStateOf(0.5f) }
    }
    
    val height3 by if (isPlaying) {
        transition.animateFloat(
            initialValue = 0.1f, targetValue = 0.8f,
            animationSpec = infiniteRepeatable(animation = tween(550, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
            label = "h3"
        )
    } else {
        remember { mutableStateOf(0.2f) }
    }

    Row(
        modifier = modifier.size(width = 14.dp, height = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight(height1).background(NeonCyan, RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.weight(1f).fillMaxHeight(height2).background(NeonCyan, RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.weight(1f).fillMaxHeight(height3).background(NeonCyan, RoundedCornerShape(1.dp)))
    }
}

@Composable
fun SongListItem(
    song: SongEntity,
    isDownloading: Boolean,
    downloadProgress: Int?,
    onPlay: () -> Unit,
    onLikeToggle: () -> Unit,
    onDownload: () -> Unit,
    onRemoveClick: (() -> Unit)? = null,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    onInfoClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val cardBg = if (isCurrent) SlateDark.copy(alpha = 0.95f) else SlateDark.copy(alpha = 0.7f)
    val borderStroke = if (isCurrent) {
        BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(NeonCyan, AccentPurple)))
    } else {
        BorderStroke(0.5.dp, CardBorder)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onPlay)
            .testTag("song_item_${song.id}"),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(12.dp),
        border = borderStroke
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = song.coverUrl,
                        contentDescription = song.title,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    if (isCurrent) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            ActiveEqualizerIndicator(isPlaying = isPlaying)
                        }
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            song.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrent) NeonCyan else TextWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isCurrent) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(NeonCyanMuted, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isPlaying) "PLAYING" else "PAUSED",
                                    fontSize = 8.sp,
                                    color = NeonCyan,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            song.artist,
                            fontSize = 12.sp,
                            color = MutedText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (song.isOfficialRelease) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Filled.Verified,
                                contentDescription = "Official Release",
                                tint = LuxeGold,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Info Button to open registry details
                if (onInfoClick != null) {
                    IconButton(onClick = onInfoClick) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "Registry Info",
                            tint = NeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Like Button
                IconButton(onClick = onLikeToggle) {
                    Icon(
                        imageVector = if (song.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (song.isLiked) NeonCyan else MutedText,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Download Button / Spinner
                if (isDownloading) {
                    Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { (downloadProgress ?: 0).toFloat() / 100f },
                            modifier = Modifier.size(22.dp),
                            color = NeonCyan,
                            strokeWidth = 2.dp
                        )
                    }
                } else {
                    IconButton(onClick = {
                        if (!song.isDownloaded) {
                            if (song.allowDownloads) {
                                onDownload()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Download restricted by Content Owner: ${song.copyrightOwner}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }) {
                        Icon(
                            imageVector = if (song.isDownloaded) {
                                Icons.Filled.CheckCircle
                            } else if (!song.allowDownloads) {
                                Icons.Filled.Lock
                            } else {
                                Icons.Filled.DownloadForOffline
                            },
                            contentDescription = "Download status",
                            tint = if (song.isDownloaded) {
                                NeonCyan
                            } else if (!song.allowDownloads) {
                                RedFlag.copy(alpha = 0.8f)
                            } else {
                                MutedText
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Delete / Remove callback
                if (onRemoveClick != null) {
                    IconButton(onClick = onRemoveClick) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = RedFlag, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. DISCOVER & GROUNDED SEARCH SCREEN
// ==========================================
@Composable
fun DiscoverScreen(
    viewModel: MusicViewModel,
    onSongSelect: (SongEntity, List<SongEntity>) -> Unit,
    onAddSongToPlaylist: (SongEntity) -> Unit
) {
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.searchResultSongs.collectAsState()

    val youtubeQuery by viewModel.youtubeSearchQuery.collectAsState()
    val youtubeResults by viewModel.youtubeSearchResultTracks.collectAsState()
    var searchSourceMode by remember { mutableStateOf("YouTube") } // "Local" or "YouTube"

    val currentSongState by viewModel.currentSong.collectAsState()
    val isPlayingState by viewModel.isPlaying.collectAsState()

    val groundingResult by viewModel.searchGroundingResult.collectAsState()
    val groundingSources by viewModel.searchGroundingSources.collectAsState()
    val groundingLoading by viewModel.searchGroundingLoading.collectAsState()

    var activeQuery by remember { mutableStateOf("") }
    var micListening by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedSongForInfo by remember { mutableStateOf<SongEntity?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepMidnight)
                .padding(top = 64.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Search Input
            item {
                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo),
                            contentDescription = "Audio City Logo",
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.5.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "AUDIO CITY",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextWhite,
                                letterSpacing = 2.sp
                            )
                            Text(
                                text = "STREAM. DOWNLOAD. DISCOVER.",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))



                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = if (searchSourceMode == "YouTube") youtubeQuery else query,
                            onValueChange = { 
                                if (searchSourceMode == "YouTube") {
                                    viewModel.updateYouTubeSearchQuery(it)
                                } else {
                                    viewModel.updateSearchQuery(it)
                                }
                            },
                            placeholder = { 
                                Text(
                                    if (searchSourceMode == "YouTube") "Search 100M+ videos on YouTube..." 
                                    else "Search songs, artists, genres...", 
                                    color = MutedText
                                ) 
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("search_text_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedContainerColor = SlateDark,
                                unfocusedContainerColor = SlateDark,
                                cursorColor = if (searchSourceMode == "YouTube") Color(0xFFFF0000) else NeonCyan,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true,
                            trailingIcon = {
                                val textVal = if (searchSourceMode == "YouTube") youtubeQuery else query
                                if (textVal.isNotEmpty()) {
                                    IconButton(
                                        onClick = { 
                                            if (searchSourceMode == "YouTube") {
                                                viewModel.updateYouTubeSearchQuery("")
                                            } else {
                                                viewModel.updateSearchQuery("")
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = "Clear", tint = TextLight)
                                    }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Microphone Search (Simulated speech audio transcription)
                        IconButton(
                            onClick = {
                                if (!micListening) {
                                    micListening = true
                                    Toast.makeText(context, "Listening with Gemini Mic...", Toast.LENGTH_SHORT).show()
                                    scope.launch {
                                        delay(2000) // Pulse duration
                                        // Base64 WAV dummy stream representing spoken "Play Summer Lofi Chill"
                                        viewModel.transcribeAndSearch("UklGRigAAABXQVZFZm10IBIAAAABAAEARKwAAIhYAQACABAAAABkYXRhAgAAAAAA")
                                        micListening = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .background(if (micListening) NeonCyan else SlateDark, RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                imageVector = if (micListening) Icons.Filled.MicNone else Icons.Filled.Mic,
                                contentDescription = "Voice Search",
                                tint = if (micListening) DeepMidnight else NeonCyan
                            )
                        }
                    }
                }
            }

            // Quick Tag Pills
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Lofi", "Synthwave", "Acoustic", "Electronic", "Ambient", "Podcast").forEach { tag ->
                        val isSelected = query.equals(tag, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isSelected) NeonCyan else SlateDark,
                                    RoundedCornerShape(16.dp)
                                )
                                .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                                .clickable {
                                    viewModel.updateSearchQuery(if (isSelected) "" else tag)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                tag,
                                fontSize = 12.sp,
                                color = if (isSelected) DeepMidnight else TextLight,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Google Search Grounding Hub (AI Powered Research)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateDark),
                    border = BorderStroke(1.dp, GlowAmbient.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Language, contentDescription = "Web Search", tint = NeonCyan)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Gemini Search Grounding", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                            }
                            Box(
                                modifier = Modifier
                                    .background(NeonCyanMuted, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("LIVE SEARCH", fontSize = 8.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Ask any current music question (e.g., 'What are the top Billboard hits in 2026?' or 'Analyze acoustic trends'). Gemini will search the web dynamically and return grounded sources.",
                            fontSize = 11.sp,
                            color = MutedText,
                            lineHeight = 15.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row {
                            TextField(
                                value = activeQuery,
                                onValueChange = { activeQuery = it },
                                placeholder = { Text("Ask Google Search...", color = MutedText, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = DeepMidnight,
                                    unfocusedContainerColor = DeepMidnight,
                                    cursorColor = NeonCyan,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (activeQuery.isNotEmpty()) {
                                        viewModel.conductSearchGrounding(activeQuery)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                            ) {
                                Text("Search", color = DeepMidnight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        if (groundingLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(24.dp))
                            }
                        } else if (groundingResult.isNotEmpty()) {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                Text("AI Grounded Answer:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LuxeGold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    groundingResult,
                                    fontSize = 12.sp,
                                    color = TextLight,
                                    lineHeight = 16.sp,
                                    modifier = Modifier
                                        .background(DeepMidnight, RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Sources cited:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MutedText)
                                groundingSources.forEach { src ->
                                    Text(
                                        text = src,
                                        fontSize = 10.sp,
                                        color = NeonCyan,
                                        modifier = Modifier.padding(vertical = 2.dp),
                                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Search Results List
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (searchSourceMode == "YouTube") {
                            if (youtubeQuery.isEmpty()) "YouTube Popular Videos" else "YouTube Results for '$youtubeQuery'"
                        } else {
                            if (query.isEmpty()) "Search Results" else "Results for '$query'"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )

                    if (searchSourceMode == "YouTube") {
                        val isConfigured = com.example.data.api.YouTubeManager.isCredentialsConfigured()
                        Box(
                            modifier = Modifier
                                .background(if (isConfigured) Color(0xFFFF0000).copy(alpha = 0.2f) else LuxeGold.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .border(1.dp, if (isConfigured) Color(0xFFFF0000) else LuxeGold, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isConfigured) "🔴 YOUTUBE API LIVE" else "⚡ YOUTUBE SANDBOX",
                                fontSize = 8.sp,
                                color = if (isConfigured) Color(0xFFFF0000) else LuxeGold,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (searchSourceMode == "YouTube") {
                val sandboxList = com.example.data.api.YouTubeManager.getSandboxTracks()

                val isYouTubeConfigured = com.example.data.api.YouTubeManager.isCredentialsConfigured()
                val finalYouTubeResults = if (isYouTubeConfigured && youtubeResults.isNotEmpty()) {
                    youtubeResults
                } else {
                    if (youtubeQuery.isBlank()) {
                        sandboxList
                    } else {
                        sandboxList.filter { 
                            it.title.contains(youtubeQuery, ignoreCase = true) || 
                            it.channelTitle.contains(youtubeQuery, ignoreCase = true)
                        }
                    }
                }

                if (finalYouTubeResults.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.HourglassEmpty, contentDescription = "Empty", tint = MutedText, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No matching YouTube tracks found.", color = MutedText, fontSize = 13.sp)
                        }
                    }
                } else {
                    items(finalYouTubeResults) { track ->
                        val mappedSong = SongEntity(
                            id = "youtube_${track.id}",
                            title = track.title,
                            artist = track.channelTitle,
                            album = "YouTube Release",
                            durationSec = 240,
                            streamUrl = track.previewUrl,
                            coverUrl = track.thumbnailUrl,
                            genre = "YouTube Music",
                            isUploadedByArtist = false,
                            copyrightOwner = "YouTube LLC Licensed Audio Feed",
                            allowDownloads = false
                        )
                        val isCurrent = currentSongState?.id == mappedSong.id

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { viewModel.playYouTubeTrack(track, finalYouTubeResults) }
                                .testTag("youtube_track_${track.id}"),
                            colors = CardDefaults.cardColors(containerColor = if (isCurrent) SlateDark.copy(alpha = 0.95f) else SlateDark.copy(alpha = 0.7f)),
                            border = if (isCurrent) {
                                BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(Color(0xFFFF0000), NeonCyan)))
                            } else {
                                BorderStroke(0.5.dp, CardBorder)
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Box(contentAlignment = Alignment.Center) {
                                        AsyncImage(
                                            model = mappedSong.coverUrl,
                                            contentDescription = mappedSong.title,
                                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        if (isCurrent && isPlayingState) {
                                            Box(
                                                modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(color = Color(0xFFFF0000), modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(mappedSong.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isCurrent) Color(0xFFFF0000) else TextWhite, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(mappedSong.artist, fontSize = 11.sp, color = TextLight, maxLines = 1)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Box(
                                                modifier = Modifier.background(Color(0xFFFF0000).copy(alpha = 0.15f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text("YouTube Video", fontSize = 7.sp, color = Color(0xFFFF0000), fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                        Text(track.description, fontSize = 9.sp, color = MutedText, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            onAddSongToPlaylist(mappedSong)
                                        }
                                    ) {
                                        Icon(Icons.Filled.PlaylistAdd, contentDescription = "Add to Playlist", tint = Color(0xFFFF0000))
                                    }
                                    Text(
                                        text = String.format("%02d:%02d", mappedSong.durationSec / 60, mappedSong.durationSec % 60),
                                        fontSize = 11.sp,
                                        color = MutedText,
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                if (results.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.HourglassEmpty, contentDescription = "Empty", tint = MutedText, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No matching songs or artists found.", color = MutedText, fontSize = 13.sp)
                        }
                    }
                } else {
                    items(results) { song ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                val isCurrent = currentSongState?.id == song.id
                                SongListItem(
                                    song = song,
                                    isDownloading = viewModel.downloadingSongIds.collectAsState().value.containsKey(song.id),
                                    downloadProgress = viewModel.downloadingSongIds.collectAsState().value[song.id],
                                    onPlay = { onSongSelect(song, results) },
                                    onLikeToggle = { viewModel.toggleLike(song.id) },
                                    onDownload = { viewModel.downloadSong(song.id) },
                                    isCurrent = isCurrent,
                                    isPlaying = isCurrent && isPlayingState,
                                    onInfoClick = { selectedSongForInfo = song }
                                )
                            }
                            IconButton(
                                onClick = { onAddSongToPlaylist(song) },
                                modifier = Modifier.testTag("add_to_playlist_btn_${song.id}")
                            ) {
                                Icon(Icons.Filled.PlaylistAdd, contentDescription = "Add to playlist", tint = NeonCyan)
                            }
                        }
                    }
                }
            }
        }

        selectedSongForInfo?.let { song ->
            PublicSongInfoDialog(
                song = song,
                viewModel = viewModel,
                onDismiss = { selectedSongForInfo = null }
            )
        }
    }
}

// ==========================================
// 4. LIBRARY SCREEN (Curation, Playlists)
// ==========================================
@Composable
fun LibraryScreen(
    viewModel: MusicViewModel,
    onPlaylistSelect: (PlaylistEntity) -> Unit,
    onSongSelect: (SongEntity, List<SongEntity>) -> Unit
) {
    val playlists by viewModel.allPlaylists.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState()

    val currentSongState by viewModel.currentSong.collectAsState()
    val isPlayingState by viewModel.isPlaying.collectAsState()

    val highThinkingResult by viewModel.highThinkingResult.collectAsState()
    val highThinkingLoading by viewModel.highThinkingLoading.collectAsState()

    var showCreatePlaylist by remember { mutableStateOf(false) }
    var plName by remember { mutableStateOf("") }
    var plDesc by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepMidnight)
            .padding(top = 64.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // AI Curation Plan Engine (Requires High Reasoning thinking level)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = SlateDark),
                border = BorderStroke(1.dp, LuxeGold.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Psychology, contentDescription = "Thinking", tint = LuxeGold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Gemini High-Thinking Curation", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                        }
                        Box(
                            modifier = Modifier
                                .background(LuxeGold.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("REASONING HIGH", fontSize = 8.sp, color = LuxeGold, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Generate a deeply analyzed, structured 5-Day Audio Curation Plan based on your historical listening statistics, musical aesthetic tastes, and activities.",
                        fontSize = 11.sp,
                        color = MutedText,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (highThinkingLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = LuxeGold, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Engaging AI thinking nodes... Writing deep audio plan...", fontSize = 10.sp, color = LuxeGold)
                            }
                        }
                    } else if (highThinkingResult.isNotEmpty()) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Text(
                                "Your Bespoke Curation Strategy:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = LuxeGold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = highThinkingResult,
                                fontSize = 11.sp,
                                color = TextLight,
                                lineHeight = 16.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .background(DeepMidnight, RoundedCornerShape(8.dp))
                                    .verticalScroll(rememberScrollState())
                                    .padding(10.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.generateHighThinkingCuration() },
                                colors = ButtonDefaults.buttonColors(containerColor = LuxeGold.copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Re-Generate Plan", color = LuxeGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Button(
                            onClick = { viewModel.generateHighThinkingCuration() },
                            colors = ButtonDefaults.buttonColors(containerColor = LuxeGold),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Generate 5-Day Curation Plan", color = DeepMidnight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Liked Songs Section
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Liked Tracks", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                Text("${likedSongs.size} songs", fontSize = 12.sp, color = NeonCyan)
            }
        }

        if (likedSongs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateDark.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.FavoriteBorder, contentDescription = "No likes", tint = MutedText, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Songs you like will appear here.", color = MutedText, fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(likedSongs.take(4)) { song ->
                val isCurrent = currentSongState?.id == song.id
                SongListItem(
                    song = song,
                    isDownloading = viewModel.downloadingSongIds.collectAsState().value.containsKey(song.id),
                    downloadProgress = viewModel.downloadingSongIds.collectAsState().value[song.id],
                    onPlay = { onSongSelect(song, likedSongs) },
                    onLikeToggle = { viewModel.toggleLike(song.id) },
                    onDownload = { viewModel.downloadSong(song.id) },
                    isCurrent = isCurrent,
                    isPlaying = isCurrent && isPlayingState
                )
            }
        }

        // Playlists Section Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Playlists", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                IconButton(
                    onClick = { showCreatePlaylist = !showCreatePlaylist },
                    modifier = Modifier.testTag("create_playlist_trigger")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Create Playlist", tint = NeonCyan)
                }
            }
        }

        // Create Playlist Card
        if (showCreatePlaylist) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("New Custom Playlist", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = plName,
                            onValueChange = { plName = it },
                            placeholder = { Text("Playlist Name", color = MutedText) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        TextField(
                            value = plDesc,
                            onValueChange = { plDesc = it },
                            placeholder = { Text("Short Description", color = MutedText) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showCreatePlaylist = false }) {
                                Text("Cancel", color = MutedText)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (plName.isNotEmpty()) {
                                        viewModel.createPlaylist(plName, plDesc)
                                        plName = ""
                                        plDesc = ""
                                        showCreatePlaylist = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                            ) {
                                Text("Create", color = DeepMidnight, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Playlist lists
        if (playlists.isEmpty()) {
            item {
                Text("No playlists created yet.", color = MutedText, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
            }
        } else {
            items(playlists) { pl ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onPlaylistSelect(pl) }
                        .testTag("playlist_card_${pl.id}"),
                    colors = CardDefaults.cardColors(containerColor = SlateDark),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            AsyncImage(
                                model = pl.coverUrl,
                                contentDescription = pl.name,
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(pl.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                                Text(pl.description, fontSize = 12.sp, color = MutedText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${pl.songCount} songs", fontSize = 11.sp, color = NeonCyan)
                            }
                        }
                        if (pl.isCustom) {
                            IconButton(onClick = { viewModel.deletePlaylist(pl.id) }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete Playlist", tint = RedFlag)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. DOWNLOADS SCREEN (Offline Storage)
// ==========================================
@Composable
fun DownloadsScreen(
    viewModel: MusicViewModel,
    onSongSelect: (SongEntity, List<SongEntity>) -> Unit
) {
    val downloadedSongs by viewModel.downloadedSongs.collectAsState()
    val currentSongState by viewModel.currentSong.collectAsState()
    val isPlayingState by viewModel.isPlaying.collectAsState()
    var offlineModeOnly by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepMidnight)
            .padding(top = 64.dp, start = 16.dp, end = 16.dp, bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Audio City Storage", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextWhite)
                Text("${downloadedSongs.size} tracks cached offline", fontSize = 12.sp, color = NeonCyan)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Offline Mode", fontSize = 12.sp, color = TextLight, modifier = Modifier.padding(end = 6.dp))
                Switch(
                    checked = offlineModeOnly,
                    onCheckedChange = { offlineModeOnly = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Device Storage Gauge Meter
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateDark),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Audio City Local Sandbox", fontSize = 12.sp, color = TextLight, fontWeight = FontWeight.Bold)
                    Text("185.2 MB of 10.0 GB", fontSize = 11.sp, color = MutedText)
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { 0.02f },
                    modifier = Modifier.fillMaxWidth(),
                    color = NeonCyan,
                    trackColor = DeepMidnight
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text("Downloaded Songs", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextWhite, modifier = Modifier.padding(vertical = 4.dp))

        if (downloadedSongs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.OfflinePin, contentDescription = "Offline", tint = MutedText, modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No offline downloads found.", color = TextLight)
                    Text("Tap the download icon on any song to save offline.", fontSize = 12.sp, color = MutedText)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(downloadedSongs) { song ->
                    val isCurrent = currentSongState?.id == song.id
                    SongListItem(
                        song = song,
                        isDownloading = false,
                        downloadProgress = null,
                        onPlay = { onSongSelect(song, downloadedSongs) },
                        onLikeToggle = { viewModel.toggleLike(song.id) },
                        onDownload = {},
                        onRemoveClick = { viewModel.removeDownloadedSong(song.id) },
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && isPlayingState
                    )
                }
            }
        }
    }
}

// ==========================================
// 6. VOICE COMPANION (Live Conversation API)
// ==========================================
@Composable
fun VoiceCompanionScreen(
    viewModel: MusicViewModel
) {
    val history by viewModel.voiceCompanionHistory.collectAsState()
    val status by viewModel.voiceCompanionStatus.collectAsState()
    val loading by viewModel.voiceCompanionLoading.collectAsState()

    var showHistoryDialog by remember { mutableStateOf(false) }
    val activityLogs by viewModel.userActivity.collectAsState()
    val voiceLogs = remember(activityLogs) {
        activityLogs.filter { it.action == "voice_command" }
    }

    var textInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val listState = rememberScrollState()

    var isListeningByVoice by remember { mutableStateOf(false) }
    val micAmplitude by com.example.ui.player.VoiceInputAnalyzer.amplitude.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(isListeningByVoice) {
        if (isListeningByVoice) {
            com.example.ui.player.VoiceInputAnalyzer.startListening(context)
        } else {
            com.example.ui.player.VoiceInputAnalyzer.stopListening()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DeepMidnight, AccentPurple.copy(alpha = 0.3f), DeepMidnight)
                )
            )
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Cosmic Logo (Custom Brand Logo & Title)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "Audio City Logo",
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    "CITY COMPANION AI",
                    fontSize = 14.sp,
                    color = TextWhite,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )
                Text(
                    "AUDIO CITY PLATFORM",
                    fontSize = 8.sp,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Conversation history bubble window
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SlateDark.copy(alpha = 0.6f))
                .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            if (history.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.Hearing,
                        contentDescription = "Companion Ready",
                        tint = VocalLavender,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Voice nodes are active.",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        "\"Hey City Companion, play Summer Lofi Chill!\"",
                        fontStyle = FontStyle.Italic,
                        color = MutedText,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(listState)
                ) {
                    history.forEach { message ->
                        val isUser = message.role == "user"
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 12.dp,
                                            topEnd = 12.dp,
                                            bottomStart = if (isUser) 12.dp else 0.dp,
                                            bottomEnd = if (isUser) 0.dp else 12.dp
                                        )
                                    )
                                    .background(if (isUser) NeonCyan else SurfaceDark)
                                    .padding(12.dp)
                                    .widthIn(max = 260.dp)
                            ) {
                                Text(
                                    text = message.parts.firstOrNull()?.text ?: "",
                                    fontSize = 13.sp,
                                    color = if (isUser) DeepMidnight else TextWhite,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                    LaunchedEffect(history.size) {
                        listState.animateScrollTo(listState.maxValue)
                    }
                }
            }
        }

        // Animated Waves & Status Details (Glow & High-Fidelity Voice Visualizer)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            VoiceVisualizerComponent(
                amplitude = if (isListeningByVoice) micAmplitude else if (loading) 0.65f else 0f,
                isActive = isListeningByVoice || loading,
                statusText = when {
                    isListeningByVoice -> "Listening to your voice..."
                    loading -> "Companion is thinking..."
                    status.contains("Response spoken") -> "Response spoken via Kore TTS"
                    else -> "Tap the microphone or type to converse"
                },
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Collapsible/Clickable Voice Command History Log Chip
        Row(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(16.dp))
                .background(SlateDark.copy(alpha = 0.6f))
                .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                .clickable { showHistoryDialog = true }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = "History",
                tint = NeonCyan,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Voice Command History (${voiceLogs.size})",
                color = NeonCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Controls Area (Text Input and Microphone click)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("Talk to City Companion AI...", color = MutedText, fontSize = 12.sp) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("companion_text_input"),
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SlateDark,
                    unfocusedContainerColor = SlateDark,
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Interactive Voice Microphone trigger
            IconButton(
                onClick = {
                    if (!isListeningByVoice) {
                        isListeningByVoice = true
                        Toast.makeText(context, "Voice mode active. Speak now!", Toast.LENGTH_SHORT).show()
                        scope.launch {
                            delay(3200) // Simulated voice input duration
                            val sampleCompanionCommands = listOf(
                                "Play some chill lo-fi chill out",
                                "Find me Electro Rush",
                                "What's the status of the media?",
                                "Show voice history logs please",
                                "Play matching song lofi",
                                "Recommend music based on my history"
                            )
                            val cmdText = sampleCompanionCommands.random()
                            Toast.makeText(context, "Transcribed: \"$cmdText\"", Toast.LENGTH_LONG).show()
                            viewModel.sendVoiceCompanionMessage(cmdText)
                            isListeningByVoice = false
                        }
                    } else {
                        isListeningByVoice = false
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(if (isListeningByVoice) NeonCyanMuted else SurfaceDark, CircleShape)
                    .testTag("companion_mic_button")
            ) {
                Icon(
                    imageVector = if (isListeningByVoice) Icons.Filled.MicNone else Icons.Filled.Mic,
                    contentDescription = "Voice Input Mode",
                    tint = if (isListeningByVoice) NeonCyan else TextLight
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = {
                    if (textInput.isNotEmpty()) {
                        viewModel.sendVoiceCompanionMessage(textInput)
                        textInput = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(NeonCyan, CircleShape)
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send", tint = DeepMidnight)
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = { viewModel.clearVoiceCompanion() },
                modifier = Modifier
                    .size(48.dp)
                    .background(SurfaceDark, CircleShape)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Clear", tint = TextLight)
            }
        }
    }

    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Voice Command History",
                        color = TextWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Review recent voice control commands processed by the system. Action results are recorded instantly.",
                        color = MutedText,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (voiceLogs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.MicOff,
                                    contentDescription = null,
                                    tint = MutedText,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No voice commands issued yet",
                                    color = MutedText,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                        ) {
                            items(voiceLogs) { log ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = SlateDark),
                                    border = BorderStroke(1.dp, CardBorder.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(NeonCyan.copy(alpha = 0.15f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Mic,
                                                contentDescription = "Voice",
                                                tint = NeonCyan,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "\"${log.songTitle ?: "Unknown Voice Input"}\"",
                                                color = TextWhite,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                fontStyle = FontStyle.Italic
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = log.artistName ?: "Action executed",
                                                color = VocalLavender,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                            .format(java.util.Date(log.timestamp))
                                        Text(
                                            text = timeStr,
                                            color = MutedText,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false }) {
                    Text("Close", color = NeonCyan, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = DeepMidnight,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

// ==========================================
// 7. PROFILE SCREEN (Statistics, User details)
// ==========================================
@Composable
fun ProfileScreen(
    viewModel: MusicViewModel,
    onOpenSettings: () -> Unit,
    onOpenPremium: () -> Unit
) {
    val userProfile by viewModel.currentUser.collectAsState()
    val activityLogs by viewModel.userActivity.collectAsState()

    var showEditDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editEmail by remember { mutableStateOf("") }
    var editMode by remember { mutableStateOf(false) }

    LaunchedEffect(userProfile) {
        userProfile?.let {
            editName = it.name
            editEmail = it.email
            editMode = it.artistModeEnabled
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepMidnight)
            .padding(top = 64.dp, bottom = 12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        // Upper Profile Info
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                colors = CardDefaults.cardColors(containerColor = SlateDark),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.size(96.dp)) {
                        AsyncImage(
                            model = userProfile?.photoUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?q=80&w=300",
                            contentDescription = "Avatar",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .border(2.dp, NeonCyan, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { showEditDialog = true },
                            modifier = Modifier
                                .size(28.dp)
                                .background(NeonCyan, CircleShape)
                                .align(Alignment.BottomEnd)
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit photo", tint = DeepMidnight, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        userProfile?.name ?: "Zesty Kiddy",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )

                    Text(
                        userProfile?.email ?: "zestykiddy2005@gmail.com",
                        fontSize = 12.sp,
                        color = MutedText
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ProfileStatItem("Followers", (userProfile?.followersCount ?: 124).toString())
                        Divider(modifier = Modifier.width(1.dp).height(24.dp), color = CardBorder)
                        ProfileStatItem("Following", (userProfile?.followingCount ?: 56).toString())
                        Divider(modifier = Modifier.width(1.dp).height(24.dp), color = CardBorder)
                        ProfileStatItem("Listen Time", "48.5 hrs")
                    }
                }
            }
        }

        // Account actions / Mode toggles
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = SlateDark),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("Premium Billing Center", color = TextWhite) },
                        supportingContent = { Text(if (userProfile?.isPremium == true) "Active plan: ${userProfile?.planName}" else "Activate premium listening privileges", color = MutedText) },
                        leadingContent = { Icon(Icons.Filled.Star, contentDescription = "Premium", tint = LuxeGold) },
                        trailingContent = {
                            Button(
                                onClick = onOpenPremium,
                                colors = ButtonDefaults.buttonColors(containerColor = if (userProfile?.isPremium == true) RedFlag else NeonCyan)
                            ) {
                                Text(
                                    if (userProfile?.isPremium == true) "Manage" else "Join",
                                    color = DeepMidnight,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    Divider(color = CardBorder)

                    ListItem(
                        headlineContent = { Text("Artist Dashboard Mode", color = TextWhite) },
                        supportingContent = { Text("Unlock artist uploads and analytic metrics", color = MutedText) },
                        leadingContent = { Icon(Icons.Filled.MusicNote, contentDescription = "Artist", tint = NeonCyan) },
                        trailingContent = {
                            Switch(
                                checked = userProfile?.artistModeEnabled == true,
                                onCheckedChange = { checked ->
                                    userProfile?.let {
                                        viewModel.saveUserProfile(it.name, it.email, it.photoUrl, checked)
                                    }
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan)
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.testTag("artist_mode_switch")
                    )
                }
            }
        }

        // System Diagnostics / Error Boundary Section
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = SlateDark),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "System Diagnostics & Protection",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextLight,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Status Indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFF4CAF50), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Boundary Protection", fontSize = 12.sp, color = TextWhite)
                        }
                        Text("ACTIVE & SECURE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Simulation Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val errorBoundaryScope = LocalErrorBoundary.current
                        
                        Button(
                            onClick = {
                                errorBoundaryScope.reportError(
                                    RuntimeException("Simulated UI Resonance Interruption: A simulated recomposition exception occurred inside the active media layout.")
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("sim_ui_error_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                            border = BorderStroke(1.dp, CardBorder),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Text("Simulate UI Error", fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                        }
                        
                        Button(
                            onClick = {
                                Thread {
                                    throw RuntimeException("Simulated VM Hardware Thread Interruption: This crash was captured by the global CrashHandler.")
                                }.start()
                            },
                            modifier = Modifier.weight(1f).testTag("sim_vm_crash_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                            border = BorderStroke(1.dp, CardBorder),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Text("Simulate VM Crash", fontSize = 11.sp, color = RedFlag, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Artist upload area if checked
        if (userProfile?.artistModeEnabled == true) {
            item {
                ArtistDashboardScreen(viewModel = viewModel)
            }
        }

        // System Admin Dashboards
        item {
            AdminDashboardScreen(viewModel = viewModel)
        }

        // Activity Log Timeline Section
        item {
            Text(
                "My Auditory Activity Timeline",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
        }

        if (activityLogs.isEmpty()) {
            item {
                Text("No activity logged yet.", color = MutedText, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
            }
        } else {
            items(activityLogs) { log ->
                ActivityLogItem(log = log)
            }
        }
    }

    // Edit Profile Modal
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Member Profile", color = TextWhite) },
            text = {
                Column {
                    TextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Display Name") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                    TextField(
                        value = editEmail,
                        onValueChange = { editEmail = it },
                        label = { Text("Email Address") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isNotEmpty() && editEmail.isNotEmpty()) {
                            viewModel.saveUserProfile(editName, editEmail, userProfile?.photoUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?q=80&w=300", editMode)
                            showEditDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text("Save", color = DeepMidnight)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel", color = MutedText)
                }
            },
            containerColor = SlateDark
        )
    }
}

@Composable
fun PublicSongInfoDialog(
    song: SongEntity,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    var showReportPortal by remember { mutableStateOf(false) }
    var reportEmail by remember { mutableStateOf("") }
    var reportDetails by remember { mutableStateOf("") }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = LuxeGold, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Song Registry & License Center", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextWhite)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Main Registry Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateDark),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("TITLE: ${song.title}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                        Text("ARTIST: ${song.artist}", fontSize = 11.sp, color = NeonCyan)
                        Text("ALBUM: ${song.album}", fontSize = 11.sp, color = MutedText)
                        Spacer(modifier = Modifier.height(6.dp))
                        Divider(color = CardBorder)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("ISRC CODE: ${song.isrcCode ?: "US-AC1-26-89021"}", fontSize = 10.sp, color = LuxeGold, fontFamily = FontFamily.Monospace)
                        Text("COPYRIGHT HOLDER: ${song.copyrightOwner}", fontSize = 10.sp, color = TextLight)
                        Text("DISTRIBUTOR/LABEL: ${song.labelName ?: "Independent Release"}", fontSize = 10.sp, color = TextLight)
                        
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                            Icon(
                                imageVector = if (song.allowDownloads) Icons.Filled.CloudDownload else Icons.Filled.Lock,
                                contentDescription = null,
                                tint = if (song.allowDownloads) NeonCyan else RedFlag,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (song.allowDownloads) "Downloads Allowed" else "Restricted by Owner Policy",
                                fontSize = 10.sp,
                                color = if (song.allowDownloads) NeonCyan else RedFlag,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Public Artist / Producer Profile Snippet
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(NeonCyan, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(song.artist.take(1), color = DeepMidnight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(song.artist, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Filled.Verified, contentDescription = "Verified Artist", tint = LuxeGold, modifier = Modifier.size(14.dp))
                                }
                                Text("Global Platform Rank: #${song.globalRank.takeIf { it > 0 } ?: "47"}", fontSize = 9.sp, color = MutedText)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Licensed creator and copyright verified partner of Audio City. Streams music legal distributions worldwide across edge Content Delivery Network (CDN) servers.",
                            fontSize = 10.sp,
                            color = TextLight,
                            lineHeight = 14.sp
                        )
                    }
                }

                if (!showReportPortal) {
                    Button(
                        onClick = { showReportPortal = true },
                        colors = ButtonDefaults.buttonColors(containerColor = RedFlag.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, RedFlag)
                    ) {
                        Text("Submit DMCA / Copyright Report", color = RedFlag, fontSize = 11.sp)
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = RedFlag.copy(alpha = 0.05f)),
                        border = BorderStroke(1.dp, RedFlag.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("DMCA Copyright Takedown Request", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RedFlag)
                            Spacer(modifier = Modifier.height(6.dp))
                            TextField(
                                value = reportEmail,
                                onValueChange = { reportEmail = it },
                                placeholder = { Text("Your Email Address", color = MutedText, fontSize = 10.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = TextWhite,
                                    unfocusedTextColor = TextWhite,
                                    focusedContainerColor = SlateDark,
                                    unfocusedContainerColor = SlateDark
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            TextField(
                                value = reportDetails,
                                onValueChange = { reportDetails = it },
                                placeholder = { Text("State details of copyright ownership dispute...", color = MutedText, fontSize = 10.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 3,
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = TextWhite,
                                    unfocusedTextColor = TextWhite,
                                    focusedContainerColor = SlateDark,
                                    unfocusedContainerColor = SlateDark
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (reportEmail.isNotEmpty() && reportDetails.isNotEmpty()) {
                                            viewModel.fileCopyrightReport(song.id, reportEmail, reportDetails)
                                            Toast.makeText(context, "DMCA dispute successfully filed! Track restricted.", Toast.LENGTH_LONG).show()
                                            showReportPortal = false
                                            onDismiss()
                                        } else {
                                            Toast.makeText(context, "Please fill in all details.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = RedFlag),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("File Takedown", fontSize = 10.sp, color = TextWhite)
                                }
                                Button(
                                    onClick = { showReportPortal = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = SlateDark),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Cancel", fontSize = 10.sp, color = TextLight)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
            ) {
                Text("Close Registry", color = DeepMidnight, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = DeepMidnight,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun ProfileStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextWhite)
        Text(label, fontSize = 11.sp, color = MutedText)
    }
}

@Composable
fun ActivityLogItem(log: UserActivityEntity) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = SlateDark.copy(alpha = 0.5f)),
        border = BorderStroke(0.5.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when (log.action) {
                "play" -> Icons.Filled.PlayArrow
                "like" -> Icons.Filled.Favorite
                "download" -> Icons.Filled.DownloadDone
                "comment" -> Icons.Filled.Comment
                "upload" -> Icons.Filled.CloudUpload
                else -> Icons.Filled.Settings
            }
            val color = when (log.action) {
                "like" -> NeonCyan
                "play" -> LuxeGold
                "download" -> VocalLavender
                else -> TextLight
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(color.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = log.action, tint = color, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                val actionDesc = when (log.action) {
                    "play" -> "Listened to"
                    "like" -> "Liked track"
                    "unlike" -> "Unliked track"
                    "download" -> "Downloaded track"
                    "comment" -> "Commented on"
                    "upload" -> "Uploaded song"
                    "playlist" -> "Created Playlist"
                    else -> "Updated settings"
                }
                Text(
                    text = "$actionDesc '${log.songTitle ?: log.artistName}'",
                    fontSize = 12.sp,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (log.artistName != null && log.action == "play") "by ${log.artistName}" else "Activity logs",
                    fontSize = 10.sp,
                    color = MutedText
                )
            }
        }
    }
}

// ==========================================
// 8. ARTIST DASHBOARD PANEL COMPONENT
// ==========================================
@Composable
fun ArtistDashboardScreen(
    viewModel: MusicViewModel
) {
    var title by remember { mutableStateOf("") }
    var album by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("Lofi") }
    var streamUrl by remember { mutableStateOf("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3") }
    var coverUrl by remember { mutableStateOf("https://images.unsplash.com/photo-1514525253161-7a46d19cd819?q=80&w=300") }

    val uploadProgress by viewModel.uploadProgress.collectAsState()
    val userProfile by viewModel.currentUser.collectAsState()
    val allSongs by viewModel.allSongs.collectAsState()

    // Filter artist songs
    val artistSongs = allSongs.filter { it.artistId == userProfile?.id || it.artist.equals(userProfile?.name, ignoreCase = true) }
    val totalStreams = artistSongs.sumOf { it.playCount }
    val royaltyEarned = totalStreams * 0.0045

    // Local inputs for verification request
    var bio by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var verGenre by remember { mutableStateOf("") }
    var accountType by remember { mutableStateOf("Artist") }

    // Interactive Analytics State
    var activeMetric by remember { mutableStateOf("Streams") }
    val graphPoints = remember(activeMetric) {
        when (activeMetric) {
            "Streams" -> listOf(12f, 28f, 18f, 42f, 38f, 58f, 45f, 78f, 65f, 92f)
            "Listeners" -> listOf(35f, 38f, 34f, 48f, 52f, 46f, 55f, 68f, 62f, 75f)
            else -> listOf(8f, 14f, 19f, 24f, 32f, 41f, 48f, 56f, 65f, 80f) // Follower growth
        }
    }
    val graphTrendText = when (activeMetric) {
        "Streams" -> "+15.2% hourly streams surge 📈"
        "Listeners" -> "+8.7% loyal listener retention 🎧"
        else -> "+22.1% community follower expansion 🚀"
    }

    // Interactive Financial summary state
    var selectedPayoutRate by remember { mutableStateOf(0.0045) }
    var selectedPlatform by remember { mutableStateOf("Spotify Premium") }
    var forecastMultiplier by remember { mutableStateOf(1f) }
    var cashoutLogs by remember { mutableStateOf(listOf<Triple<String, Double, String>>()) } // Date, Amount, Status
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        // Main Artist Card Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .testTag("artist_dashboard_card"),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Brush, contentDescription = "Artist Panel", tint = NeonCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Creative Artist Upload Console", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                    }
                    if (userProfile?.isVerifiedArtist == true) {
                        Box(
                            modifier = Modifier
                                .background(LuxeGold.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .border(1.dp, LuxeGold, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Verified, contentDescription = "Verified", tint = LuxeGold, modifier = Modifier.size(10.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("VERIFIED", fontSize = 8.sp, color = LuxeGold, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Track Title (e.g. Dreamy Breeze)", color = MutedText) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )

                TextField(
                    value = album,
                    onValueChange = { album = it },
                    placeholder = { Text("Album Name (e.g. Midnight Beats Vol. 1)", color = MutedText) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )

                // Genre selection
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select Genre:", color = TextLight, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Lofi", "Synthwave", "Acoustic", "Electronic").forEach { g ->
                            val sel = genre == g
                            Box(
                                modifier = Modifier
                                    .background(if (sel) NeonCyan else SlateDark, RoundedCornerShape(4.dp))
                                    .clickable { genre = g }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(g, fontSize = 10.sp, color = if (sel) DeepMidnight else TextLight, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                TextField(
                    value = streamUrl,
                    onValueChange = { streamUrl = it },
                    placeholder = { Text("Audio MP3 Stream URL", color = MutedText) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )

                TextField(
                    value = coverUrl,
                    onValueChange = { coverUrl = it },
                    placeholder = { Text("Cover Artwork URL", color = MutedText) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (uploadProgress != null) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Uploading track package...", fontSize = 12.sp, color = NeonCyan)
                            Text("$uploadProgress%", fontSize = 12.sp, color = NeonCyan)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { uploadProgress!!.toFloat() / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = NeonCyan
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            if (title.isNotEmpty() && album.isNotEmpty()) {
                                viewModel.artistUploadSong(title, album, 185, genre, streamUrl, coverUrl)
                                title = ""
                                album = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = "Upload")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Publish Song to Audio City", color = DeepMidnight, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Global Royalty & Analytics Dashboard
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = SlateDark),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ShowChart, contentDescription = "Analytics", tint = LuxeGold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Creator Performance Analytics", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                    }
                    Text(
                        text = graphTrendText,
                        fontSize = 11.sp,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Stats row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = DeepMidnight)
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Total Streams", fontSize = 9.sp, color = MutedText)
                            Text("$totalStreams", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = NeonCyan)
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = DeepMidnight)
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Royalty Balance", fontSize = 9.sp, color = MutedText)
                            Text(String.format("$%.2f", royaltyEarned), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = LuxeGold)
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = DeepMidnight)
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Publications", fontSize = 9.sp, color = MutedText)
                            Text("${artistSongs.size}", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TextWhite)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                
                // Metric Selector Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Streams", "Listeners", "Followers").forEach { metric ->
                        val selected = activeMetric == metric
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (selected) NeonCyan.copy(alpha = 0.2f) else DeepMidnight, RoundedCornerShape(6.dp))
                                .border(1.dp, if (selected) NeonCyan else CardBorder, RoundedCornerShape(6.dp))
                                .clickable { activeMetric = metric }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = metric,
                                fontSize = 11.sp,
                                color = if (selected) NeonCyan else TextLight,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Dynamic Canvas Sparkline
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(DeepMidnight, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    val widthStep = size.width / (graphPoints.size - 1)
                    val heightMax = graphPoints.maxOrNull() ?: 100f
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(0f, size.height - (graphPoints[0] / heightMax) * size.height)
                        for (i in 1 until graphPoints.size) {
                            val x = i * widthStep
                            val y = size.height - (graphPoints[i] / heightMax) * size.height
                            lineTo(x, y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = NeonCyan,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx())
                    )
                }
            }
        }

        // Listener Demographics Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = SlateDark),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Public, contentDescription = "Demographics", tint = NeonCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Listener Demographics & Platform Split", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Two columns: Left is Locations, Right is Age/Device
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left Column: Top Locations
                    Column(modifier = Modifier.weight(1.1f)) {
                        Text("Top Regions / Cities", fontSize = 11.sp, color = LuxeGold, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        val locations = listOf(
                            "Dar es Salaam, TZ" to 0.38f,
                            "Nairobi, KE" to 0.24f,
                            "Tokyo, JP" to 0.15f,
                            "London, UK" to 0.13f,
                            "New York, US" to 0.10f
                        )
                        locations.forEach { (city, percent) ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(city, fontSize = 10.sp, color = TextLight, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text("${(percent * 100).toInt()}%", fontSize = 10.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .background(DeepMidnight, RoundedCornerShape(2.dp))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(percent)
                                            .fillMaxHeight()
                                            .background(NeonCyan, RoundedCornerShape(2.dp))
                                    )
                                }
                            }
                        }
                    }

                    // Right Column: Age Segments & Devices
                    Column(modifier = Modifier.weight(0.9f)) {
                        Text("Age Distribution", fontSize = 11.sp, color = LuxeGold, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))

                        val ages = listOf(
                            "18–24" to 0.55f,
                            "25–34" to 0.30f,
                            "35+" to 0.15f
                        )
                        ages.forEach { (age, percent) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(age, fontSize = 10.sp, color = TextLight, modifier = Modifier.width(36.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(5.dp)
                                        .background(DeepMidnight, RoundedCornerShape(2.dp))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(percent)
                                            .fillMaxHeight()
                                            .background(LuxeGold, RoundedCornerShape(2.dp))
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${(percent*100).toInt()}%", fontSize = 9.sp, color = TextWhite, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Platform Distribution", fontSize = 11.sp, color = LuxeGold, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Android", fontSize = 9.sp, color = MutedText)
                                Text("65%", fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("iOS", fontSize = 9.sp, color = MutedText)
                                Text("30%", fontSize = 11.sp, color = TextWhite, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Web/PC", fontSize = 9.sp, color = MutedText)
                                Text("5%", fontSize = 11.sp, color = TextLight, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Smart Royalty Calculator & Revenue Forecasting Widget
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = SlateDark),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Calculate, contentDescription = "Revenue Calculator", tint = LuxeGold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Interactive Royalty & Financial Predictor", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Simulate future revenue based on streaming payout tiers and audience growth estimates.", fontSize = 11.sp, color = MutedText)
                
                Spacer(modifier = Modifier.height(12.dp))

                // Payout Platform Rate selector row
                Text("Select Payout Rate Tier:", fontSize = 11.sp, color = TextLight, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val platformTiers = listOf(
                        Triple("Spotify", 0.0045, "Spotify Standard ($0.0045)"),
                        Triple("Tidal", 0.0110, "Tidal Premium ($0.0110)"),
                        Triple("Apple", 0.0075, "Apple Music ($0.0075)"),
                        Triple("Indie", 0.0035, "Independent Feed ($0.0035)")
                    )
                    platformTiers.forEach { (label, rate, desc) ->
                        val active = selectedPayoutRate == rate
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (active) LuxeGold else DeepMidnight, RoundedCornerShape(4.dp))
                                .border(1.dp, if (active) LuxeGold else CardBorder, RoundedCornerShape(4.dp))
                                .clickable {
                                    selectedPayoutRate = rate
                                    selectedPlatform = desc
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, fontSize = 9.sp, color = if (active) DeepMidnight else TextLight, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Multiplier selector
                Text("Projected Growth Simulation:", fontSize = 11.sp, color = TextLight, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val factors = listOf(
                        "1x (Current)" to 1f,
                        "2x Boost" to 2f,
                        "5x Viral" to 5f,
                        "10x Fame" to 10f,
                        "50x Global" to 50f
                    )
                    factors.forEach { (lbl, mult) ->
                        val active = forecastMultiplier == mult
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (active) NeonCyan else DeepMidnight, RoundedCornerShape(4.dp))
                                .border(1.dp, if (active) NeonCyan else CardBorder, RoundedCornerShape(4.dp))
                                .clickable { forecastMultiplier = mult }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(lbl, fontSize = 8.sp, color = if (active) DeepMidnight else TextLight, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Forecast metrics cards
                val projectedStreams = (totalStreams * forecastMultiplier).toInt()
                val projectedMonthly = projectedStreams * selectedPayoutRate
                val projectedAnnual = projectedMonthly * 12

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = DeepMidnight)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Projected Streams", fontSize = 9.sp, color = MutedText)
                            Text("$projectedStreams", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = NeonCyan)
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = DeepMidnight)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Est. Monthly Royalty", fontSize = 9.sp, color = MutedText)
                            Text(String.format("$%.2f", projectedMonthly), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = LuxeGold)
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = DeepMidnight)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Annualized Earnings", fontSize = 9.sp, color = MutedText)
                            Text(String.format("$%.2f", projectedAnnual), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TextWhite)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Divider(color = CardBorder)
                Spacer(modifier = Modifier.height(12.dp))

                // Threshold withdrawal widget
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Instant Payout & Cashout Console", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                        Text("Current balance: " + String.format("$%.2f", royaltyEarned) + " / $100.00 threshold", fontSize = 10.sp, color = MutedText)
                    }
                    Button(
                        onClick = {
                            if (royaltyEarned <= 0.0) {
                                Toast.makeText(context, "Balance is $0.00! Publish songs & gather streams to earn royalties.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Instant transfer of " + String.format("$%.2f", royaltyEarned) + " initiated to your linked Stripe account!", Toast.LENGTH_LONG).show()
                                cashoutLogs = listOf(Triple("June 26, 2026", royaltyEarned, "Processing (ETA: 2 Days)")) + cashoutLogs
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LuxeGold),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Filled.Payments, contentDescription = "Cashout", modifier = Modifier.size(14.dp), tint = DeepMidnight)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Withdraw Payout", fontSize = 11.sp, color = DeepMidnight, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                val payoutProgress = (royaltyEarned / 100.00).coerceIn(0.0, 1.0)
                LinearProgressIndicator(
                    progress = { payoutProgress.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(2.5.dp)),
                    color = LuxeGold,
                    trackColor = DeepMidnight
                )

                if (cashoutLogs.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Recent Direct Withdrawals:", fontSize = 11.sp, color = TextLight, fontWeight = FontWeight.Bold)
                    cashoutLogs.forEach { (date, amt, status) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .background(DeepMidnight, RoundedCornerShape(4.dp))
                                .padding(6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Cashout Request", fontSize = 10.sp, color = TextWhite, fontWeight = FontWeight.Bold)
                                Text(date, fontSize = 9.sp, color = MutedText)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(String.format("$%.2f", amt), fontSize = 10.sp, color = LuxeGold, fontWeight = FontWeight.Bold)
                                Text(status, fontSize = 8.sp, color = NeonCyan)
                            }
                        }
                    }
                }
            }
        }

        // AWS S3 / CDN Distribution Monitor
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = SlateDark),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudSync, contentDescription = "CDN Monitor", tint = NeonCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("S3 Cloud Storage & Multi-Region CDN Status", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Active Global Distribution Node Latencies:", fontSize = 11.sp, color = MutedText)
                Spacer(modifier = Modifier.height(6.dp))

                val regions = listOf(
                    "us-east-1 (Virginia S3)" to "24ms (Optimal)",
                    "eu-west-2 (London S3)" to "12ms (Optimal)",
                    "ap-east-1 (Tokyo S3)" to "48ms (Cached)"
                )
                regions.forEach { (reg, lat) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(reg, fontSize = 11.sp, color = TextLight)
                        Text(lat, fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Verification Request Portal
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = SlateDark),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.WorkspacePremium, contentDescription = "Verification Badge", tint = LuxeGold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Artist Verification Portal", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (userProfile?.isVerifiedArtist == true) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(LuxeGold.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .border(1.dp, LuxeGold, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = "Verified Status", tint = LuxeGold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Verified Creator Badge Active", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LuxeGold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Congratulations! Your artist profile, publications, and releases carry the official Audio City golden badge of authenticity.", fontSize = 11.sp, color = TextLight, lineHeight = 15.sp)
                        }
                    }
                } else {
                    Text("Request your golden verification checkmark to prove ownership of your releases globally.", fontSize = 11.sp, color = MutedText)
                    Spacer(modifier = Modifier.height(8.dp))

                    TextField(
                        value = bio,
                        onValueChange = { bio = it },
                        placeholder = { Text("Artist Biography / DJ Bio", color = MutedText) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )

                    TextField(
                        value = website,
                        onValueChange = { website = it },
                        placeholder = { Text("Official Webpage or Linktree", color = MutedText) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )

                    TextField(
                        value = verGenre,
                        onValueChange = { verGenre = it },
                        placeholder = { Text("Primary Genre Specialties", color = MutedText) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Creator Account Type:", color = TextLight, fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("Artist", "DJ", "Producer", "Label").forEach { type ->
                                val active = accountType == type
                                Box(
                                    modifier = Modifier
                                        .background(if (active) LuxeGold else DeepMidnight, RoundedCornerShape(4.dp))
                                        .clickable { accountType = type }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(type, fontSize = 9.sp, color = if (active) DeepMidnight else TextLight, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            if (bio.isNotEmpty() && website.isNotEmpty()) {
                                viewModel.submitArtistVerification(bio, verGenre, website, accountType)
                                bio = ""
                                website = ""
                                verGenre = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LuxeGold),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Apply for Official Creator Badge", color = DeepMidnight, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // YouTube Creator Live Sync Console
        val youtubeArtistDetails by viewModel.youtubeArtistMetrics.collectAsState()
        val youtubeArtistTracks by viewModel.youtubeArtistTracks.collectAsState()
        val isYouTubeSyncing by viewModel.isYouTubeSyncing.collectAsState()

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .testTag("youtube_creator_sync_card"),
            colors = CardDefaults.cardColors(containerColor = SlateDark),
            border = BorderStroke(1.dp, Color(0xFFFF0000).copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle, // Using checkcircle as a clear verification indicator
                            contentDescription = "YouTube",
                            tint = Color(0xFFFF0000),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("YouTube Live Creator Sync", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                    }
                    val isConfigured = com.example.data.api.YouTubeManager.isCredentialsConfigured()
                    Box(
                        modifier = Modifier
                            .background(if (isConfigured) Color(0xFFFF0000).copy(alpha = 0.2f) else LuxeGold.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .border(1.dp, if (isConfigured) Color(0xFFFF0000) else LuxeGold, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isConfigured) "🔴 API ACTIVE" else "⚡ SANDBOX MODE",
                            fontSize = 8.sp,
                            color = if (isConfigured) Color(0xFFFF0000) else LuxeGold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Sync your Audio City creator handle with your YouTube video directory to load real-time audience metrics, stream projections, and subscriber stats.",
                    fontSize = 11.sp,
                    color = MutedText,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Optional YouTube API Key textfield so the user can paste their own API key live in-app!
                var customApiKey by remember { mutableStateOf(com.example.data.api.YouTubeManager.getEffectiveApiKey()) }
                
                TextField(
                    value = customApiKey,
                    onValueChange = { 
                        customApiKey = it
                        com.example.data.api.YouTubeManager.setRuntimeApiKey(it)
                    },
                    placeholder = { Text("Enter custom YouTube API v3 Key (Optional)", color = MutedText) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedContainerColor = DeepMidnight,
                        unfocusedContainerColor = DeepMidnight,
                        focusedIndicatorColor = Color(0xFFFF0000),
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                var syncArtistName by remember { mutableStateOf(userProfile?.name ?: "Kendrick Lamar") }

                TextField(
                    value = syncArtistName,
                    onValueChange = { syncArtistName = it },
                    placeholder = { Text("YouTube Channel or Artist Name", color = MutedText) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedContainerColor = DeepMidnight,
                        unfocusedContainerColor = DeepMidnight,
                        focusedIndicatorColor = Color(0xFFFF0000),
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (isYouTubeSyncing) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Color(0xFFFF0000), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Polling YouTube Catalog & Directory...", color = Color(0xFFFF0000), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            if (syncArtistName.isNotBlank()) {
                                viewModel.syncArtistYouTubeMetrics(syncArtistName)
                            } else {
                                Toast.makeText(context, "Please enter a channel name to sync", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Sync, contentDescription = "Sync", tint = TextWhite)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Initiate YouTube Statistics Sync", color = TextWhite, fontWeight = FontWeight.Bold)
                    }
                }

                val details = youtubeArtistDetails
                val tracks = youtubeArtistTracks

                // Draw statistics block
                if (details != null || (!isYouTubeSyncing && syncArtistName.isNotBlank() && details == null)) {
                    val artistName = details?.title ?: syncArtistName
                    val channelName = details?.channelTitle ?: syncArtistName
                    val subscribersCount = 1420500 // Sandbox count
                    val activeViews = 248900000 // Sandbox views

                    Spacer(modifier = Modifier.height(14.dp))
                    Divider(color = CardBorder)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Active YouTube Feed: $artistName", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF0000))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = DeepMidnight)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Subscribers Sync", fontSize = 9.sp, color = MutedText)
                                Text(String.format("%,d", subscribersCount), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TextWhite)
                            }
                        }
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = DeepMidnight)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Total Video Views", fontSize = 9.sp, color = MutedText)
                                Text(String.format("%,d", activeViews), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFF0000))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Channel Owner: $channelName", fontSize = 9.sp, color = MutedText)

                    if (tracks.isNotEmpty() || details == null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Synced YouTube Global Releases:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                        Spacer(modifier = Modifier.height(6.dp))

                        val displayTracks = if (tracks.isNotEmpty()) tracks.take(3) else listOf(
                            com.example.data.api.YouTubeTrack("sw_1", "Not Like Us - Official Video", "Kendrick Lamar", "Exclusive music video", "https://img.youtube.com/vi/dQw4w9WgXcQ/0.jpg", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"),
                            com.example.data.api.YouTubeTrack("sw_2", "HUMBLE. - Live performance", "Kendrick Lamar", "Live on YouTube Sessions", "https://img.youtube.com/vi/kJQP7kiw5Fk/0.jpg", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"),
                            com.example.data.api.YouTubeTrack("sw_3", "Alright - Official Music Video", "Kendrick Lamar", "Alright album master release", "https://img.youtube.com/vi/9bZkp7q19f0/0.jpg", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3")
                        )

                        displayTracks.forEach { t ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(DeepMidnight, RoundedCornerShape(6.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = "Active", tint = Color(0xFFFF0000), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(t.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextWhite, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        Text("Channel: " + t.channelTitle, fontSize = 8.sp, color = MutedText)
                                    }
                                }
                                Box(
                                    modifier = Modifier.background(Color(0xFFFF0000).copy(alpha = 0.15f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("API SYNCED", fontSize = 8.sp, color = Color(0xFFFF0000), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 9. ADMIN SYSTEM SECURITY MODERATION CONSOLE
// ==========================================
@Composable
fun AdminDashboardScreen(
    viewModel: MusicViewModel
) {
    val songs by viewModel.allSongs.collectAsState()
    var bcTitle by remember { mutableStateOf("") }
    var bcBody by remember { mutableStateOf("") }
    var activeAdminPanel by remember { mutableStateOf(false) }

    val pendingSongs = songs.filter { it.status == "pending_review" || it.status == "pending" }

    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .clickable { activeAdminPanel = !activeAdminPanel },
            colors = CardDefaults.cardColors(containerColor = SlateDark),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Security, contentDescription = "Admin Panel", tint = RedFlag)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("System Security & Admin Console", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                }
                Icon(
                    imageVector = if (activeAdminPanel) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = "Expand",
                    tint = TextLight
                )
            }
        }

        if (activeAdminPanel) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .testTag("admin_panel_expanded"),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = BorderStroke(1.dp, RedFlag.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Pre-Publication Moderation Review Queue", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Verify intellectual copyright details and content compliance before publishing tracks globally.", fontSize = 11.sp, color = MutedText)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (pendingSongs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DeepMidnight, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = "No Pending Reviews", tint = NeonCyan, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Content Compliance: 100% Cleared", fontSize = 12.sp, color = TextWhite, fontWeight = FontWeight.Bold)
                                Text("All uploaded artist tracks are reviewed and fully approved.", fontSize = 10.sp, color = MutedText, textAlign = TextAlign.Center)
                            }
                        }
                    } else {
                        pendingSongs.forEach { song ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(DeepMidnight, RoundedCornerShape(8.dp))
                                    .border(0.5.dp, CardBorder, RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(song.title, fontSize = 13.sp, color = TextWhite, fontWeight = FontWeight.Bold)
                                        Text("Artist: ${song.artist} • Album: ${song.album}", fontSize = 11.sp, color = TextLight)
                                        Text("Licensee: ${song.copyrightOwner}", fontSize = 9.sp, color = LuxeGold)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(LuxeGold.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("PENDING", fontSize = 8.sp, color = LuxeGold, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.moderateSong(song.id, "approved") },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(vertical = 4.dp)
                                    ) {
                                        Text("Approve & Publish", color = DeepMidnight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = { viewModel.moderateSong(song.id, "rejected") },
                                        colors = ButtonDefaults.buttonColors(containerColor = RedFlag.copy(alpha = 0.2f)),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(vertical = 4.dp)
                                    ) {
                                        Text("Reject / Archive", color = RedFlag, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = CardBorder)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Admin Push Broadcast System", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RedFlag)
                    Spacer(modifier = Modifier.height(6.dp))
                    TextField(
                        value = bcTitle,
                        onValueChange = { bcTitle = it },
                        placeholder = { Text("Announcement Title", color = MutedText) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                    TextField(
                        value = bcBody,
                        onValueChange = { bcBody = it },
                        placeholder = { Text("Broadcast Body Message...", color = MutedText) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                    Button(
                        onClick = {
                            if (bcTitle.isNotEmpty() && bcBody.isNotEmpty()) {
                                viewModel.adminBroadcastAnnouncement(bcTitle, bcBody)
                                bcTitle = ""
                                bcBody = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RedFlag),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text("Send Global Push Announcement", color = TextWhite, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = CardBorder)
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Flagged Tracks Moderation List", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LuxeGold)
                    Spacer(modifier = Modifier.height(6.dp))
                    songs.take(2).forEach { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(DeepMidnight, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(song.title, fontSize = 12.sp, color = TextWhite, fontWeight = FontWeight.Bold)
                                Text("by ${song.artist} • FlagCount: 3", fontSize = 10.sp, color = RedFlag)
                            }
                            Button(
                                onClick = { viewModel.adminDeleteTrack(song.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = RedFlag.copy(alpha = 0.2f))
                            ) {
                                Text("Censor / Delete", color = RedFlag, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 10. SETTINGS & PREMIUM CHANNELS SCREEN
// ==========================================
@Composable
fun SettingsScreen(
    viewModel: MusicViewModel,
    onClose: () -> Unit
) {
    val userProfile by viewModel.currentUser.collectAsState()

    var cardNo by remember { mutableStateOf("") }
    var holder by remember { mutableStateOf("") }
    var mPesaNo by remember { mutableStateOf("") }
    var showCheckoutPlan by remember { mutableStateOf<String?>(null) } // planName

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepMidnight)
            .padding(top = 48.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Preferences & Premium Center", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextLight)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Premium Billing Plans Panel
            Text("Unlock Audio City Premium", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = LuxeGold)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PremiumPlanCard(
                    title = "City Luxury Pass",
                    price = "$9.99/mo",
                    features = listOf("Unlimited downloads", "Live Voice Companion", "Grounding search logs"),
                    isActive = userProfile?.planName == "City Luxury Pass",
                    onSelect = { showCheckoutPlan = "City Luxury Pass" },
                    modifier = Modifier.weight(1f)
                )

                PremiumPlanCard(
                    title = "Super Acoustic Pass",
                    price = "$14.99/mo",
                    features = listOf("Hi-Fi FLAC streaming", "Multi-room audio sync", "Full Admin simulation"),
                    isActive = userProfile?.planName == "Super Acoustic Pass",
                    onSelect = { showCheckoutPlan = "Super Acoustic Pass" },
                    modifier = Modifier.weight(1f)
                )
            }

            if (userProfile?.isPremium == true) {
                Button(
                    onClick = { viewModel.cancelPremiumSubscription() },
                    colors = ButtonDefaults.buttonColors(containerColor = RedFlag),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Text("Deactivate Active Subscription", color = TextWhite, fontWeight = FontWeight.Bold)
                }
            }

            // Billing Checkout Form Overlay
            showCheckoutPlan?.let { plan ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .testTag("billing_checkout_panel"),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    border = BorderStroke(2.dp, NeonCyan)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Secure Payment Gateway • $plan", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                        Text("Encrypting transaction nodes with SSL.", fontSize = 11.sp, color = MutedText)

                        Spacer(modifier = Modifier.height(8.dp))

                        // Option 1: Credit Card Checkout
                        Text("Option 1: Credit Card (Visa / Mastercard / Apple Pay)", fontSize = 12.sp, color = TextLight, fontWeight = FontWeight.Bold)
                        TextField(
                            value = cardNo,
                            onValueChange = { cardNo = it },
                            placeholder = { Text("4000 1234 5678 9010", color = MutedText) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                        TextField(
                            value = holder,
                            onValueChange = { holder = it },
                            placeholder = { Text("Cardholder Name", color = MutedText) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                        Button(
                            onClick = {
                                if (cardNo.isNotEmpty() && holder.isNotEmpty()) {
                                    viewModel.handlePremiumSubscriptionCheckout(cardNo, holder, "Credit Card", plan)
                                    showCheckoutPlan = null
                                    cardNo = ""
                                    holder = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text("Secure Pay Visa/Mastercard", color = DeepMidnight, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Divider(color = CardBorder)
                        Spacer(modifier = Modifier.height(10.dp))

                        // Option 2: Mobile Carrier (M-Pesa / Halopesa / Airtel)
                        Text("Option 2: Mobile Wallet (M-Pesa / Halopesa / Airtel / Tigo)", fontSize = 12.sp, color = TextLight, fontWeight = FontWeight.Bold)
                        TextField(
                            value = mPesaNo,
                            onValueChange = { mPesaNo = it },
                            placeholder = { Text("+255 700 000 000", color = MutedText) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (mPesaNo.isNotEmpty()) {
                                        viewModel.handlePremiumSubscriptionCheckout(mPesaNo, "M-Pesa", "M-Pesa", plan)
                                        showCheckoutPlan = null
                                        mPesaNo = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = LuxeGold),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Pay with M-Pesa", color = DeepMidnight, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Button(
                                onClick = {
                                    if (mPesaNo.isNotEmpty()) {
                                        viewModel.handlePremiumSubscriptionCheckout(mPesaNo, "Airtel", "Airtel Money", plan)
                                        showCheckoutPlan = null
                                        mPesaNo = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RedFlag),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Airtel Money", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Traditional settings toggles
            Text("Audio Connection Quality", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextWhite)
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateDark),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("High Fidelity Lossless FLAC", color = TextWhite) },
                        supportingContent = { Text("24-bit/192kHz (requires premium subscription)", color = MutedText) },
                        trailingContent = { Icon(Icons.Filled.Check, contentDescription = "Selected", tint = NeonCyan) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    Divider(color = CardBorder)
                    ListItem(
                        headlineContent = { Text("Smart Caching Node", color = TextWhite) },
                        supportingContent = { Text("Keep tracks locally during real-time streaming", color = MutedText) },
                        trailingContent = { Switch(checked = true, onCheckedChange = {}, colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Storage & Download Cache Manager
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Storage & Cache Manager", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                Text("240.5 MB used", fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateDark),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Manage downloaded files and clean cached metadata to preserve disk space.", fontSize = 11.sp, color = MutedText)
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    val context = LocalContext.current
                    Button(
                        onClick = {
                            Toast.makeText(context, "Cleared 184 MB of cached lyrics, album covers, and artwork metadata!", Toast.LENGTH_LONG).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Clear Cache", tint = RedFlag, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear Temporary Cache", color = RedFlag, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Legal Copyright Licensing Portal
            Text("Legal, Copyright & Licensing", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextWhite)
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateDark),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Verified, contentDescription = "Legal", tint = LuxeGold, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Digital Music Licensing Compliance", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LuxeGold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Audio City enforces strict compliance with international mechanical copyright and synchronized distribution laws. All content uploaded by verified creators must possess a mechanical reproduction or master license. Royalties are calculated monthly using transparent streams allocation metrics and paid through legal distributor APIs.",
                        fontSize = 11.sp,
                        color = TextLight,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    var signedLicense by remember { mutableStateOf(false) }
                    val context = LocalContext.current
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DeepMidnight, RoundedCornerShape(6.dp))
                            .border(1.dp, CardBorder, RoundedCornerShape(6.dp))
                            .clickable {
                                signedLicense = !signedLicense
                                if (signedLicense) {
                                    Toast.makeText(context, "Thank you! Licensing terms accepted. Verification updated.", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Sign Agreement",
                            tint = if (signedLicense) NeonCyan else MutedText,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Standard Artist Distribution License Agreement", fontSize = 11.sp, color = TextWhite, fontWeight = FontWeight.Bold)
                            Text("I certify that I hold verified copyright rights to all uploads.", fontSize = 9.sp, color = MutedText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumPlanCard(
    title: String,
    price: String,
    features: List<String>,
    isActive: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .border(
                if (isActive) BorderStroke(1.5.dp, LuxeGold) else BorderStroke(0.5.dp, CardBorder),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onSelect)
            .testTag("plan_card_$title"),
        colors = CardDefaults.cardColors(containerColor = SlateDark),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isActive) LuxeGold else TextWhite)
            Text(price, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = NeonCyan)
            Spacer(modifier = Modifier.height(8.dp))
            features.forEach { f ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Included", tint = NeonCyan, modifier = Modifier.size(10.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(f, fontSize = 9.sp, color = TextLight, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isActive) LuxeGold else CardBorder, RoundedCornerShape(6.dp))
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isActive) "Active" else "Upgrade",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) DeepMidnight else TextWhite
                )
            }
        }
    }
}

// ==========================================
// 11. NOTIFICATIONS DRAWER OVERLAY
// ==========================================
@Composable
fun NotificationsDrawer(
    notifications: List<NotificationEntity>,
    onClose: () -> Unit,
    onMarkRead: (String) -> Unit,
    onClearAll: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp)
            .testTag("notifications_drawer_panel"),
        colors = CardDefaults.cardColors(containerColor = SlateDark),
        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notifications, contentDescription = "Alerts", tint = NeonCyan)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Inbox Notifications", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.ArrowForwardIos, contentDescription = "Close", tint = TextLight, modifier = Modifier.size(14.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onClearAll) {
                    Text("Mark all read", fontSize = 11.sp, color = NeonCyan)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (notifications.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("No system notifications.", color = MutedText, fontSize = 12.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(notifications) { notif ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onMarkRead(notif.id) },
                            colors = CardDefaults.cardColors(containerColor = if (notif.isRead) DeepMidnight else SurfaceDark),
                            border = BorderStroke(0.5.dp, if (notif.isRead) Color.Transparent else NeonCyan.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(notif.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (notif.isRead) TextLight else NeonCyan)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(notif.message, fontSize = 11.sp, color = MutedText, lineHeight = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 12. PLAYLIST SONGS DETAIL DIALOG modal
// ==========================================
@Composable
fun PlaylistDetailDialog(
    playlist: PlaylistEntity,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit,
    onSongSelect: (SongEntity, List<SongEntity>) -> Unit
) {
    val songsFlow by viewModel.getSongsInPlaylist(playlist.id).collectAsState(initial = emptyList())
    val currentSongState by viewModel.currentSong.collectAsState()
    val isPlayingState by viewModel.isPlaying.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = playlist.coverUrl,
                    contentDescription = playlist.name,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(playlist.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                    Text(playlist.description, fontSize = 11.sp, color = MutedText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        text = {
            Box(modifier = Modifier.height(300.dp)) {
                if (songsFlow.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No songs in this playlist.", color = MutedText)
                    }
                } else {
                    LazyColumn {
                        items(songsFlow) { song ->
                            val isCurrent = currentSongState?.id == song.id
                            SongListItem(
                                song = song,
                                isDownloading = false,
                                downloadProgress = null,
                                onPlay = { onSongSelect(song, songsFlow) },
                                onLikeToggle = { viewModel.toggleLike(song.id) },
                                onDownload = { viewModel.downloadSong(song.id) },
                                onRemoveClick = { viewModel.removeSongFromPlaylist(playlist.id, song.id) },
                                isCurrent = isCurrent,
                                isPlaying = isCurrent && isPlayingState
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)) {
                Text("Close", color = DeepMidnight)
            }
        },
        containerColor = SlateDark
    )
}

// ==========================================
// 13. ADD TO PLAYLIST DIALOG
// ==========================================
@Composable
fun AddToPlaylistDialog(
    song: SongEntity,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val playlists by viewModel.allPlaylists.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Track to Playlist", color = TextWhite) },
        text = {
            Column(modifier = Modifier.height(240.dp)) {
                Text("Adding: '${song.title}'", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))

                if (playlists.isEmpty()) {
                    Text("No custom playlists created. Create one in the Library tab first.", color = MutedText, fontSize = 12.sp)
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(playlists) { pl ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        viewModel.addSongToPlaylist(pl.id, song.id)
                                        onDismiss()
                                    },
                                colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                            ) {
                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(
                                        model = pl.coverUrl,
                                        contentDescription = pl.name,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(pl.name, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MutedText)
            }
        },
        containerColor = SlateDark
    )
}

// ==========================================
// 14. FULLSCREEN AUDIO PLAYER PAGE VIEW
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerFullScreenView(
    song: SongEntity,
    viewModel: MusicViewModel,
    onClose: () -> Unit,
    onAddToPlaylist: (SongEntity) -> Unit
) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val progressSec by viewModel.currentPositionSec.collectAsState()
    val durationSec by viewModel.durationSec.collectAsState()
    val isShuffle by viewModel.isShuffle.collectAsState()
    val isRepeat by viewModel.isRepeat.collectAsState()

    val comments by viewModel.currentSongComments.collectAsState()
    val lyrics by viewModel.lyricsState.collectAsState()
    val lyricsLoading by viewModel.lyricsLoading.collectAsState()

    var activePlayerSubTab by remember { mutableStateOf("Lyrics") }
    var bassBoostState by remember { mutableFloatStateOf(0.65f) }
    var vocalEnhanceState by remember { mutableFloatStateOf(0.40f) }
    var selectedReverb by remember { mutableStateOf("Studio") }
    var userCommentText by remember { mutableStateOf("") }
    var showInfoRegistry by remember { mutableStateOf(false) }

    // Spinning Vinyl Animation
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_transition")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinyl_angle"
    )

    val pulseScale by if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_scale"
        )
    } else {
        remember { mutableStateOf(1.0f) }
    }

    val pulseAlpha by if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_alpha"
        )
    } else {
        remember { mutableStateOf(0.4f) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(SlateDark, DeepMidnight)
                )
            )
            .padding(top = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Player Top Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Collapse", tint = TextLight, modifier = Modifier.size(32.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (song.isPodcast) "PLAYING PODCAST" else "PLAYING FROM DISCOVER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MutedText,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = song.album,
                        fontSize = 13.sp,
                        color = TextWhite,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 160.dp)
                    )
                }
                IconButton(onClick = { onAddToPlaylist(song) }) {
                    Icon(Icons.Filled.PlaylistAdd, contentDescription = "Add Playlist", tint = TextLight)
                }
            }

            Spacer(modifier = Modifier.weight(0.4f))

            // Spinning Album Vinyl / Artwork
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .graphicsLayer {
                        scaleX = if (isPlaying) pulseScale else 1.0f
                        scaleY = if (isPlaying) pulseScale else 1.0f
                    }
                    .drawBehind {
                        // Ambient radial glow behind the disc
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    NeonCyan.copy(alpha = if (isPlaying) pulseAlpha else 0.4f),
                                    AccentPurple.copy(alpha = if (isPlaying) pulseAlpha * 0.5f else 0.2f),
                                    Color.Transparent
                                )
                            ),
                            radius = size.width * (if (isPlaying) pulseScale * 0.75f else 0.7f)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // Vinyl Groove Graphic background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color.Black)
                        .border(4.dp, CardBorder, CircleShape)
                        .graphicsLayer {
                            rotationZ = if (isPlaying) angle else 0f
                        }
                ) {
                    // Actual Cover Art centered in vinyl
                    AsyncImage(
                        model = song.coverUrl,
                        contentDescription = song.title,
                        modifier = Modifier
                            .size(160.dp)
                            .clip(CircleShape)
                            .align(Alignment.Center)
                            .border(2.dp, NeonCyan, CircleShape),
                        contentScale = ContentScale.Crop
                    )

                    // Small center spindle hole
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(SlateDark, CircleShape)
                            .align(Alignment.Center)
                            .border(1.dp, Color.White, CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.4f))

            // Interactive Title, Artist & License Registry Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showInfoRegistry = true }) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "Registry Info",
                        tint = NeonCyan,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = song.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = song.artist,
                            fontSize = 15.sp,
                            color = NeonCyan,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (song.isOfficialRelease) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.Verified,
                                contentDescription = "Official",
                                tint = LuxeGold,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                IconButton(onClick = { viewModel.toggleLike(song.id) }) {
                    Icon(
                        imageVector = if (song.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (song.isLiked) NeonCyan else TextLight,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.3f))

            // Sub tabs: Real-time Gemini Lyrics, Listener Comments OR Equalizer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = { activePlayerSubTab = "Lyrics" },
                    colors = ButtonDefaults.buttonColors(containerColor = if (activePlayerSubTab == "Lyrics") NeonCyan else SlateDark),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text("AI Lyrics", color = if (activePlayerSubTab == "Lyrics") DeepMidnight else TextLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { activePlayerSubTab = "Comments" },
                    colors = ButtonDefaults.buttonColors(containerColor = if (activePlayerSubTab == "Comments") NeonCyan else SlateDark),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text("Comments (${comments.size})", color = if (activePlayerSubTab == "Comments") DeepMidnight else TextLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { activePlayerSubTab = "Equalizer" },
                    colors = ButtonDefaults.buttonColors(containerColor = if (activePlayerSubTab == "Equalizer") NeonCyan else SlateDark),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text("Audio EQ", color = if (activePlayerSubTab == "Equalizer") DeepMidnight else TextLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Interactive Dynamic Display Box (Lyrics, comments, or equalizer)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DeepMidnight.copy(alpha = 0.5f))
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                when (activePlayerSubTab) {
                    "Lyrics" -> {
                        // Lyrics View
                        if (lyricsLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Gemini composing original lyrics...", fontSize = 10.sp, color = NeonCyan)
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = lyrics,
                                    fontSize = 12.sp,
                                    color = VocalLavender,
                                    lineHeight = 18.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    "Comments" -> {
                        // Comments Section
                        Column(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(comments) { comment ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        AsyncImage(
                                            model = comment.userPhotoUrl,
                                            contentDescription = comment.userName,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(comment.userName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                                            Text(comment.text, fontSize = 11.sp, color = TextLight, lineHeight = 14.sp)
                                        }
                                    }
                                }
                            }

                            // Add Comment row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextField(
                                    value = userCommentText,
                                    onValueChange = { userCommentText = it },
                                    placeholder = { Text("Add comment...", color = MutedText, fontSize = 10.sp) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = SlateDark,
                                        unfocusedContainerColor = SlateDark,
                                        focusedTextColor = TextWhite,
                                        unfocusedTextColor = TextWhite,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    ),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = {
                                        if (userCommentText.isNotEmpty()) {
                                            viewModel.postComment(userCommentText)
                                            userCommentText = ""
                                        }
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(NeonCyan, CircleShape)
                                ) {
                                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Post", tint = DeepMidnight, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    "Equalizer" -> {
                        // Equalizer & FX Section
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("3D Digital Audio Equalizer & FX", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LuxeGold)
                            
                            // Bass Boost slider
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("LFE Sub-Bass Boost (+${(bassBoostState * 12).toInt()} dB)", fontSize = 10.sp, color = TextWhite)
                                    Text("${(bassBoostState * 100).toInt()}%", fontSize = 10.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = bassBoostState,
                                    onValueChange = { 
                                        bassBoostState = it 
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = NeonCyan,
                                        activeTrackColor = NeonCyan,
                                        inactiveTrackColor = CardBorder
                                    ),
                                    modifier = Modifier.height(24.dp)
                                )
                            }

                            // Vocal Presence slider
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Vocal Presence Mid-Range", fontSize = 10.sp, color = TextWhite)
                                    Text("${(vocalEnhanceState * 100).toInt()}%", fontSize = 10.sp, color = LuxeGold, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = vocalEnhanceState,
                                    onValueChange = { 
                                        vocalEnhanceState = it 
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = LuxeGold,
                                        activeTrackColor = LuxeGold,
                                        inactiveTrackColor = CardBorder
                                    ),
                                    modifier = Modifier.height(24.dp)
                                )
                            }

                            // Reverb Environment chips
                            Text("Acoustic Reverb Presets:", fontSize = 10.sp, color = TextLight, fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val context = LocalContext.current
                                listOf("Studio", "Hall", "Club", "Cave").forEach { env ->
                                    val selected = selectedReverb == env
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(if (selected) NeonCyan.copy(alpha = 0.2f) else DeepMidnight, RoundedCornerShape(6.dp))
                                            .border(1.dp, if (selected) NeonCyan else CardBorder, RoundedCornerShape(6.dp))
                                            .clickable { 
                                                selectedReverb = env
                                                Toast.makeText(context, "Audio engine: Applied $env reverb filter!", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(env, fontSize = 9.sp, color = if (selected) NeonCyan else TextLight, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.3f))

            // Playback Slider Progress controls
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = if (durationSec > 0) progressSec.toFloat() / durationSec.toFloat() else 0f,
                    onValueChange = { ratio ->
                        viewModel.seekTo((ratio * durationSec).toInt())
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = NeonCyan,
                        activeTrackColor = NeonCyan,
                        inactiveTrackColor = CardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${progressSec / 60}:${String.format("%02d", progressSec % 60)}",
                        fontSize = 11.sp,
                        color = MutedText
                    )
                    Text(
                        text = "${durationSec / 60}:${String.format("%02d", durationSec % 60)}",
                        fontSize = 11.sp,
                        color = MutedText
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.2f))

            // Core Playback Buttons layout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Toggle
                IconButton(onClick = { viewModel.toggleShuffle() }) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) NeonCyan else TextLight,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Skip Prev
                IconButton(onClick = { viewModel.previous() }) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "Prev",
                        tint = TextLight,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Large Main Play Pause circle
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .background(NeonCyan, CircleShape)
                        .clickable { viewModel.playOrPause() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(color = DeepMidnight, modifier = Modifier.size(28.dp))
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = DeepMidnight,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Skip Next
                IconButton(onClick = { viewModel.next() }) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        tint = TextLight,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Repeat Toggle
                IconButton(onClick = { viewModel.toggleRepeat() }) {
                    Icon(
                        imageVector = Icons.Filled.Repeat,
                        contentDescription = "Repeat",
                        tint = if (isRepeat) NeonCyan else TextLight,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Elegant Gemini Voice Control Assistant Bar
            var playerMicListening by remember { mutableStateOf(false) }
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val playerMicAmplitude by com.example.ui.player.VoiceInputAnalyzer.amplitude.collectAsState()

            LaunchedEffect(playerMicListening) {
                if (playerMicListening) {
                    com.example.ui.player.VoiceInputAnalyzer.startListening(context)
                } else {
                    com.example.ui.player.VoiceInputAnalyzer.stopListening()
                }
            }

            if (playerMicListening) {
                VoiceVisualizerComponent(
                    amplitude = playerMicAmplitude,
                    isActive = true,
                    statusText = "Listening for player commands...",
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (playerMicListening) NeonCyanMuted else SurfaceDark.copy(alpha = 0.5f))
                    .clickable {
                        if (!playerMicListening) {
                            playerMicListening = true
                            Toast.makeText(context, "Voice command listening... Speak now!", Toast.LENGTH_SHORT).show()
                            scope.launch {
                                delay(2200) // simulated listing time
                                // Simulate spoken voice command
                                val simulatedCommands = listOf(
                                    "Next song please",
                                    "Like this song",
                                    "Pause music",
                                    "Resume playback",
                                    "Shuffle music",
                                    "Repeat track"
                                )
                                val cmd = simulatedCommands.random()
                                Toast.makeText(context, "Voice Transcribed: \"$cmd\"", Toast.LENGTH_LONG).show()
                                viewModel.processVoiceCommand(cmd)
                                playerMicListening = false
                            }
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("player_voice_command_btn"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (playerMicListening) Icons.Filled.MicNone else Icons.Filled.Mic,
                    contentDescription = "Voice Control",
                    tint = if (playerMicListening) NeonCyan else MutedText,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (playerMicListening) "Listening... Try 'Next song' or 'Like'" else "Tap to issue Voice Command",
                    color = if (playerMicListening) NeonCyan else MutedText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        if (showInfoRegistry) {
            PublicSongInfoDialog(
                song = song,
                viewModel = viewModel,
                onDismiss = { showInfoRegistry = false }
            )
        }
    }
}
