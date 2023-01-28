package muesli1.directvideostream

import java.nio.ByteBuffer

object Constants {
    const val PORT: Int = 19640
    const val SETUP_HEADER_LENGTH: Int = 4

    fun decodeLength(data: ByteArray): Int {
        //return (data[0].toInt() shl 0) or (data[1].toInt() shl 8) or (data[2].toInt() shl 16) or (data[3].toInt() shl 24)
        return ByteBuffer.wrap(data).int
    }
    fun encodeLength(size: Int, array: ByteArray = ByteArray(SETUP_HEADER_LENGTH)): ByteArray {
        /*assert(array.size >= SETUP_HEADER_LENGTH)

        array[0] = (size and (Byte.MAX_VALUE.toInt())).toByte()
        array[1] = ((size shr 8) and (Byte.MAX_VALUE.toInt())).toByte()
        array[2] = ((size shr 16) and (Byte.MAX_VALUE.toInt())).toByte()
        array[3] = ((size shr 24) and (Byte.MAX_VALUE .toInt())).toByte()

        return array*/
        return ByteBuffer.allocate(SETUP_HEADER_LENGTH).putInt(size).array()
    }
}