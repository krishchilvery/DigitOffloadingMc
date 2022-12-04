package com.example.captureslave

import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo

data class DiscoveryDataModel (val endpointId: String, val info: DiscoveredEndpointInfo? = null) {
}