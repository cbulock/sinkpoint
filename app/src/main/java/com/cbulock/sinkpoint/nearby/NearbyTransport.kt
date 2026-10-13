package com.cbulock.sinkpoint.nearby

import android.Manifest
import android.content.Context
import android.os.Build
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class Peer(val endpoint: String, val name: String)
sealed interface TransportEvent {
    data class Found(val peer: Peer) : TransportEvent
    data class Lost(val endpoint: String) : TransportEvent
    data class Pairing(val peer: Peer, val code: String) : TransportEvent
    data class Connected(val endpoint: String) : TransportEvent
    data object Disconnected : TransportEvent
    data class Message(val value: WireMessage) : TransportEvent
    data class Failure(val cause: String) : TransportEvent
}
interface NearbyTransport {
    val events: Flow<TransportEvent>
    fun available(): Boolean
    suspend fun host(name: String)
    suspend fun discover()
    suspend fun connect(peer: Peer, name: String)
    suspend fun accept(endpoint: String)
    fun reject(endpoint: String)
    suspend fun send(message: WireMessage)
    fun close()
}
fun nearbyPermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= 31) addAll(listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE))
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
    else {
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}.toTypedArray()

class GoogleNearbyTransport(private val context: Context) : NearbyTransport {
    private val client = Nearby.getConnectionsClient(context)
    private val channel = Channel<TransportEvent>(Channel.UNLIMITED)
    override val events = channel.receiveAsFlow()
    private var endpoint: String? = null
    private var candidate: String? = null
    private var active = false
    private var decoder = WireCodec.Decoder()
    private fun emit(event: TransportEvent) { channel.trySend(event) }
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(id: String, payload: Payload) {
            if (id != endpoint) return
            try {
                require(payload.type == Payload.Type.BYTES)
                decoder.accept(requireNotNull(payload.asBytes()))?.let { emit(TransportEvent.Message(it)) }
            } catch (e: Exception) { emit(TransportEvent.Failure(e.message ?: "Invalid payload")); close() }
        }
        override fun onPayloadTransferUpdate(id: String, update: PayloadTransferUpdate) {
            if (id == endpoint && update.status == PayloadTransferUpdate.Status.FAILURE) {
                emit(TransportEvent.Failure("Transfer interrupted")); close()
            }
        }
    }
    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
            if (!active || candidate != null && candidate != id || endpoint != null) { client.rejectConnection(id); return }
            candidate = id
            emit(TransportEvent.Pairing(Peer(id, info.endpointName.take(32)), info.authenticationDigits))
        }
        override fun onConnectionResult(id: String, result: ConnectionResolution) {
            if (!active || candidate != id) { client.disconnectFromEndpoint(id); return }
            candidate = null
            if (result.status.isSuccess) {
                endpoint = id; decoder = WireCodec.Decoder()
                client.stopAdvertising(); client.stopDiscovery()
                emit(TransportEvent.Connected(id))
            } else emit(TransportEvent.Failure(result.status.statusMessage ?: "Connection rejected"))
        }
        override fun onDisconnected(id: String) {
            if (endpoint == id) { endpoint = null; emit(TransportEvent.Disconnected) }
        }
    }
    override fun available() = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    override suspend fun host(name: String) {
        active = true
        client.startAdvertising(name.take(32), SERVICE, lifecycle, AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()).await()
    }
    override suspend fun discover() {
        active = true
        client.startDiscovery(SERVICE, object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) { if (active) emit(TransportEvent.Found(Peer(id, info.endpointName.take(32)))) }
            override fun onEndpointLost(id: String) { if (active) emit(TransportEvent.Lost(id)) }
        }, DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()).await()
    }
    override suspend fun connect(peer: Peer, name: String) { client.requestConnection(name.take(32), peer.endpoint, lifecycle).await() }
    override suspend fun accept(endpoint: String) { client.acceptConnection(endpoint, payloadCallback).await() }
    override fun reject(endpoint: String) { client.rejectConnection(endpoint); candidate = null }
    override suspend fun send(message: WireMessage) {
        val id = requireNotNull(endpoint) { "Not connected" }
        for (bytes in WireCodec.encode(message)) client.sendPayload(id, Payload.fromBytes(bytes)).await()
    }
    override fun close() {
        active = false
        endpoint = null; candidate = null; decoder = WireCodec.Decoder()
        client.stopAdvertising(); client.stopDiscovery(); client.stopAllEndpoints()
    }
    companion object { const val SERVICE = "com.cbulock.sinkpoint.connections.v1" }
}
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
