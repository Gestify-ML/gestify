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

    private val trackUris = listOf(
        "spotify:track:4xdBrk0nFZaP54vvZj0yx7", // Hot To Go
        "spotify:track:2PmMh2t7jAtN6cqFooA0Xy", // Jolene
        "spotify:track:2Y8BloifAHEn6GproQgPs7", // Silver Springs
        "spotify:track:0RW1UL8w8rjQkaIaljaFc5", // Photo ID
        "spotify:track:6dBUzqjtbnIa1TwYbyw5CM"  // Lover's Rock
    )

    private val trackInfo = mutableStateOf("Track Info: ")
    private val trackState = mutableStateOf("Not playing")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MainScreen(trackInfo, trackState)
        }

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
    private fun subscribeToPlayerState() {
        spotifyAppRemote?.playerApi?.subscribeToPlayerState()?.setEventCallback { playerState ->
            playerState.track?.let { track ->
                runOnUiThread {
                    trackInfo.value = "Now Playing: ${track.name} by ${track.artist.name}"
                    trackState.value = "Playing"
                }
            }
        }?.setErrorCallback {
            Log.e("Gestify", "Error subscribing to player state: ${it.message}")
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
                subscribeToPlayerState()  // Subscribe to track changes after connecting
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
    fun MainScreen(trackInfo: MutableState<String>, trackState: MutableState<String>) {
        Scaffold(
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
