package com.linknest.app.data.metadata

import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.util.Locale

class MetadataSecurityPolicy(
    private val resolver: (String) -> Array<InetAddress> = { InetAddress.getAllByName(it) }
) {
    fun validate(url: URI): Result<Unit> {
        val scheme = url.scheme?.lowercase(Locale.US)
        if (scheme != "http" && scheme != "https") {
            return Result.failure(MetadataFetchException("Only http and https URLs are supported."))
        }
        if (url.userInfo != null) {
            return Result.failure(MetadataFetchException("URLs with embedded credentials are not supported."))
        }
        val host = url.host?.trim()?.trim('[', ']')?.lowercase(Locale.US)
        if (host.isNullOrBlank() || host == "localhost" || host.endsWith(".localhost")) {
            return Result.failure(MetadataFetchException("That host is not publicly reachable."))
        }
        if (url.port !in -1..65535) {
            return Result.failure(MetadataFetchException("The URL port is invalid."))
        }

        val addresses = runCatching { resolver(host).toList() }.getOrElse {
            return Result.failure(MetadataFetchException("The host could not be resolved."))
        }
        if (addresses.isEmpty() || addresses.any(::isProhibitedAddress)) {
            return Result.failure(MetadataFetchException("That host resolves to a private or local address."))
        }
        return Result.success(Unit)
    }

    private fun isProhibitedAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return true

        val bytes = address.address
        if (bytes.size == 4) return isProhibitedIpv4(bytes)

        if (address is Inet6Address && bytes.size == 16) {
            // HttpURLConnection may resolve an IPv4-mapped/compatible IPv6 literal
            // even when the caller supplied a loopback or private IPv4 address.
            val mappedIpv4 = when {
                bytes.take(10).all { it == 0.toByte() } &&
                    bytes[10].toInt() and 0xff == 0xff && bytes[11].toInt() and 0xff == 0xff -> bytes.copyOfRange(12, 16)
                bytes.take(12).all { it == 0.toByte() } -> bytes.copyOfRange(12, 16)
                else -> null
            }
            if (mappedIpv4 != null && isProhibitedIpv4(mappedIpv4)) return true

            val first = bytes[0].toInt() and 0xff
            if (first and 0xfe == 0xfc) return true // fc00::/7 unique-local IPv6
            if (bytes[0].toInt() and 0xff == 0x20 && bytes[1].toInt() and 0xff == 0x01 &&
                bytes[2].toInt() and 0xff == 0x0d && bytes[3].toInt() and 0xff == 0xb8
            ) return true // 2001:db8::/32 documentation range
            if (bytes[0].toInt() and 0xff == 0x20 && bytes[1].toInt() and 0xff == 0x01 &&
                bytes[2].toInt() and 0xff == 0x00 && bytes[3].toInt() and 0xff == 0x02 &&
                bytes[4] == 0.toByte() && bytes[5] == 0.toByte()
            ) return true // 2001:2::/48 benchmarking range
        }
        return false
    }

    private fun isProhibitedIpv4(bytes: ByteArray): Boolean {
        if (bytes.size != 4) return true
        val first = bytes[0].toInt() and 0xff
        val second = bytes[1].toInt() and 0xff
        val third = bytes[2].toInt() and 0xff
        return when {
            first == 0 || first == 10 || first == 127 -> true
            first == 100 && second in 64..127 -> true // RFC 6598 shared address space
            first == 169 && second == 254 -> true
            first == 172 && second in 16..31 -> true
            first == 192 && second == 0 && third == 0 -> true
            first == 192 && second == 0 && third == 2 -> true // TEST-NET-1
            first == 192 && second == 168 -> true
            first == 198 && second in 18..19 -> true // benchmarking
            first == 198 && second == 51 && third == 100 -> true // TEST-NET-2
            first == 203 && second == 0 && third == 113 -> true // TEST-NET-3
            first >= 224 -> true
            else -> false
        }
    }
}
