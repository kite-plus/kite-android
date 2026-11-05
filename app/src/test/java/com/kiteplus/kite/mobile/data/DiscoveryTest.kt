package com.kiteplus.kite.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The ordering rules the sweep depends on, checked without a network.
 */
class DiscoveryTest {

    @Test
    fun `every private subnet this phone is on is swept`() {
        // A phone can hold both a hotspot address and the home one; guessing
        // one of them used to lose the server entirely.
        val prefixes = prefixesOf(
            listOf("192.168.1.5", "10.0.0.7", "172.30.56.216"),
        )
        assertEquals(listOf("192.168.1", "10.0.0", "172.30.56"), prefixes)
    }

    @Test
    fun `addresses that cannot lead to the server are dropped`() {
        val prefixes = prefixesOf(
            listOf(
                "127.0.0.1", // the phone itself
                "169.254.10.20", // link local: no lease, nothing to find
                "172.32.0.4", // outside 172.16/12
                "100.64.0.9", // carrier grade NAT
                "261.1.1.1", // not an address at all
                "not an address",
            ),
        )
        assertEquals(emptyList<String>(), prefixes)
    }

    @Test
    fun `one subnet is swept once however many addresses share it`() {
        val prefixes = prefixesOf(listOf("192.168.1.5", "192.168.1.6", "192.168.1.7"))
        assertEquals(listOf("192.168.1"), prefixes)
    }
}
