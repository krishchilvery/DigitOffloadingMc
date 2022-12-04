package com.example.capturemaster

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.net.toFile
import com.example.capturemaster.databinding.ActivityUploadBinding
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*

class UploadActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityUploadBinding

    private lateinit var client: HttpClient
    private lateinit var uris: Array<Uri?>


    private var aService: AdvertiserService? = null
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AdvertiserService.LocalBinder
            aService = binder.getService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            aService = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(connection)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityUploadBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        Intent(this, AdvertiserService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        val paths = intent.getStringArrayExtra(MainActivity.URIS_EXTRA)
        if (paths != null) {
            uris = parseUris(paths)
            Log.i(TAG, paths[0])
            Log.i(TAG, paths[1])
            Log.i(TAG, paths[2])
            Log.i(TAG, paths[3])

            viewBinding.quadrantView0.setImageURI(uris[0])
            viewBinding.quadrantView1.setImageURI(uris[1])
            viewBinding.quadrantView2.setImageURI(uris[2])
            viewBinding.quadrantView3.setImageURI(uris[3])
        }
        viewBinding.uploadButton.setOnClickListener {
            aService?.sendFiles(uris)
        }
        viewBinding.retakeButton.setOnClickListener {
            retake()
        }
    }

    private fun parseUris(paths: Array<String>): Array<Uri?> {
        val uris = arrayOfNulls<Uri>(4)
        for(i in 0..3){
            uris[i] = Uri.parse(paths[i])
        }
        return uris
    }

    private fun retake() {
        val mainIntent = Intent(this, MainActivity::class.java)
        mainIntent.putExtra(MainActivity.RETAKE_EXTRA, true)
        startActivity(mainIntent)
    }

    private suspend fun upload(): HttpResponse? {
        val url = "http://www.google.com"
        val file = uris[0]?.toFile()
        var response: HttpResponse? = null
        if(file != null) {
             response = client.post(url) {
                setBody(MultiPartFormDataContent(
                    formData {
                        append("image", file.readBytes(), Headers.build {
                            append(HttpHeaders.ContentType, "image/png")
                            append(HttpHeaders.ContentDisposition, "filename=\"quadrant.png\"" )
                        })
                    },
                    boundary = "WebAppBoundary"
                )
                )
                onUpload {
                    bytesSentTotal, contentLength ->
                    Log.i(TAG, "Sent $bytesSentTotal bytes from $contentLength")
                }
            }
        }
        return response
    }

    companion object {
        const val TAG = "UploadActivity"
    }
}