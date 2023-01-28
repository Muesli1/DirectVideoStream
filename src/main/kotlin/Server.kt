package muesli1.directvideostream

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.PortUnreachableException
import java.util.*
import kotlin.system.measureTimeMillis


fun log(message: String, request: DatagramPacket?) {
    println(buildString {
        append(message)
        if(request != null) {
            append(" with request")
            append(" len = ${request.length}")
            append(", ")
            append("from = ${request.socketAddress}")
        }
    })
}


open class UgaBuga {
    companion object {
        fun nom() {

        }
    }
}

open class ChunkedVideo {
    companion object {

    }
}

class Client<T: UgaBuga>(val processor: suspend (T) -> ByteArray,
                         val sendLength: Int,
                         private val setupFrequency: Int = 60,
                         private val socket: DatagramSocket = DatagramSocket(),
                private val address: InetAddress = InetAddress.getByName("192.168.188.52")) {


    init {
        socket.connect(address, Constants.PORT)
    }
    private var lastSetup = setupFrequency
    private val lastSetupMutex = Mutex()

    private suspend fun sendSetupPacketAsync() = sendViaSocketAsync(createPacket(Constants.encodeLength(sendLength)))

    suspend fun sendDataAsync(data: T) {
        val send = lastSetupMutex.withLock {
            if(lastSetup++ >= setupFrequency) {
                lastSetup = 0
                return@withLock true
            }
            return@withLock false
        }

        if(send) sendSetupPacketAsync()

        sendViaSocketAsync(createPacket(processor.invoke(data), sendLength))
    }

    private fun createPacket(data: ByteArray, length: Int = data.size) = DatagramPacket(data, length, address, Constants.PORT)

    private suspend fun sendViaSocketAsync(packet: DatagramPacket) = coroutineScope {
        //withContext(Dispatchers.IO) {
        try {
            socket.send(packet)
        }
        catch (ignored: PortUnreachableException) {

        }
        //}
    }
}


class Server<T: UgaBuga>(val processor: suspend (ByteArray) ->  T,
                         val channel: Channel<T> = Channel(capacity = Channel.CONFLATED),
                         @Volatile private var packetLength: Int = Constants.SETUP_HEADER_LENGTH,
                         @Volatile private var initialized: Boolean = false,
                         private val socket: DatagramSocket = DatagramSocket(Constants.PORT),

            ) {

    var totalReceivedAmount = 0L

    fun recreateIfNeeded(inputArray: ByteArray?): ByteArray  {
        val size = packetLength
        if (inputArray == null || inputArray.size != packetLength) {
            return ByteArray(size)
        }

        return inputArray
    }

    /**
     * Don't share the inputArrays!
     */
    suspend fun receiveAsync(inputArray: ByteArray = ByteArray(packetLength)): Boolean = coroutineScope {
        val array = recreateIfNeeded(inputArray)
        val size = array.size

        val request = DatagramPacket(ByteArray(size), size)

        //withContext(Dispatchers.IO) {
            socket.receive(request)
        //}

        assert(request.data === array)

        val data = request.data
        val dataLength = request.length

        if(data == null) {
            log("Silently dropped packet: Missing data", request)
            return@coroutineScope false
        }

        val initializingPacket = dataLength == Constants.SETUP_HEADER_LENGTH
        if(!initialized) {
            if(!initializingPacket) {
                // Silently drop!
                log("Silently dropped packet: Not Initialized", request)
                return@coroutineScope false
            }
            initialized = true
            packetLength = Constants.decodeLength(data)
            log("Setup with length $packetLength", request)
            return@coroutineScope false
        }
        else {
            if(dataLength != size) {
                // Silently drop!
                // log("Silently dropped packet: Incorrect length", request)
                return@coroutineScope false
            }

            //log("Received",request)
            totalReceivedAmount += 1

            channel.send(processor(request.data))
            return@coroutineScope true
        }
    }


}

suspend fun runServer(scope: CoroutineScope, timeSeconds: Int=10, receiverThreads: Int = 10, serverThreads: Int = 10, packetSize: Int = 2560) {


    @OptIn(DelicateCoroutinesApi::class)
    val serverThreadPool = newFixedThreadPoolContext(serverThreads, "Server")

    @OptIn(DelicateCoroutinesApi::class)
    val receiversThreadPool = newFixedThreadPoolContext(receiverThreads, "Receivers")

    val server = Server<UgaBuga>({
        UgaBuga()
    })


    val tookMillis = measureTimeMillis {
        repeat(serverThreads) {
            scope.launch(serverThreadPool) {
                try {
                    while (true) {
                        server.receiveAsync()
                    }
                } finally {
                    println("Server broken!")
                }
            }
        }

        repeat(receiverThreads) {
            scope.launch(receiversThreadPool) {
                // println("Start receive")

                while (true) {
                    val received = server.channel.receiveCatching()
                    //println("Received in ${Thread.currentThread().name}")
                }
            }
        }



        repeat(timeSeconds) {
            delay(1000)
            println(it + 1)
        }
    }


    println("kill!")
    scope.cancel()

    val packetPerSecond = 1000 * (server.totalReceivedAmount / tookMillis.toDouble())
    val bytesPerSecond = packetPerSecond * 2560
    println("${server.totalReceivedAmount} in $tookMillis millis = $packetPerSecond pps = ${bytesPerSecond.toLong()} bps")



    /*


     */
    /*

    server.channel.close()
    println("Closed channel!")
    serverThreadPool.close()
    println("Closed server thread!")
    receiversThreadPool.close()
    println("Closed receiver threads!")
     */
}

fun runClient(scope: CoroutineScope, senderThreads: Int = 10, packetSize: Int = 2560, packetAmountPerThread: Int = Integer.MAX_VALUE) {

    @OptIn(DelicateCoroutinesApi::class)
    val clientThreadPool = newFixedThreadPoolContext(senderThreads,"Client")



    repeat(senderThreads) {
        scope.launch(clientThreadPool) {
            //println("Start send")
            val client = Client<UgaBuga>({
                ByteArray(packetSize)
            }, packetSize)

            repeat(packetAmountPerThread) {
                //if(it % 1000 == 0) println("Prepare to send $it")
                client.sendDataAsync(UgaBuga())
                //println("Send data $it")
            }
        }
    }

}

fun main(): Unit = runBlocking {


    runClient(this, packetSize = 1)
    runServer(scope=this, timeSeconds = 3, packetSize = 1)



    //264888318
    //1546205808
    //1582418155
    //2147483647
    //2147483647
    //2147483647
    //2334883246
    //
}


