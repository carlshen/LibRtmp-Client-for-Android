package io.antmedia.rtmp_client.test

import android.annotation.SuppressLint
import android.content.IntentFilter
import android.content.res.Configuration
import android.media.AudioFormat
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.text.TextUtils
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.CompoundButton
import android.widget.Toast
import android.widget.ToggleButton


/**
 * Realtime living with rtmp stream
 * Created by carl shen on 2022/7/28.
 */

open class LiveActivity : BaseActivity(), CompoundButton.OnCheckedChangeListener,
    OnNetworkChangeListener {
    private var liveView: View? = null
    private var livePusher: LivePusherNew? = null
//    private var livePusher: LivePusherRtmp? = null
    private var isPushing = false
    private var connectionReceiver: ConnectionReceiver? = null
    private var orientationHandler: OrientationHandler? = null

    @SuppressLint("HandlerLeak")
    private val mHandler = object : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            if (msg.what == MSG_ERROR) {
                val errMsg = msg.obj as String
                if (!TextUtils.isEmpty(errMsg)) {
                    Toast.makeText(this@LiveActivity, errMsg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override val layoutId: Int
        get() = R.layout.activity_live

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hideActionBar()
        initView()
        initPusher()
        registerBroadcast(this)
        orientationHandler = OrientationHandler(this)
        orientationHandler?.enable()
        orientationHandler?.setOnOrientationListener(object :OrientationHandler.OnOrientationListener {
            override fun onOrientation(orientation: Int) {
                val previewDegree = (orientation + 90) % 360
                livePusher?.setPreviewDegree(previewDegree)
            }
        })
    }

    private fun initView() {
        initViewsWithClick(R.id.btn_swap)
        (findViewById<View>(R.id.btn_live) as ToggleButton).setOnCheckedChangeListener(this)
        (findViewById<View>(R.id.btn_mute) as ToggleButton).setOnCheckedChangeListener(this)
        liveView = getView(R.id.surface_camera)
    }

    private fun initPusher() {
        val width = 640
        val height = 480
        val videoBitRate = 800000 // kb/s
        val videoFrameRate = 10 // fps
        val videoParam = VideoParam(width, height,
                Integer.valueOf(Camera2Helper.CAMERA_ID_BACK), videoBitRate, videoFrameRate)
        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_STEREO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val numChannels = 2
        val audioParam = AudioParam(sampleRate, channelConfig, audioFormat, numChannels)
        // Camera1: SurfaceView  Camera2: TextureView
        livePusher = LivePusherNew(this, videoParam, audioParam, liveView, CameraType.CAMERA2)
//        livePusher = LivePusherRtmp(this, videoParam, audioParam, liveView, CameraType.CAMERA2)
        livePusher?.setVideoCodecInfo(width, height, videoFrameRate, videoBitRate);
        if (liveView is SurfaceView) {
            val holder: SurfaceHolder = (liveView as SurfaceView).holder
            livePusher!!.setPreviewDisplay(holder)
        }
    }

    private fun registerBroadcast(networkChangeListener: OnNetworkChangeListener) {
        connectionReceiver = ConnectionReceiver(networkChangeListener)
        val intentFilter = IntentFilter()
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(connectionReceiver, intentFilter)
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        when (buttonView.id) {
            R.id.btn_live//start or stop living
            -> if (isChecked) {
                livePusher!!.startPush(LIVE_URL)
                isPushing = true
            } else {
                livePusher!!.stopPush()
                isPushing = false
            }
            R.id.btn_mute//mute or not
            -> {
                Log.i(TAG, "isChecked=$isChecked")
                livePusher!!.setMute(isChecked)
            }
            else -> {
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        orientationHandler?.disable()
        if (livePusher != null) {
            if (isPushing) {
                isPushing = false
                livePusher?.stopPush()
            }
            livePusher!!.release()
        }
        if (connectionReceiver != null) {
            unregisterReceiver(connectionReceiver)
        }
    }

    override fun onViewClick(view: View) {
        if (view.id == R.id.btn_swap) {//switch camera
            livePusher!!.switchCamera()
        }
    }

    override fun onSelectedFile(filePath: String) {

    }

    override fun onNetworkChange() {
        Toast.makeText(this, "network is not available", Toast.LENGTH_SHORT).show()
        if (livePusher != null && isPushing) {
            livePusher?.stopPush()
            isPushing = false
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.i(TAG, "onConfigurationChanged, orientation=" + newConfig.orientation)
    }

    companion object {

        private val TAG = LiveActivity::class.java.simpleName
//        private const val LIVE_URL = "rtmp://192.168.31.29/live/stream"
        private const val LIVE_URL = "rtmp://live-push.bilivideo.com/live-bvc/" + "?streamname=live_574012814_82567615&key=8a91f1087409d56f62e972b698d2a9d0&schedule=rtmp&pflag=1"
        private const val MSG_ERROR = 100
    }
}
