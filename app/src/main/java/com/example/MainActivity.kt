package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.NiraxRepository
import com.example.data.PublicProfile
import com.example.data.UiState
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.signOut
import com.example.ui.screens.ChatThreadScreen
import com.example.ui.screens.ChatsScreen
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.FeedScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.theme.NiraxTheme
import com.example.viewmodel.NiraxViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private const val ROUTE_FEED = "feed"
private const val ROUTE_DISCOVER = "discover"
private const val ROUTE_CHATS = "chats"
private const val ROUTE_PROFILE = "profile"
private const val ROUTE_CHAT_THREAD = "chat_thread"

private data class NiraxNavTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val NiraxTabs = listOf(
    NiraxNavTab(ROUTE_FEED, R.string.nav_feed, Icons.Filled.DynamicFeed, Icons.Outlined.DynamicFeed, "nav_feed"),
    NiraxNavTab(ROUTE_DISCOVER, R.string.nav_discover, Icons.Filled.Explore, Icons.Outlined.Explore, "nav_discover"),
    NiraxNavTab(ROUTE_CHATS, R.string.nav_chats, Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat, "nav_chats"),
    NiraxNavTab(ROUTE_PROFILE, R.string.nav_profile, Icons.Filled.Person, Icons.Outlined.Person, "nav_profile")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NiraxTheme {
                NiraxAppRoot()
            }
        }
    }
}

@Composable
fun NiraxAppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = { currentUser = Firebase.auth.currentUser },
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        val uid = user.uid
        key(uid) {
            NiraxAuthenticatedShell(
                currentUserId = uid,
                onSignOut = {
                    signOut(
                        credentialManager = credentialManager,
                        scope = scope,
                        onSignOutComplete = { currentUser = null }
                    )
                }
            )
        }
    }
}

@Composable
private fun NiraxAuthenticatedShell(
    currentUserId: String,
    onSignOut: () -> Unit
) {
    val viewModel: NiraxViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                NiraxViewModel(NiraxRepository(db), currentUserId)
            }
        }
    )

    val myProfileState by viewModel.myProfileState.collectAsStateWithLifecycle()
    val profilesState by viewModel.profilesState.collectAsStateWithLifecycle()
    val followingIds by viewModel.followingIds.collectAsStateWithLifecycle()
    val likedPostIds by viewModel.likedPostIds.collectAsStateWithLifecycle()
    val postsState by viewModel.postsState.collectAsStateWithLifecycle()
    val commentsState by viewModel.commentsState.collectAsStateWithLifecycle()
    val conversationsState by viewModel.conversationsState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val isMutating by viewModel.isMutating.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_FEED
    var activeChatRecipient by remember { mutableStateOf<PublicProfile?>(null) }

    val openChatWith: (PublicProfile) -> Unit = { recipient ->
        activeChatRecipient = recipient
        viewModel.openConversationThread(recipient.userId)
        navController.navigate(ROUTE_CHAT_THREAD)
    }

    val showTopLevelNav = currentRoute != ROUTE_CHAT_THREAD

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (showTopLevelNav && !useNavRail) {
                    NavigationBar {
                        NiraxTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            val label = stringResource(tab.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showTopLevelNav && useNavRail) {
                    NavigationRail {
                        NiraxTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            val label = stringResource(tab.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_FEED
                    ) {
                        composable(ROUTE_FEED) {
                            val myProfile = (myProfileState as? UiState.Success)?.data
                            FeedScreen(
                                currentUserId = currentUserId,
                                myProfile = myProfile,
                                postsState = postsState,
                                commentsState = commentsState,
                                likedPostIds = likedPostIds,
                                followingIds = followingIds,
                                isMutating = isMutating,
                                onCreatePost = viewModel::createPost,
                                onEditPost = viewModel::editPost,
                                onDeletePost = viewModel::deletePost,
                                onToggleLike = viewModel::toggleLike,
                                onOpenThread = viewModel::selectPostForThread,
                                onAddComment = viewModel::addComment,
                                onDeleteComment = viewModel::deleteComment,
                                onMessageUser = { uid, name, handle, avatarIndex ->
                                    openChatWith(
                                        PublicProfile(
                                            userId = uid,
                                            displayName = name,
                                            handle = handle,
                                            avatarIndex = avatarIndex
                                        )
                                    )
                                },
                                onProfileClick = {
                                    navController.navigate(ROUTE_PROFILE) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }

                        composable(ROUTE_DISCOVER) {
                            DiscoverScreen(
                                currentUserId = currentUserId,
                                profilesState = profilesState,
                                followingIds = followingIds,
                                onToggleFollow = viewModel::toggleFollow,
                                onStartChat = openChatWith
                            )
                        }

                        composable(ROUTE_CHATS) {
                            ChatsScreen(
                                currentUserId = currentUserId,
                                conversationsState = conversationsState,
                                profilesState = profilesState,
                                onOpenConversation = openChatWith
                            )
                        }

                        composable(ROUTE_PROFILE) {
                            ProfileScreen(
                                currentUserId = currentUserId,
                                profileState = myProfileState,
                                postsState = postsState,
                                commentsState = commentsState,
                                followingCount = followingIds.size,
                                likedPostIds = likedPostIds,
                                isMutating = isMutating,
                                onUpdateProfile = viewModel::updateProfile,
                                onToggleLike = viewModel::toggleLike,
                                onOpenThread = viewModel::selectPostForThread,
                                onAddComment = viewModel::addComment,
                                onDeleteComment = viewModel::deleteComment,
                                onEditPost = viewModel::editPost,
                                onDeletePost = viewModel::deletePost,
                                onSignOut = onSignOut
                            )
                        }

                        composable(ROUTE_CHAT_THREAD) {
                            val recipient = activeChatRecipient
                            if (recipient == null) {
                                LaunchedEffect(Unit) {
                                    navController.popBackStack()
                                }
                            } else {
                                ChatThreadScreen(
                                    currentUserId = currentUserId,
                                    recipient = recipient,
                                    messagesState = messagesState,
                                    isMutating = isMutating,
                                    onSendMessage = { text, onDone ->
                                        viewModel.sendMessage(recipient, text, onDone)
                                    },
                                    onBack = {
                                        viewModel.closeConversationThread()
                                        navController.popBackStack()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
