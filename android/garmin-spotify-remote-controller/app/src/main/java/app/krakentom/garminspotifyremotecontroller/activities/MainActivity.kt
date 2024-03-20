package app.krakentom.garminspotifyremotecontroller.activities

import android.content.BroadcastReceiver
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import app.krakentom.garminspotifyremotecontroller.R
import app.krakentom.garminspotifyremotecontroller.services.MyService
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import kotlinx.coroutines.launch
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


class MainActivity : AppCompatActivity() {

    private val SPOTIFY_CLIENT_ID = "TODO YOUR SPOTIFY ID"
    private val SPOTIFY_REDIRECT_URI = "http://localhost/"

    private lateinit var updateReceiver: DataUpdateReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        spotifyConnect()

        updateReceiver = DataUpdateReceiver(findViewById(R.id.textView))
        val intentFilter = IntentFilter(MyService.INTENT_ACTION)
        registerReceiver(updateReceiver, intentFilter)
        MyService.startService(this)
    }

    public override fun onResume() {
        super.onResume()
    }

    public override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(updateReceiver)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    private class DataUpdateReceiver(val textView: TextView) : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == MyService.INTENT_ACTION) {
                textView.text = intent.extras?.get("value").toString()
            }
        }
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.start_service -> {
                MyService.startService(this)
                true
            }

            R.id.stop_service -> {
                MyService.stopService(this)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun spotifyConnect() {
        SpotifyAppRemote.disconnect(MyService.spotifyAppRemote)
        lifecycleScope.launch {
            try {
                MyService.spotifyAppRemote = spotifyConnectToAppRemote(true)
                MyService.startService(this@MainActivity)
            } catch (error: Throwable) {
                Log.e(TAG, "", error)
            }
        }
    }

    private suspend fun spotifyConnectToAppRemote(showAuthView: Boolean): SpotifyAppRemote? =
        suspendCoroutine { cont: Continuation<SpotifyAppRemote> ->
            SpotifyAppRemote.connect(
                application,
                ConnectionParams.Builder(SPOTIFY_CLIENT_ID)
                    .setRedirectUri(SPOTIFY_REDIRECT_URI)
                    .showAuthView(showAuthView)
                    .build(),
                object : Connector.ConnectionListener {
                    override fun onConnected(spotifyAppRemote: SpotifyAppRemote) {
                        cont.resume(spotifyAppRemote)
                    }

                    override fun onFailure(error: Throwable) {
                        cont.resumeWithException(error)
                    }
                })
        }
}