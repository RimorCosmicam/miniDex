package com.minidex.app.input.adb

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * Generates and persists standard 2048-bit RSA keys for ADB authentication on localhost.
 */
class AdbCrypto private constructor(val keyPair: KeyPair) {

    companion object {
        private const val KEY_FILE_PRIVATE = "minidex_adb.priv"
        private const val KEY_FILE_PUBLIC = "minidex_adb.pub"

        fun loadOrGenerate(context: Context): AdbCrypto {
            val privFile = File(context.filesDir, KEY_FILE_PRIVATE)
            val pubFile = File(context.filesDir, KEY_FILE_PUBLIC)

            if (privFile.exists() && pubFile.exists()) {
                try {
                    val kf = KeyFactory.getInstance("RSA")

                    val privBytes = FileInputStream(privFile).use { it.readBytes() }
                    val privateKey = kf.generatePrivate(PKCS8EncodedKeySpec(privBytes))

                    val pubBytes = FileInputStream(pubFile).use { it.readBytes() }
                    val publicKey = kf.generatePublic(X509EncodedKeySpec(pubBytes))
                    require(
                        (privateKey as? RSAPrivateCrtKey)?.modulus ==
                            (publicKey as? RSAPublicKey)?.modulus
                    ) { "Stored ADB private and public keys do not match" }

                    return AdbCrypto(KeyPair(publicKey, privateKey))
                } catch (_: Exception) {
                    privFile.delete()
                    pubFile.delete()
                }
            }

            // Generate new 2048-bit RSA keypair
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048)
            val kp = kpg.generateKeyPair()

            FileOutputStream(privFile).use { it.write(kp.private.encoded) }
            FileOutputStream(pubFile).use { it.write(kp.public.encoded) }

            return AdbCrypto(kp)
        }
    }

}
