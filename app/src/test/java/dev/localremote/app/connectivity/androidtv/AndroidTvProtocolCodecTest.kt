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

        val secret = AndroidTvPairingSecret.calculate("441234", client, server)

        assertEquals(
            "44b9a40f46e00835899299c59d664ce88073baa0f609ca2dd3866cdc538d535f",
            secret.toHex()
        )
    }

    @Test
    fun pairingSecretRejectsWrongDisplayedCheckByte() {
        val (client, server) = publicKeyFixtures()

        assertThrows(IllegalArgumentException::class.java) {
            AndroidTvPairingSecret.calculate("451234", client, server)
        }
    }

    private fun publicKeyFixtures(): Pair<RSAPublicKey, RSAPublicKey> {
        val factory = KeyFactory.getInstance("RSA")
        val exponent = BigInteger.valueOf(65537)
        val client = factory.generatePublic(
            RSAPublicKeySpec(
                BigInteger(
                    "D3A5B7C9E1F2031425364758697A8B9CADBECFE0F102132435465768798A9BACBDCEDFF00112233445566778899AABBCCDDEEFF112233445566778899AABBCCD",
                    16
                ),
                exponent
            )
        ) as RSAPublicKey
        val server = factory.generatePublic(
            RSAPublicKeySpec(
                BigInteger(
                    "C7D9EBFD0F21334557697B8D9FA1B3C5D7E9FB0D1F31435567798B9DAFC1D3E5F7192B3D4F5163758799BADCFE1032547698BADCFEDCBA9876543210FEDCBA987654321",
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
