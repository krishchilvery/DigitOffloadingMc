package com.example.capturemaster

import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.capturemaster.databinding.ActivityUploadBinding
import kotlin.random.Random


class UploadActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityUploadBinding

    private lateinit var uris: Array<Uri?>

    private lateinit var imageUri: Uri

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

    override fun onStart() {
        super.onStart()
        bindAdvertiser()
        listenForEvents()
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        stopListening()
    }

    private fun listenForEvents() {
        LocalBroadcastManager.getInstance(applicationContext).registerReceiver(
            mMessageReceiver,
            IntentFilter(AdvertiserService.PREDICTION_DONE_EVENT)
        )
    }

    private fun stopListening() {
        LocalBroadcastManager.getInstance(applicationContext).unregisterReceiver(mMessageReceiver)
    }

    private val mMessageReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Get extra data included in the Intent
            val label = intent.getIntExtra(AdvertiserService.PREDICTION_EXTRA, 0)
            saveImageToGallery(label.toString())
            startMainActivity()
        }
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
    }

    private fun getRandomName(category: String): String {
        val randInt = Random.nextInt(10000)
        return "${category}_${randInt}"
    }

    fun saveImageToGallery(category: String) {
        val imageName = getRandomName(category)
        val cr = applicationContext.contentResolver
        val values = ContentValues()
        values.put(MediaStore.Images.Media.TITLE, imageName)
        if(Build.VERSION.SDK_INT  >= Build.VERSION_CODES.Q)
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$category")
        values.put(MediaStore.Images.Media.DISPLAY_NAME, imageName)
        values.put(MediaStore.Images.Media.DESCRIPTION, "Category: $category")
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        // Add the date meta data to ensure the image is added at the front of the gallery
        val millis = System.currentTimeMillis()
        values.put(MediaStore.Images.Media.DATE_ADDED, millis / 1000L)
        values.put(MediaStore.Images.Media.DATE_MODIFIED, millis / 1000L)
        values.put(MediaStore.Images.Media.DATE_TAKEN, millis)
        var url: Uri? = null
        try {
            url = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            val BUFFER_SIZE = 1024
            val fileStream = cr.openInputStream(imageUri)
            fileStream.use { fs ->
                val imageOut = cr.openOutputStream(url!!)
                imageOut.use { os ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val numBytesRead: Int? = fs?.read(buffer)
                        if (numBytesRead != null) {
                            if (numBytesRead <= 0) {
                                break
                            }
                        }
                        if (numBytesRead != null) {
                            os?.write(buffer, 0, numBytesRead)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (url != null) {
                cr.delete(url, null, null)
            }
        }
    }

    fun bindAdvertiser() {
        Intent(this, AdvertiserService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityUploadBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

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

        imageUri = Uri.parse(intent.getStringExtra(MainActivity.IMAGE_URI_EXTRA))
    }

    private fun parseUris(paths: Array<String>): Array<Uri?> {
        val uris = arrayOfNulls<Uri>(4)
        for (i in 0..3) {
            uris[i] = Uri.parse(paths[i])
        }
        return uris
    }

    private fun retake() {
        val mainIntent = Intent(this, MainActivity::class.java)
        mainIntent.putExtra(MainActivity.RETAKE_EXTRA, true)
        startActivity(mainIntent)
    }

    companion object {
        const val TAG = "UploadActivity"
    }
}