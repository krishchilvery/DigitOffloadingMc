package com.example.captureslave

import android.Manifest
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.captureslave.databinding.ActivityMainBinding
import com.example.captureslave.ml.PatchModel
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import org.tensorflow.lite.DataType
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.TransformToGrayscaleOp
import org.tensorflow.lite.support.model.Model
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var viewBinding: ActivityMainBinding

    private var uri: Uri? = null

    private lateinit var recyclerView: RecyclerView
    private var data = ArrayList<DiscoveryDataModel>()
    private var connectedEndpoint :DiscoveryDataModel? = null
    private var adapter = DiscoveryAdapter(data) { endpointId: String -> connectToMaster(endpointId) }

    private fun initTensorflow() {
        val compatList = CompatibilityList()
        val options = if(compatList.isDelegateSupportedOnThisDevice) {
            Model.Options.Builder().setDevice(Model.Device.GPU).build()
        }else{
            Model.Options.Builder().setNumThreads(4).build()
        }
    }

    private fun appendData (endpointId:String, endpointInfo: DiscoveredEndpointInfo) {
        data.add(DiscoveryDataModel(endpointId, endpointInfo))
        adapter.notifyItemInserted(data.size - 1)
    }

    private fun removeData (endpointId: String) {
        val index = data.indexOfFirst { it.endpointId == endpointId }
        data.removeAt(index)
        adapter.notifyItemRemoved(index)
    }

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            Log.i(TAG, it.toString())

        }

    private fun checkPermissions(){
        requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        checkPermissions()
        initTensorflow()
        recyclerView = viewBinding.advertiserList
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        viewBinding.discoverButton.setOnClickListener {
            viewBinding.imageView.visibility = View.GONE
            viewBinding.predView.visibility = View.GONE
            viewBinding.advertiserList.visibility = View.VISIBLE
            connectedEndpoint?.endpointId?.let { it ->
                Nearby.getConnectionsClient(applicationContext).disconnectFromEndpoint(
                    it
                )
            }
            startDiscovery()
        }
    }

    private fun predict(){
        val parcelFileDesc = uri?.let { contentResolver.openFileDescriptor(it, "r")}
        val fileDesc = parcelFileDesc?.fileDescriptor
        val img = BitmapFactory.decodeFileDescriptor(fileDesc)
        parcelFileDesc?.close()

        val model = PatchModel.newInstance(this)

        val imageProcessor = ImageProcessor.Builder().add(TransformToGrayscaleOp()).build()

        var tfImg = TensorImage(DataType.UINT8)
        tfImg.load(img)
        tfImg = imageProcessor.process(tfImg)

        val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(14, 14, 1), DataType.UINT8)

        Log.d("shape", tfImg.buffer.toString())
        Log.d("shape", inputFeature0.buffer.toString())

        inputFeature0.loadBuffer(tfImg.buffer)

        val outputs = model.process(inputFeature0)
        val predictions =  outputs.outputFeature0AsTensorBuffer.floatArray
        val confidence = outputs.outputFeature1AsTensorBuffer.floatArray[0]
        val label = outputs.outputFeature2AsTensorBuffer.intArray[0]

        val pred = Prediction(
            predictions,
            confidence,
            label
        )
        viewBinding.predView.visibility=View.VISIBLE
        viewBinding.predView.text = "Predicted label ${pred.label} with confidence ${pred.confidence}"

        val gson = Gson()
        val json = gson.toJson(pred)
        sendPredictions(json)
    }

    private fun sendPredictions (s: String) {
        val payload = Payload.fromBytes(s.toByteArray())
        connectedEndpoint?.let { Nearby.getConnectionsClient(applicationContext).sendPayload(it.endpointId, payload) }
    }

    data class Prediction(val predictions: FloatArray, val confidence: Float, val label: Int) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Prediction

            if (!predictions.contentEquals(other.predictions)) return false
            if (confidence != other.confidence) return false
            if (label != other.label) return false

            return true
        }

        override fun hashCode(): Int {
            var result = predictions.contentHashCode()
            result = 31 * result + confidence.hashCode()
            result = 31 * result + label
            return result
        }
    }

    private fun getLocalUserName(): String {
        val r = Random.nextInt(1000)
        return "ANDROID-15-slave-$r"
    }

    private val payloadCallback: PayloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            // This always gets the full data of the payload. Is null if it's not a BYTES payload.
            if (payload.type == Payload.Type.FILE) {
                uri = payload.asFile()?.asUri()
                viewBinding.imageView.setImageURI(uri)
                predict()
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Bytes payloads are sent as a single chunk, so you'll receive a SUCCESS update immediately
            // after the call to onPayloadReceived().
        }
    }

    private val connectionLifecycleCallback: ConnectionLifecycleCallback =
        object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
                // Automatically accept the connection on both sides.
                Nearby.getConnectionsClient(applicationContext).acceptConnection(endpointId, payloadCallback)
            }

            override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
                when (result.status.statusCode) {
                    ConnectionsStatusCodes.STATUS_OK -> {
                        val snackbar = Snackbar.make(viewBinding.root, "Connected to $endpointId", Snackbar.LENGTH_LONG)
                        snackbar.show()
                        connectedEndpoint = DiscoveryDataModel(endpointId)
                    }
                    ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                        val snackbar = Snackbar.make(viewBinding.root, "Rejected to $endpointId", Snackbar.LENGTH_LONG)
                        snackbar.show()
                    }
                    ConnectionsStatusCodes.STATUS_ERROR -> {
                        val snackbar = Snackbar.make(viewBinding.root, "Failed to $endpointId", Snackbar.LENGTH_LONG)
                        snackbar.show()
                    }
                    else -> {}
                }
            }

            override fun onDisconnected(endpointId: String) {
                // We've been disconnected from this endpoint. No more data can be
                // sent or received.
            }
        }


    private val endpointDiscoveryCallback: EndpointDiscoveryCallback =
        object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                // An endpoint was found. We request a connection to it.
//                viewBinding.textView.text = endpointId
                appendData(endpointId, info)
            }

            override fun onEndpointLost(endpointId: String) {
                // A previously discovered endpoint has gone away.
                removeData(endpointId)
                connectedEndpoint = null
            }
        }

    private fun connectToMaster(endpointId: String) {
        Nearby.getConnectionsClient(applicationContext)
            .requestConnection(getLocalUserName(), endpointId, connectionLifecycleCallback)
            .addOnSuccessListener {
                viewBinding.imageView.visibility = View.VISIBLE
                viewBinding.advertiserList.visibility = View.GONE
            }
            .addOnFailureListener{
                Log.e(TAG, it.toString())
            }
    }
    private fun startDiscovery() {
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
        Nearby.getConnectionsClient(applicationContext)
            .startDiscovery("CAPTURE_CLOUD", endpointDiscoveryCallback, discoveryOptions)
            .addOnSuccessListener {
                Log.i(TAG, "Discover")
            }
            .addOnFailureListener {
                    e: java.lang.Exception? ->
                Log.e(TAG, e.toString())
            }
    }

    companion object {
        const val TAG = "MainActivity"

        private val REQUIRED_PERMISSIONS: Array<String> = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
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