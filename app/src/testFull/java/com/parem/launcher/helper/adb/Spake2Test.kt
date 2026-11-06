package com.parem.launcher.helper.adb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Vectors produced by BoringSSL's own SPAKE2 (spake25519.cc at 9e04aed, run
 * with fixed private inputs) via the oracle harness published with
 * Flyfish233/spake2-java. Matching them byte for byte is what lets Parem pair
 * with adbd, which uses exactly that code.
 */
class Spake2Test {

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun exchange(
        password: String, aliceName: String, bobName: String,
        alicePrivate: String, bobPrivate: String,
        aliceMessage: String, bobMessage: String, key: String,
    ) {
        val alice = Spake2(true, hex(aliceName), hex(bobName), hex(password), hex(alicePrivate))
        val bob = Spake2(false, hex(bobName), hex(aliceName), hex(password), hex(bobPrivate))
        assertArrayEquals(hex(aliceMessage), alice.message)
        assertArrayEquals(hex(bobMessage), bob.message)
        assertArrayEquals(hex(key), alice.processMessage(bob.message))
        assertArrayEquals(hex(key), bob.processMessage(alice.message))
    }

    private val adbClient = "616462207061697220636c69656e7400" // "adb pair client\0"
    private val adbServer = "61646220706169722073657276657200" // "adb pair server\0"
    private val bobInput = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f" + "00".repeat(32)

    @Test
    fun adbNamesMatchBoringSsl() = exchange(
        password = "353135313039", aliceName = adbClient, bobName = adbServer,
        alicePrivate = "01" + "00".repeat(63), bobPrivate = bobInput,
        aliceMessage = "1876811ed2c78beb885abcfeee9174e822e056d67474d7f924e5c019d087dc71",
        bobMessage = "e814a51e5e1032ce32bd9e47f74fb58a42f02b61b355e062a36836dbd8de35c5",
        key = "5c1a3ba6d4176e4f1cce1b33d22d10ea5c94078c35d6adde6b01365f0df35d1f50b7a5ad6910bfc17295e5d1969e502a6141cdc5c7e2cc99b238d786c9275865",
    )

    @Test
    fun privateInputOfOrderMinusOneMatchesBoringSsl() = exchange(
        password = "353135313039", aliceName = "616c696365", bobName = "626f62",
        alicePrivate = "ecd3f55c1a631258d69cf7a2def9de1400000000000000000000000000000010" + "00".repeat(32),
        bobPrivate = bobInput,
        aliceMessage = "58e879f7c7c7dee563d11a0764f8e323dd0c2f46799b69de637409343c9094c5",
        bobMessage = "e814a51e5e1032ce32bd9e47f74fb58a42f02b61b355e062a36836dbd8de35c5",
        key = "4cda88ce6753f4dc818b81411b8b12400ac6d038deb1b18c96c4b6e20da80e7b2ceae5eba89db3b9b3de52d9222e2d1fb0049b4c474c3d55d5e2fc9f40666419",
    )

    // The three below have password scalars that are 2, 4 and 5 mod 8, so they
    // exercise BoringSSL's "add multiples of the order" password-scalar hack.

    @Test
    fun passwordScalarHackTwoMatchesBoringSsl() = exchange(
        password = "86a0", aliceName = "", bobName = "",
        alicePrivate = "09c66af6279d7417cfdb73103d1cbb4dece145ad21aeb5ebfc38d0e95ba2476632170eea16ba67c1a28adb6c6dafc6c51cda14516dd0c5e0aaf63b4e28604dfa",
        bobPrivate = "4a76f1ef4ef1df9c53ef31ed85106dcfabefeb92ffe1592b471c0f69d375e0a109b1e7ad106c7e3c9f5f52e4ece2661ee0774240cf97cb6388aeba68ab3b3da7",
        aliceMessage = "94a2e5aab450887fd06d6942f81fe322ca56ca4119479439ae739da50a3620e7",
        bobMessage = "72be771439e1d47aebb8eb9fbb18bd20473a4b13b3a2a76e24fc83d23a62520f",
        key = "8e1b811d6330bdf91951da1ebe46d6579dca1da58a4b4359747dcf3d8f84a105f3eaff7f5ba53ac2105b1217d750ad0f63b143590cb3db9d066492b82b26c665",
    )

    @Test
    fun passwordScalarHackFourMatchesBoringSsl() = exchange(
        password = "840416b4509b", aliceName = "", bobName = "",
        alicePrivate = "34e4d32806638cc09e54ae077ec01bb45286af607a5a97d7d4154c164329c936428962fe0f6ac4c61655912f56a8bf7b0a3d3d4f80783604d37a451f64af208b",
        bobPrivate = "9220814b732bf48777855d9d23723ed9e4e4c49d6ed224eeec11380ab54b475927444d7f5903795e1c303c434b2984fd31fbf4ac000989f1d669a728ea91d4d1",
        aliceMessage = "add36d6cfb6ab8b5c15f6ff6eeffdddab4f5c2a13c04e3ff7161f9dae284e11b",
        bobMessage = "48339418cd9b94d187dac9a262bedc5fbfe6ecfb9f51487318e3259fa133be8e",
        key = "87971045437f20a1584d64c1d67fb0d60f0008156df4352fd0113a8b783270063c230a98e5ddcee86fcb19ad168dcb140f38563e2ad1051ac46d05e09afab7f2",
    )

    @Test
    fun passwordScalarHackFiveMatchesBoringSsl() = exchange(
        password = "3f5d7ea872afec67c03a926c303d6738", aliceName = "0a", bobName = "",
        alicePrivate = "69de96ad8d4836cdcc4d436320f29b7a2a687601dbfad581e0643fbfe75f4b818e5a0a9a3871856d326aa2719eff8eeb9af276cf589f375499b5521a423a0166",
        bobPrivate = "7e3db3ec8f02d423e473f6e0e20227986c1519dde94af3b09996a8d2692bdb27b80cfd3d6b048077cf93d9df506bbd2ec5bca9505e40d0a8d9b24be1e6dfe6f8",
        aliceMessage = "d59f3e13cb9ad28d8674db7e328f71f25995477026c30c6a2742e74dc05369b4",
        bobMessage = "3b2041effea99ebd9df5d1589cbe59812cc7c0d348120a670e0ccf3bce09de98",
        key = "d127c562f7e9a8b5d55d39a1625fffb93acd8c3ef9070674b2ba8e6ebe0c0a5b9fcab87a87fbc4c588e27e01d910909abd23bc2f735c432e4db9a782072d5cc0",
    )

    @Test
    fun wrongPasswordGivesDifferentKeys() {
        val alice = Spake2(true, AdbWire.CLIENT_NAME, AdbWire.SERVER_NAME, "123456".toByteArray(), hex(bobInput))
        val bob = Spake2(false, AdbWire.SERVER_NAME, AdbWire.CLIENT_NAME, "123457".toByteArray(), hex("02" + "00".repeat(63)))
        assertNotEquals(alice.processMessage(bob.message)!!.toList(), bob.processMessage(alice.message)!!.toList())
    }

    @Test
    fun rejectsShortMessage() {
        val alice = Spake2(true, AdbWire.CLIENT_NAME, AdbWire.SERVER_NAME, "123456".toByteArray(), hex(bobInput))
        assertNull(alice.processMessage(ByteArray(31)))
        assertEquals(32, alice.message.size)
    }
}
