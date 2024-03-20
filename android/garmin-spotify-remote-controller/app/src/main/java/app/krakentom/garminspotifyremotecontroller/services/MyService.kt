package app.krakentom.garminspotifyremotecontroller.services

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import app.krakentom.garminspotifyremotecontroller.R
import app.krakentom.garminspotifyremotecontroller.activities.MainActivity
import app.krakentom.garminspotifyremotecontroller.models.PlayerInfo
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.PlayerState


class MyService : Service() {

    private val garminConnectedDevices = mutableListOf<IQDevice>()


    private lateinit var garminConnectIQ: ConnectIQ
    private var garminApp = IQApp(GARMIN_WATCH_ID)

    private val spotifyErrorCallback = { throwable: Throwable -> spotifyLogError(throwable) }
    private var spotifyPlayerStateSubscription: Subscription<PlayerState>? = null

    private val playerInfo: PlayerInfo = PlayerInfo()

    companion object {
        var spotifyAppRemote: SpotifyAppRemote? = null
        const val INTENT_ACTION = "UPDATE_UI_INTENT"

        fun startService(context: Context) {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            for (service in manager.getRunningServices(Int.MAX_VALUE)) {
                if (MyService::class.java.name.equals(service.service.className)) {
                    Toast.makeText(context, "Garmin Spotify Service is already running", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            val startIntent = Intent(context, MyService::class.java)
            ContextCompat.startForegroundService(context, startIntent)
        }

        fun stopService(context: Context) {
            val stopIntent = Intent(context, MyService::class.java)
            context.stopService(stopIntent)
        }

        private const val CHANNEL_ID = "Garmin Spotify Remote Controller"
        private const val GARMIN_WATCH_ID = "TODO YOUR WATCH ID"

    }

    private fun setText(text: String) {
        val intent = Intent(INTENT_ACTION)
        intent.putExtra("value", text)
        sendBroadcast(intent)
    }

    override fun onCreate() {
        super.onCreate()
        spotifyPlayerStateSubscription = spotifyAssertAppRemoteConnected()
            ?.playerApi
            ?.subscribeToPlayerState()
            ?.setEventCallback(spotifyPlayerStateEventCallback)

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Garmin Spotify Remote Controller")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()
        startForeground(1, notification)

        try {
            garminConnectIQ = ConnectIQ.getInstance(this, ConnectIQ.IQConnectType.WIRELESS)
            garminConnectIQ.initialize(this, true, garminConnectIQListener)
            setText("Garmin started")
        } catch (e: Exception) {
            setText("startCommand: $e")
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        setText("Garmin stopped")
        garminReleaseConnectIQSdk()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "GarminSpotifyRemoteController Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private val garminConnectIQListener: ConnectIQ.ConnectIQListener =
        object : ConnectIQ.ConnectIQListener {
            override fun onInitializeError(errStatus: ConnectIQ.IQSdkErrorStatus) {
                setText( "Garmin SDK initialization error!")
            }

            override fun onSdkReady() {
                setText("Garmin SDK ready")
                garminLoadDevices()
            }

            override fun onSdkShutDown() {
                setText("Garmin SDK shut down")
            }
        }

    fun garminLoadDevices() {
        try {
            val devices = garminConnectIQ.knownDevices ?: listOf()
            setText("known devices: " + devices.joinToString(", ") { it.friendlyName })

            garminConnectedDevices.clear()
            devices.forEach {
                it.status = garminConnectIQ.getDeviceStatus(it)

                garminConnectIQ.registerForDeviceEvents(it) { device, status ->
                    if (status == IQDevice.IQDeviceStatus.CONNECTED) {
                        garminListenByMyAppEvents(device)
                        setText("${device.friendlyName} connected, listing for events")
                        sendPlayerInfoToGarmin()
                    }
                }
                garminGetAppStatus(it)
            }
        } catch (e: Exception) {
            setText("garmin load devices: $e")
        }
    }

    private fun garminListenByMyAppEvents(device: IQDevice) {
        try {
            garminConnectIQ.registerForAppEvents(device, garminApp) { _, _, message, _ ->
                try {
                    val builder = StringBuilder()
                    if (message.size > 0) {
                        for (o in message) {
                            builder.append(o.toString())
                            builder.append("\r\n")
                        }
                    }

                    val command = builder.toString().removeSuffix("\r\n")
                    setText("received command from garmin '$command'")
                    when (command) {
                        "playPause" -> {
                            spotifyPause()
                        }
                        "nextSong" -> {
                            spotifyNext()
                        }
                        "likeUnlikeSong" -> {
                            spotifyLikeUnlikeSong()
                        }
                    }
                    sendPlayerInfoToGarmin()
                } catch (e: Exception) {
                    setText("garmin message: $e")
                }

            }
        } catch (e: Exception) {
            setText("register app events: $e")
        }
    }

    private fun garminReleaseConnectIQSdk() {
        try {
            garminConnectIQ.unregisterAllForEvents()
            garminConnectIQ.shutdown(this)
        } catch (e: Exception) {
            setText("garmin release: $e")
        }
    }

    private fun spotifyAssertAppRemoteConnected(): SpotifyAppRemote? {
        spotifyAppRemote?.let {
            if (it.isConnected) {
                return it
            }
        }
        return null
    }

    private fun spotifyPause() {
        spotifyAssertAppRemoteConnected()?.let {
            it.playerApi
                .playerState
                .setResultCallback { playerState ->
                    if (playerState.isPaused) {
                        it.playerApi
                            .resume()
                            .setErrorCallback(spotifyErrorCallback)
                    } else {
                        it.playerApi
                            .pause()
                            .setErrorCallback(spotifyErrorCallback)
                    }
                }
        }
    }

    private fun spotifyNext() {
        spotifyAssertAppRemoteConnected()
            ?.playerApi
            ?.skipNext()
            ?.setErrorCallback(spotifyErrorCallback)
    }

    private fun spotifyLikeUnlikeSong() {
        spotifyAssertAppRemoteConnected()?.let {
            it.playerApi
                .playerState
                .setResultCallback { playerState ->
                    it.userApi.getLibraryState(playerState.track.uri)
                        .setResultCallback { libraryState ->
                            if (libraryState.isAdded) {
                                it.userApi
                                    .removeFromLibrary(playerState.track.uri)
                                    .setErrorCallback(spotifyErrorCallback)
                            } else {
                                it.userApi
                                    .addToLibrary(playerState.track.uri)
                                    .setErrorCallback(spotifyErrorCallback)
                            }
                        }
                }
        }
    }

    private val spotifyPlayerStateEventCallback =
        Subscription.EventCallback<PlayerState> { playerState ->
            spotifyAssertAppRemoteConnected()?.let {
                it.userApi.getLibraryState(playerState.track.uri)
                    .setResultCallback { libraryState ->

                        playerInfo.song = playerState.track.name
                        playerInfo.artist = playerState.track.artist.name
                        playerInfo.duration = playerState.track.duration
                        playerInfo.isInLibrary = libraryState.isAdded

                        sendPlayerInfoToGarmin()
                    }
            }
        }

    private fun sendPlayerInfoToGarmin() {
//        setText(playerInfo.toMap().toString())
        garminConnectedDevices.forEach {
            try {
                garminConnectIQ.sendMessage(
                    it,
                    garminApp,
                    playerInfo.toMap()
                ) { _, _, _ -> }
            } catch (e: Exception) {
                setText("send player info: $e")
            }
        }
    }

    private fun <T : Any?> spotifyCancelAndResetSubscription(subscription: Subscription<T>?): Subscription<T>? {
        return subscription?.let {
            if (!it.isCanceled) {
                it.cancel()
            }
            null
        }
    }

    private fun garminGetAppStatus(device: IQDevice) {
        try {
            garminConnectIQ.getApplicationInfo(GARMIN_WATCH_ID, device, object :
                ConnectIQ.IQApplicationInfoListener {
                override fun onApplicationInfoReceived(app: IQApp) {
                    garminConnectedDevices.add(device)
                }

                override fun onApplicationNotInstalled(applicationId: String) {
                }
            })
        } catch (e: Exception) {
            setText("garmin app status: $e")
        }
    }

    private fun spotifyLogError(e: Throwable) {
        setText("spotify error: $e")
    }
}