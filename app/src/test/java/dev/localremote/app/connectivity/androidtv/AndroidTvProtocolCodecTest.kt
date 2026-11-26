package dev.localremote.app.connectivity.androidtv

import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AndroidTvProtocolCodecTest {
    @Test
    fun protobufWriterAndReaderRoundTripVarintAndBytes() {
        val encoded = ProtoWriter()
            .varint(1, 150)
            .bytes(2, byteArrayOf(1, 2, 3))
            .toByteArray()

        assertArrayEquals(
            byteArrayOf(8, 0x96.toByte(), 1, 18, 3, 1, 2, 3),
            encoded
        )

        val decoded = ProtoReader.parse(encoded)
        assertEquals(150L, ProtoReader.firstNumber(decoded, 1))
        assertArrayEquals(byteArrayOf(1, 2, 3), ProtoReader.firstBytes(decoded, 2))
    }

    @Test
    fun protobufReaderRejectsTruncatedLengthDelimitedField() {
        assertThrows(IllegalArgumentException::class.java) {
            ProtoReader.parse(byteArrayOf(18, 4, 1, 2))
        }
    }

    @Test
    fun pairingSecretMatchesKnownSha256Vector() {
        val (client, server) = publicKeyFixtures()

        val secret = AndroidTvPairingSecret.calculate("C00000", client, server)

        assertEquals(
            "c04c3dc271c4cae277b81899a4a7550a269addc4916fe800628dfc7529b596ac",
            secret.toHex()
        )
    }

    @Test
    fun pairingSecretRejectsWrongDisplayedCheckByte() {
        val (client, server) = publicKeyFixtures()

        assertThrows(IllegalArgumentException::class.java) {
            AndroidTvPairingSecret.calculate("C10000", client, server)
        }
    }

    private fun publicKeyFixtures(): Pair<RSAPublicKey, RSAPublicKey> {
        val factory = KeyFactory.getInstance("RSA")
        val exponent = BigInteger.valueOf(65537)
        // Fixed 1024-bit public moduli are accepted by the JCA RSA provider.
        // These public values are test-only; no private key material is included.
        val client = factory.generatePublic(
            RSAPublicKeySpec(
                BigInteger(
                    "8492B88A224F254AC34B1F3F1B0E8E478A023CFC7F657493E9A6D9CB8CFA85CF77ABF38BF4B5319E891B08F9135669041D5925A705D1BC22089D6ED08B92E684EC775B16507F2F31ED7647460D3573E19F34BE7F105FAF0BDF82642C84A990BEBEDB2A54F99456577C40762910E082AF86C975879995BA893D4CB593CC497E55",
                    16
                ),
                exponent
            )
        ) as RSAPublicKey
        val server = factory.generatePublic(
            RSAPublicKeySpec(
                BigInteger(
                    "93FCCE5B28B4980EE8640367D4AB2ABEB02746D626F2D65C2D754D046D8554B436793A3A6264962D924A7B5A897E0D1F587704C25D977EF7B93EAD1D5CB894AF769CAF5C493C14A76664B47825A200B57AA93E6A72F74B90568B86A4AD4836D45BB2E1215BF969867FF2BAE09532C5E854D582143DF5A999AB7213EDD867B4CF",
                    16
                ),
                exponent
            )
        ) as RSAPublicKey
        return client to server
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}
