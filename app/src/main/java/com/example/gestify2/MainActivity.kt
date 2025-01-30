package com.example.gestify2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.Track
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    private val clientId = "b233af9168d145bf89e3fa3b03f8f334"
    private val redirectUri = "com.example.gestify2://callback"
    private val REQUEST_CODE = 1337

    private var spotifyAppRemote: SpotifyAppRemote? = null

    // Track URIs to choose from
    private val trackUris = listOf(
        "spotify:track:11dFghVXANMlKmJXsNCbNl", // Example track 1
        "spotify:track:11dFghVXANMlKmJXsNCbNl", // Example track 2
        "spotify:track:11dFghVXANMlKmJXsNCbNl", // Example track 3
        "spotify:track:11dFghVXANMlKmJXsNCbNl", // Example track 4
        "spotify:track:11dFghVXANMlKmJXsNCbNl"  // Example track 5
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize composable content
        setContent {
            MainScreen()
        }

        // Call the function to initiate the login process
        initiateSpotifyLogin()
    }

    private fun initiateSpotifyLogin() {
        val builder = AuthorizationRequest.Builder(clientId, AuthorizationResponse.Type.TOKEN, redirectUri)
        builder.setScopes(arrayOf("streaming"))
        val request = builder.build()

        AuthorizationClient.openLoginActivity(this, REQUEST_CODE, request)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE) {
            val response = AuthorizationClient.getResponse(resultCode, data)

            when (response.type) {
                AuthorizationResponse.Type.TOKEN -> {
                    val accessToken = response.accessToken
                    connectToSpotify(accessToken)
                }
                AuthorizationResponse.Type.ERROR -> {
                    Toast.makeText(this, "Authorization Error: ${response.error}", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    // Handle other cases
                }
            }
        }
    }

    private fun connectToSpotify(accessToken: String) {
        val connectionParams = ConnectionParams.Builder(clientId)
            .setRedirectUri(redirectUri)
            .showAuthView(true)
            .build()

        SpotifyAppRemote.connect(this, connectionParams, object : Connector.ConnectionListener {
            override fun onConnected(spotifyAppRemote: SpotifyAppRemote) {
                this@MainActivity.spotifyAppRemote = spotifyAppRemote
                playRandomTrack()
            }

            override fun onFailure(throwable: Throwable) {
                Toast.makeText(this@MainActivity, "Connection Failed: ${throwable.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun playRandomTrack() {
        val randomTrackUri = trackUris[Random.nextInt(trackUris.size)]
        spotifyAppRemote?.playerApi?.play(randomTrackUri)?.setResultCallback {
            Log.d("Gestify", "Random track is playing!")
        }?.setErrorCallback {
            Log.e("Gestify", "Error playing track: ${it.message}")
        }
    }

    // Composable function to display the UI
    @Composable
    fun MainScreen() {
        // Remember track state (whether it's playing)
        val trackState = remember { mutableStateOf("Not playing") }
        val trackInfo = remember { mutableStateOf("Track Info: ") }

        // Subscribe to track changes
        spotifyAppRemote?.playerApi?.subscribeToPlayerState()?.setEventCallback {
            it.track?.let { track ->
                trackInfo.value = "Now Playing: ${track.name} by ${track.artist.name}"
                trackState.value = "Playing"
            }
        }

        Scaffold(
            topBar = {
                // You can add a TopAppBar here if needed
            },
            content = { paddingValues ->
                Column(modifier = Modifier.padding(paddingValues).padding(16.dp)) {
                    Text(text = "Gestify - Spotify Controller")
                    Text(text = trackState.value)  // Shows if track is playing or not
                    Text(text = trackInfo.value)  // Shows current track info
                    Button(onClick = { playRandomTrack() }) {
                        Text(text = "Play Random Track")
                    }
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure SpotifyAppRemote is disconnected when the activity is destroyed
        spotifyAppRemote?.let {
            SpotifyAppRemote.disconnect(it)
        }
    }
}
