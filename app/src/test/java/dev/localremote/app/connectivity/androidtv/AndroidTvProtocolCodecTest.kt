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
        val factory = KeyFactory.getInstance("RSA")
        val client = factory.generatePublic(
            RSAPublicKeySpec(BigInteger.valueOf(3233), BigInteger.valueOf(17))
        ) as RSAPublicKey
        val server = factory.generatePublic(
            RSAPublicKeySpec(BigInteger.valueOf(2773), BigInteger.valueOf(13))
        ) as RSAPublicKey

        val secret = AndroidTvPairingSecret.calculate("611234", client, server)

        assertEquals(
            "6141428ba5eb45b97f9056588b41119d47fcd99cc5d799f5aa972a2db5d1f722",
            secret.toHex()
        )
    }

    @Test
    fun pairingSecretRejectsWrongDisplayedCheckByte() {
        val factory = KeyFactory.getInstance("RSA")
        val client = factory.generatePublic(
            RSAPublicKeySpec(BigInteger.valueOf(3233), BigInteger.valueOf(17))
        ) as RSAPublicKey
        val server = factory.generatePublic(
            RSAPublicKeySpec(BigInteger.valueOf(2773), BigInteger.valueOf(13))
        ) as RSAPublicKey

        assertThrows(IllegalArgumentException::class.java) {
            AndroidTvPairingSecret.calculate("621234", client, server)
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}
