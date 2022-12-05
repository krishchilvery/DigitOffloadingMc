package com.example.capturemaster

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.gson.Gson

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

class AdvertiserService : Service() {

    private val binder = LocalBinder()
    private var clients = ArrayList<String>()

    private var predictions = ArrayList<Prediction>()

    val clientsCount: Int
        get() =  clients.size

    inner class LocalBinder: Binder() {
        fun getService(): AdvertiserService = this@AdvertiserService
    }

    private val payloadCallback: PayloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val receivedBytes = payload.asBytes()
                val json = receivedBytes?.decodeToString()
                val gson = Gson()
                val pred = gson.fromJson(json, Prediction::class.java)
                predictions.add(pred)
                if(predictions.size == 4) {
                    processPredictions()
                }
            }
        }

        fun processPredictions() {
            var sums = FloatArray(10)
            var confs = FloatArray(10)
            predictions.forEach {
                sums = sums.zip(it.predictions) {x, y -> x + y}.toFloatArray()
                confs[it.label] += it.confidence
            }
            val max = sums.max()
            val label = sums.indexOfFirst { it == max }
            showToast("Predicted Digit: $label")
            broadcastPrediction(label)
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
                        clients.add(endpointId)
                        showToast("Connected to $endpointId")
                    }
                    ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {

                    }
                    ConnectionsStatusCodes.STATUS_ERROR -> {

                    }
                    else -> {}
                }
            }

            override fun onDisconnected(endpointId: String) {
                clients.remove(endpointId)
                showToast("$endpointId Disconnected")
            }
        }

    fun startAdvertising() {
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
        Nearby.getConnectionsClient(applicationContext)
            .startAdvertising(resources.getString(R.string.app_name), "CAPTURE_CLOUD",
                connectionLifecycleCallback, advertisingOptions)
            .addOnSuccessListener {
                Log.i(MainActivity.TAG, "Advertise")
            }.addOnFailureListener {
                Log.e(MainActivity.TAG, it.toString())
            }
    }

    private fun publishFiles(uris: Array<Uri?>) {
        for (i in 0..3){
            val file = uris[i]?.let { contentResolver.openFileDescriptor(it, "r") }
            if(file != null) {
                val filePayload = Payload.fromFile(file)
                Nearby.getConnectionsClient(applicationContext)
                    .sendPayload(clients[i % REQUIRED_CLIENT_COUNT], filePayload)
            } else {
                Log.e("AdvertiserService", "Failed to send file")
            }
        }
    }

    fun sendFiles(uris: Array<Uri?>){
        predictions = ArrayList<Prediction>()
        when {
            clientsCount < REQUIRED_CLIENT_COUNT -> {
                showToast("Connected Clients: $clientsCount. " +
                        "Please connect ${REQUIRED_CLIENT_COUNT-clientsCount} more")
                return
            }
            clientsCount > REQUIRED_CLIENT_COUNT -> {
                showToast("Connected Clients: $clientsCount. " +
                        "Only the first ${REQUIRED_CLIENT_COUNT} clients will be used")
                publishFiles(uris)
            }
            else -> {
                publishFiles(uris)
            }
         }
    }

    fun broadcastPrediction(digit: Int) {
        val intent = Intent(PREDICTION_DONE_EVENT)
        intent.putExtra(PREDICTION_EXTRA, digit)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val PREDICTION_DONE_EVENT = "com.example.caturemaster.predicion.DONE"
        const val PREDICTION_EXTRA = "com.example.caturemaster.predicion.digit"
        const val REQUIRED_CLIENT_COUNT = 4
    }
}