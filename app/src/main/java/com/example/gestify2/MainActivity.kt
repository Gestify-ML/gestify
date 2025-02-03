package com.example.gestify2
import android.media.AudioManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    private val clientId = "b233af9168d145bf89e3fa3b03f8f334"
    private val redirectUri = "com.example.gestify2://callback"
    private var spotifyAppRemote: SpotifyAppRemote? = null

    private val trackInfo = mutableStateOf("Track Info: ")
    private val trackState = mutableStateOf("Not playing")

    private val trackUris = listOf(
        "spotify:track:4xdBrk0nFZaP54vvZj0yx7", // Hot To Go
        "spotify:track:2PmMh2t7jAtN6cqFooA0Xy", // Jolene
        "spotify:track:2Y8BloifAHEn6GproQgPs7", // Silver Springs
        "spotify:track:0RW1UL8w8rjQkaIaljaFc5", // Photo ID
        "spotify:track:6dBUzqjtbnIa1TwYbyw5CM"  // Lover's Rock
    )

    private val spotifyAuthLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val response = AuthorizationClient.getResponse(result.resultCode, result.data)
        when (response.type) {
            AuthorizationResponse.Type.TOKEN -> {
                val accessToken = response.accessToken
                connectToSpotify(accessToken)
            }
            AuthorizationResponse.Type.ERROR -> {
                Toast.makeText(this, "Authorization Error: ${response.error}", Toast.LENGTH_SHORT).show()
            }
            else -> { }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent{
            MainScreen(trackInfo, trackState, ::playRandomTrack, ::pauseTrack, ::resumeTrack, ::skipTrack, ::rewindTrack, ::volumeUp, ::volumeDown, ::mute, ::unmute)
        }
        initiateSpotifyLogin()
    }

    private fun initiateSpotifyLogin() {
        val builder = AuthorizationRequest.Builder(clientId, AuthorizationResponse.Type.TOKEN, redirectUri)
        builder.setScopes(arrayOf("streaming"))
        val request = builder.build()
        val authIntent = AuthorizationClient.createLoginActivityIntent(this, request)
        spotifyAuthLauncher.launch(authIntent)
    }

    private fun connectToSpotify(accessToken: String) {
        val connectionParams = ConnectionParams.Builder(clientId)
            .setRedirectUri(redirectUri)
            .showAuthView(true)
            .build()

        SpotifyAppRemote.connect(this, connectionParams, object : Connector.ConnectionListener {
            override fun onConnected(spotifyAppRemote: SpotifyAppRemote) {
                this@MainActivity.spotifyAppRemote = spotifyAppRemote
                subscribeToPlayerState()
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

    private fun pauseTrack() {
        spotifyAppRemote?.playerApi?.pause()?.setResultCallback {
            Log.d("Gestify", "Track paused")
            trackState.value = "Paused"
        }?.setErrorCallback {
            Log.e("Gestify", "Error pausing track: ${it.message}")
        }
    }

    private fun volumeUp() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
    }

    private fun volumeDown() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
    }

    private fun mute() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
    }

    private fun unmute() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val defaultVolume = maxVolume / 2 // Set it to 50% of max volume
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, defaultVolume, AudioManager.FLAG_SHOW_UI)
    }

    private fun rewindTrack() {
        spotifyAppRemote?.playerApi?.seekToRelativePosition(-5000) // Rewind 5 seconds
    }

    private fun resumeTrack() {
        spotifyAppRemote?.playerApi?.resume()?.setResultCallback {
            Log.d("Gestify", "Track resumed")
            trackState.value = "Playing"
        }?.setErrorCallback {
            Log.e("Gestify", "Error resuming track: ${it.message}")
        }
    }

    private fun skipTrack() {
        spotifyAppRemote?.playerApi?.skipNext()?.setResultCallback {
            Log.d("Gestify", "Skipped to next track")
        }?.setErrorCallback {
            Log.e("Gestify", "Error skipping track: ${it.message}")
        }
    }

    private fun subscribeToPlayerState() {
        spotifyAppRemote?.playerApi?.subscribeToPlayerState()?.setEventCallback { playerState ->
            playerState.track?.let { track ->
                runOnUiThread {
                    trackInfo.value = "Now Playing: ${track.name} by ${track.artist.name}"
                    trackState.value = if (playerState.isPaused) "Paused" else "Playing"
                }
            }
        }?.setErrorCallback {
            Log.e("Gestify", "Error subscribing to player state: ${it.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        spotifyAppRemote?.let { SpotifyAppRemote.disconnect(it) }
    }
}
@Composable
fun MainScreen(
    trackInfo: MutableState<String>,
    trackState: MutableState<String>,
    playRandomTrack: () -> Unit,
    pauseTrack: () -> Unit,
    resumeTrack: () -> Unit,
    skipTrack: () -> Unit,
    rewindTrack: () -> Unit,
    volumeUp: () -> Unit,
    volumeDown: () -> Unit,
    mute: () -> Unit,
    unmute: () -> Unit
) {
    Scaffold(
        content = { paddingValues ->

            Column(modifier = Modifier.padding(paddingValues).padding(16.dp)) {
                Text(text = "Gestify - Spotify Controller")
                Text(text = trackState.value)
                Text(text = trackInfo.value)

                Spacer(modifier = Modifier.height(16.dp))

                // Row 1: Play Random
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(onClick = { playRandomTrack() }, modifier = Modifier.weight(1f)) {
                        Text(text = "Play Random")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Row 2: Pause, Play, Skip
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(onClick = { pauseTrack() }, modifier = Modifier.weight(1f)) {
                        Text(text = "Pause")
                    }
                    Button(onClick = { resumeTrack() }, modifier = Modifier.weight(1f)) {
                        Text(text = "Play")
                    }
                    Button(onClick = { skipTrack() }, modifier = Modifier.weight(1f)) {
                        Text(text = "Skip")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Row 3: Rewind, Volume Up, Volume Down
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(onClick = { rewindTrack() }, modifier = Modifier.weight(1f)) {
                        Text(text = "Rewind")
                    }

                    Button(onClick = { volumeUp() }, modifier = Modifier.weight(1f)) {
                        Text(text = "Vol +")
                    }
                    Button(onClick = { volumeDown() }, modifier = Modifier.weight(1f)) {
                        Text(text = "Vol -")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Row 4: Mute and Unmute
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(onClick = { mute() }, modifier = Modifier.weight(1f)) {
                        Text(text = "Mute")
                    }
                    Button(onClick = { unmute() }, modifier = Modifier.weight(1f)) {
                        Text(text = "Unmute")
                    }
                }
            }
        }
    )
}