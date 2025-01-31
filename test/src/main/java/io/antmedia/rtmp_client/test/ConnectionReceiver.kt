package io.antmedia.rtmp_client.test

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager

class ConnectionReceiver(networkChangeListener: OnNetworkChangeListener) : BroadcastReceiver() {

    var networkChangeListener :OnNetworkChangeListener ?= networkChangeListener

    override fun onReceive(context: Context?, intent: Intent?) {

        if (ConnectivityManager.CONNECTIVITY_ACTION == intent?.action) {
            val connectivityManager : ConnectivityManager = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            if (activeNetworkInfo == null || !activeNetworkInfo.isAvailable) {
                networkChangeListener?.onNetworkChange()
            }

        }
    }
}