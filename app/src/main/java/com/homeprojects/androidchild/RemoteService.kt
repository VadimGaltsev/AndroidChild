package com.homeprojects.androidchild

import android.app.Activity.RESULT_OK
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.LocalServerSocket
import android.system.Os.accept
import android.util.Log
import java.io.InputStream
import kotlin.random.Random

const val SERVER_NAME = "unix_server"

class RemoteService : Service() {

    private val random = Random(0)

    private val binder = object : IMyAidlInterface.Stub() {

        override fun getMagicNumber() = "Hello binder " + random.nextBoolean()
    }

    override fun onBind(intent: Intent) = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startUnixLocalServer()
        val pendingIntent = intent?.getParcelableExtra<PendingIntent>("EXTRA")
        val data = Intent().putExtra("SERVER_NAME_EXTRA", SERVER_NAME)
        pendingIntent?.send(this, RESULT_OK, data)
        return Service.START_STICKY
    }

    private fun startUnixLocalServer() {
        Thread {
            val serverSocket = LocalServerSocket(SERVER_NAME)
                .accept()
            readInputStream(serverSocket.inputStream)
            serverSocket.close()
        }.start()
    }

    private fun readInputStream(`in`: InputStream) {
        `in`.use { `in` ->
            val buffer = ByteArray(1024)
            while (true) {
                val bytesToRead = `in`.read(buffer)
                if (bytesToRead == -1) break
                Log.d(TAG, String(buffer.copyOfRange(0, bytesToRead)))
            }
        }
    }
}
