package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.attemptAutoSignIn
import com.example.auth.onGoogleSignInClicked
import com.example.ui.components.PhotonCardShape
import com.example.ui.components.photonPressable
import com.example.ui.theme.PhotonAccentTeal
import com.example.ui.theme.PhotonBodyMediumStyle
import com.example.ui.theme.PhotonBodyStyle
import com.example.ui.theme.PhotonCanvas
import com.example.ui.theme.PhotonCaptionStyle
import com.example.ui.theme.PhotonOnAccent
import com.example.ui.theme.PhotonPrimaryText
import com.example.ui.theme.PhotonSecondaryText
import com.example.ui.theme.PhotonSurfaceCard
import com.example.ui.theme.PhotonSurfaceHigh
import com.example.ui.theme.PhotonTitleStyle

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isSigningIn by remember { mutableStateOf(false) }
    var isCheckingAutoSignIn by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isCheckingAutoSignIn = false
                onAuthSuccess()
            },
            onUnauthenticated = {
                isCheckingAutoSignIn = false
            },
            scope = scope
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PhotonCanvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("auth_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Flat AMOLED Photon Emblem (zero gradients, circular primary play mark)
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(PhotonAccentTeal),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.app_name),
                    tint = PhotonOnAccent,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.auth_welcome_title),
                style = PhotonTitleStyle,
                color = PhotonPrimaryText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.auth_welcome_subtitle),
                style = PhotonBodyStyle,
                color = PhotonSecondaryText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Flat tonal benefit cards (12dp radius, zero borders/gradients)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AuthFeatureCard(
                    icon = Icons.Outlined.Subscriptions,
                    title = stringResource(R.string.auth_feature_youtube_title),
                    description = stringResource(R.string.auth_feature_youtube_desc)
                )
                AuthFeatureCard(
                    icon = Icons.Outlined.LibraryMusic,
                    title = stringResource(R.string.auth_feature_music_title),
                    description = stringResource(R.string.auth_feature_music_desc)
                )
                AuthFeatureCard(
                    icon = Icons.Outlined.CloudDone,
                    title = stringResource(R.string.auth_feature_cloud_title),
                    description = stringResource(R.string.auth_feature_cloud_desc)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(150))
            ) {
                errorMessage?.let { msg ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .clip(PhotonCardShape)
                            .background(PhotonSurfaceHigh)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("auth_error_banner")
                    ) {
                        Text(
                            text = msg,
                            style = PhotonCaptionStyle,
                            color = PhotonPrimaryText
                        )
                    }
                }
            }

            // Prominent interactive Google Sign-In CTA (strictly flat high-contrast white on AMOLED)
            val buttonBusy = isSigningIn || isCheckingAutoSignIn
            val signInInteraction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .photonPressable(signInInteraction)
                    .clip(PhotonCardShape)
                    .background(if (buttonBusy) PhotonSurfaceHigh else PhotonPrimaryText)
                    .clickable(
                        interactionSource = signInInteraction,
                        indication = ripple(),
                        enabled = !buttonBusy
                    ) {
                        errorMessage = null
                        isSigningIn = true
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isSigningIn = false
                                onAuthSuccess()
                            },
                            onAuthError = { msg ->
                                isSigningIn = false
                                errorMessage = msg
                            },
                            scope = scope,
                            onAuthCancelled = {
                                isSigningIn = false
                                errorMessage = "Sign-in was cancelled. Tap below to choose your Google account."
                            }
                        )
                    }
                    .testTag("google_sign_in_button"),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (buttonBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = PhotonPrimaryText,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.auth_signing_in),
                        style = PhotonBodyMediumStyle,
                        color = PhotonPrimaryText
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.AccountCircle,
                        contentDescription = null,
                        tint = PhotonCanvas,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.auth_sign_in_google),
                        style = PhotonBodyMediumStyle,
                        color = PhotonCanvas
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.auth_privacy_note),
                style = PhotonCaptionStyle,
                color = PhotonSecondaryText,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AuthFeatureCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PhotonCardShape)
            .background(PhotonSurfaceCard)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(PhotonCardShape)
                .background(PhotonSurfaceHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PhotonPrimaryText,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = PhotonBodyMediumStyle,
                color = PhotonPrimaryText
            )
            Text(
                text = description,
                style = PhotonCaptionStyle,
                color = PhotonSecondaryText
            )
        }
    }
}
