package com.homeprojects.androidchild

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Button
import android.widget.TextView
import java.io.File
import java.io.InputStream
import java.lang.Exception
import java.lang.Process
import android.content.pm.PackageInstaller
import android.app.PendingIntent
import android.app.PendingIntent.getActivity
import android.app.Service
import android.net.ConnectivityManager
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Build
import android.os.storage.StorageManager
import android.telecom.Call
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.getSystemService
import java.lang.Thread.sleep


const val TAG = "me"
const val REQUEST_CODE = 0
const val EXTRA = "remote_name"

class MainActivity : AppCompatActivity() {

    private val serviceStatusLabel: TextView by lazy { findViewById<TextView>(R.id.service_status) }
    private val answerButton by lazy { findViewById<Button>(R.id.get_answer) }
    private val answerText by lazy { findViewById<TextView>(R.id.magic_answer) }
    private var iRemoteService: IMyAidlInterface? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            serviceStatusLabel.text = "Connected"
            iRemoteService = IMyAidlInterface.Stub.asInterface(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceStatusLabel.text = "Disconnected"
            iRemoteService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
//        val pendingIntent = createPendingResult(REQUEST_CODE, Intent(), PendingIntent.FLAG_ONE_SHOT)
        Intent(this, RemoteService::class.java)
//            .putExtra("EXTRA", pendingIntent)
            .apply {
                bindService(this, serviceConnection, Service.BIND_AUTO_CREATE)
            }
        answerButton.setOnClickListener {
            answerText.text = iRemoteService?.magicNumber
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE -> connectToRemoteProcess(data?.getStringExtra("SERVER_NAME_EXTRA") ?: "")
        }
    }

    private fun connectToRemoteProcess(remoteName: String) {
        val unixDomainSocket = LocalSocket(LocalSocket.SOCKET_STREAM)
        unixDomainSocket.connect(LocalSocketAddress(remoteName))
        unixDomainSocket.outputStream.write("Hello from client".toByteArray())
        unixDomainSocket.close()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun printVolumes() {
        val storageManager = getSystemService(Context.STORAGE_SERVICE) as StorageManager
        storageManager.storageVolumes.forEach {
            println(it.uuid)
        }
    }

    private fun uninstallApp() {
        val appPackage = "com.your.app.package"
        val intent = Intent(this, this::class.java)
        val sender = PendingIntent.getActivity(this, 0, intent, 0)
        val mPackageInstaller = packageManager.packageInstaller
        mPackageInstaller.uninstall(appPackage, sender.intentSender)
    }

    @SuppressLint("SdCardPath")
    private fun initChildProcess() {
        Log.d(TAG, "My pid ${android.os.Process.myPid()}")
        val homeDir = "/data/data/com.homeprojects.androidchild/files"
        Runtime.getRuntime().exec("chmod u+x -R $homeDir")
            .waitFor()
        val child = ProcessBuilder("$homeDir/process").apply {
            environment()["MY_PARENT"] = android.os.Process.myPid().toString()
        }
            .directory(File(homeDir))
            .start()
        var pid = -1L
        if (child::class.java.name == "java.lang.UNIXProcess") {
            val f = child::class.java.getDeclaredField("pid")
            f.isAccessible = true
            pid = f.getLong(child)
        }
        Log.d(TAG, "Child process: $pid")
        readChildOut(child.inputStream)
        child.waitFor()
    }

    private fun readChildOut(`in`: InputStream) {
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
