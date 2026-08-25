package com.minidex.app.input.adb

import android.content.Context
import android.util.Log
import dadb.AdbKeyPair
import io.github.muntashirakon.adb.PairingConnectionCtx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Handles pairing and authentication with the on-device Wireless Debugging daemon.
 * Uses libadb-android's PairingConnectionCtx for proper SPAKE2+TLS handshake.
 */
class AdbPairingClient(private val context: Context) {

    companion object {
        private const val TAG = "AdbPairingClient"
        private const val KEY_FILE_PRIVATE = "minidex_adb.priv"
        private const val CERT_FILE = "minidex_adb.crt"
        private const val DEVICE_NAME = "MiniDex"
    }

    // Lazy-load the persistent RSA private key and self-signed certificate
    private val keyAndCert by lazy { loadOrGenerateKeyAndCert() }

    /**
     * Attempts to pair with the on-device ADB daemon using the proper SPAKE2 protocol.
     * The pairing code is the 6-digit PIN displayed in Developer Options → Wireless Debugging.
     */
    suspend fun pair(host: String = "127.0.0.1", port: Int, pin: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Pairing via libadb-android PairingConnectionCtx to $host:$port")

            val (privateKey, certificate) = keyAndCert
            val pairingCtx = PairingConnectionCtx(
                host,
                port,
                pin.toByteArray(Charsets.UTF_8),
                privateKey,
                certificate,
                DEVICE_NAME
            )

            pairingCtx.use { ctx ->
                ctx.start()
            }

            Log.i(TAG, "Pairing succeeded with $host:$port")
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Wireless pairing exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Returns a dadb-compatible AdbKeyPair for the Dadb TCP connection layer.
     */
    fun getAdbKeyPair(): AdbKeyPair {
        val (privateKey, _) = keyAndCert
        val pubKeyBytes = AdbCrypto.loadOrGenerate(context).getAdbPublicKeyPayload()
        return AdbKeyPair(
            privateKey = privateKey,
            publicKeyBytes = pubKeyBytes
        )
    }

    /**
     * Loads or generates a persistent RSA private key + self-signed X.509 certificate
     * for use with PairingConnectionCtx.
     */
    private fun loadOrGenerateKeyAndCert(): Pair<java.security.PrivateKey, X509Certificate> {
        val privFile = File(context.filesDir, KEY_FILE_PRIVATE)
        val certFile = File(context.filesDir, CERT_FILE)

        if (privFile.exists() && certFile.exists()) {
            try {
                val kf = KeyFactory.getInstance("RSA")
                val privBytes = FileInputStream(privFile).use { it.readBytes() }
                val privateKey = kf.generatePrivate(PKCS8EncodedKeySpec(privBytes))

                val cf = CertificateFactory.getInstance("X.509")
                val cert = FileInputStream(certFile).use { cf.generateCertificate(it) as X509Certificate }

                Log.d(TAG, "Loaded existing RSA key and certificate")
                return Pair(privateKey, cert)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load existing keys, regenerating", e)
                privFile.delete()
                certFile.delete()
            }
        }

        // Generate new 2048-bit RSA keypair
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()

        // Generate self-signed X.509 certificate using Android's built-in APIs
        val cert = generateSelfSignedCert(kp)

        // Persist
        FileOutputStream(privFile).use { it.write(kp.private.encoded) }
        FileOutputStream(certFile).use { it.write(cert.encoded) }

        Log.i(TAG, "Generated new RSA key and self-signed certificate")
        return Pair(kp.private, cert)
    }

    /**
     * Generates a self-signed X.509 v3 certificate valid for 25 years.
     */
    private fun generateSelfSignedCert(keyPair: java.security.KeyPair): X509Certificate {
        val now = System.currentTimeMillis()
        val notBefore = java.util.Date(now - 86_400_000L) // 1 day ago
        val notAfter = java.util.Date(now + 25L * 365 * 86_400_000L) // 25 years from now
        val serial = java.math.BigInteger.valueOf(now)
        val issuer = "CN=$DEVICE_NAME"

        // Use Android's hidden X509V3CertificateGenerator replacement:
        // android.security.keystore is not available for self-signed certs,
        // so we'll use the Bouncy Castle provider bundled with libadb-android
        val certBuilder = org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
            javax.security.auth.x500.X500Principal(issuer),
            serial,
            notBefore,
            notAfter,
            javax.security.auth.x500.X500Principal(issuer),
            keyPair.public
        )

        val signer = org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withRSA")
            .build(keyPair.private)
        val certHolder = certBuilder.build(signer)

        return org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
            .getCertificate(certHolder)
    }
}
