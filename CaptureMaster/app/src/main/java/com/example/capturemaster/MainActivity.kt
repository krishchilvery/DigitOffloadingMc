package com.example.capturemaster

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.capturemaster.databinding.ActivityMainBinding
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var bmp: Bitmap
    private lateinit var quadrants: Array<Uri?>


    private var aService: AdvertiserService? = null
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AdvertiserService.LocalBinder
            aService = binder.getService()
            checkPermissions()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            aService = null
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            aService?.startAdvertising()
        }

    private fun checkPermissions(){
        requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(connection)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        OpenCVLoader.initDebug()

        // Bind to LocalService
        Intent(this, AdvertiserService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        val captureLauncher =
            registerForActivityResult(ActivityResultContracts.TakePicturePreview()) {
                if (it != null) {
                    bmp = it
                }
                quadrants = processImg(bmp)
                viewBinding.imagePreview.setImageBitmap(bmp)
                viewBinding.processButton.visibility = View.VISIBLE
            }
        val retakeFlag = intent.getBooleanExtra(RETAKE_EXTRA, false)
        if(retakeFlag) {
            captureLauncher.launch(null)
        }
        viewBinding.captureButton.setOnClickListener {
            captureLauncher.launch(null)
        }
        viewBinding.processButton.setOnClickListener {
            startUploadActivity(quadrants)
        }
    }

    private fun startUploadActivity(quadrants: Array<Uri?>) {
        val uris = arrayOfNulls<String>(4)
        for(i in 0..3){
            uris[i] = quadrants[i].toString()
        }
        val uploadIntent = Intent(this, UploadActivity::class.java)
        uploadIntent.putExtra(URIS_EXTRA, uris)
        uploadIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION + Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        startActivity(uploadIntent)
    }

    private fun processImg(bmp: Bitmap): Array<Uri?> {
        val img = Mat()
        Utils.bitmapToMat(bmp, img)
        Imgproc.cvtColor(img, img, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(img, img, Size(5.0, 5.0), 0.0)
        Imgproc.threshold(img, img, 75.0, 255.0, Imgproc.THRESH_BINARY)
        Core.bitwise_not(img,img)
        Utils.matToBitmap(img, bmp)
        val rImg = Mat()
        Log.i(TAG, img.dims().toString())
        Imgproc.resize(img, rImg, Size(28.0,28.0))
        val pBmp = Bitmap.createBitmap(28, 28,  Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(rImg, pBmp)
        val quadrants = arrayOfNulls<Uri>(4)
        for (i in 0..3) {
            val qBmp = Bitmap.createBitmap(14, 14,  Bitmap.Config.ARGB_8888)
            val x = 0 + (i/2)*14
            val y = 0 + (i%2)*14
            val roi = Rect(x,y,14,14)
            val cImg = Mat(rImg, roi)
            Utils.matToBitmap(cImg, qBmp)
            val qUri = saveQuadrantToUri(qBmp, i)
            quadrants[i] = qUri
        }
        return quadrants
    }

    private fun saveQuadrantToUri(bmp: Bitmap, quadrantNumber: Number): Uri {
        val file = File(cacheDir, "quadrant-$quadrantNumber.png")
        bmp.compress(Bitmap.CompressFormat.PNG, 100, file.outputStream())
        return FileProvider.getUriForFile(
            this,
            "$packageName.provider", file
        )
    }

    companion object {
        const val TAG = "MainActivity"
        const val URIS_EXTRA = "com.example.capturemaster.extras.uris"
        const val RETAKE_EXTRA = "com.example.capturemaster.extras.retake"

        private val REQUIRED_PERMISSIONS: Array<String> = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                arrayOf (
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                )
            else ->
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
        }
    }
}