package com.example.gestify

import android.content.Context
import android.media.AudioManager
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import kotlin.random.Random

class SpotifyConnection(private val context: Context) {

    private var spotifyAppRemote: SpotifyAppRemote? = null
    private val clientId = BuildConfig.SPOTIFY_CLIENT_ID
    private val redirectUri = "com.example.gestify://callback"


    private val trackState = MutableLiveData<String>("Not playing")  // LiveData for track state


    private val trackUris = listOf(
        "spotify:track:4xdBrk0nFZaP54vvZj0yx7",
        "spotify:track:2PmMh2t7jAtN6cqFooA0Xy",
        "spotify:track:2Y8BloifAHEn6GproQgPs7",
        "spotify:track:0RW1UL8w8rjQkaIaljaFc5",
        "spotify:track:6dBUzqjtbnIa1TwYbyw5CM"
    )

    // LiveData to observe music status in the UI
    val musicState: LiveData<String> = trackState


    public var isSpotifyConnected: Boolean = false

    fun initiateSpotifyLogin(activity: androidx.activity.ComponentActivity) {
        val builder = AuthorizationRequest.Builder(clientId, AuthorizationResponse.Type.TOKEN, redirectUri)
        builder.setScopes(arrayOf("streaming"))
        val request = builder.build()
        val authIntent = AuthorizationClient.createLoginActivityIntent(activity, request)
        activity.registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
            val response = AuthorizationClient.getResponse(result.resultCode, result.data)
            if (response.type == AuthorizationResponse.Type.TOKEN) {
                connectToSpotify(response.accessToken)
            } else {
                Toast.makeText(context, "Authorization Error: ${response.error}", Toast.LENGTH_SHORT).show()
            }
        }.launch(authIntent)

    }

    private fun connectToSpotify(accessToken: String) {
        val connectionParams = ConnectionParams.Builder(clientId)
            .setRedirectUri(redirectUri)
            .showAuthView(true)
            .build()

        SpotifyAppRemote.connect(context, connectionParams, object : Connector.ConnectionListener {
            override fun onConnected(spotifyAppRemote: SpotifyAppRemote) {
                this@SpotifyConnection.spotifyAppRemote = spotifyAppRemote
                Log.d("SpotifyConnection", "Connected to Spotify")
                isSpotifyConnected = true
                playRandomTrack()
                subscribeToPlayerState()
            }

            override fun onFailure(throwable: Throwable) {
                Toast.makeText(context, "Connection Failed: ${throwable.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun subscribeToPlayerState() {
        spotifyAppRemote?.playerApi?.subscribeToPlayerState()?.setEventCallback { playerState ->
            trackState.value = if (playerState.isPaused) "Paused: " else "Playing: "
            trackState.value += playerState.track.name

            //UPDATE musicStatus.text = trackState.value
        }?.setErrorCallback { throwable ->
            Log.e("Gestify", "Error subscribing to player state: ${throwable?.message ?: "Unknown error"}")
        }
    }

    fun playRandomTrack() {
        val randomTrackUri = trackUris[Random.nextInt(trackUris.size)]

        // Play the random track
        spotifyAppRemote?.playerApi?.play(randomTrackUri)?.setResultCallback {
            Log.d("SpotifyConnection", "Random track is playing!")
        }?.setErrorCallback {
            Log.e("SpotifyConnection", "Error playing track: ${it.message}")
        }
    }

    fun pauseTrack() {
        spotifyAppRemote?.playerApi?.pause()?.setResultCallback {
            Log.d("SpotifyConnection", "Track paused")
        }?.setErrorCallback {
            Log.e("SpotifyConnection", "Error pausing track: ${it.message}")
        }
    }

    fun volumeUp() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
    }

    fun volumeDown() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
    }


    fun mute() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
    }

     fun unmute() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val defaultVolume = maxVolume / 2 // Set it to 50% of max volume
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, defaultVolume, AudioManager.FLAG_SHOW_UI)
    }

    fun rewindTrack() {
        spotifyAppRemote?.playerApi?.seekToRelativePosition(-5000) // Rewind 5 seconds
    }

    fun resumeTrack() {
        spotifyAppRemote?.playerApi?.resume()?.setResultCallback {
            Log.d("Gestify", "Track resumed")
        }?.setErrorCallback {
            Log.e("Gestify", "Error resuming track: ${it.message}")
        }
    }

    fun skipTrack() {
        spotifyAppRemote?.playerApi?.skipNext()?.setResultCallback {
            Log.d("Gestify", "Skipped to next track")
        }?.setErrorCallback {
            Log.e("Gestify", "Error skipping track: ${it.message}")
        }
    }

    fun cleanup() {
        spotifyAppRemote?.let { SpotifyAppRemote.disconnect(it) }
    }
}