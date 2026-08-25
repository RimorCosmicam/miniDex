package com.minidex.app.input.adb

import android.content.Context
import android.os.Build
import android.util.Log
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit

/**
 * Handles pairing and authentication with the on-device Wireless Debugging daemon.
 * Uses libadb-android's PairingConnectionCtx for proper SPAKE2+TLS handshake.
 */
class AdbPairingClient(private val context: Context) : AbsAdbConnectionManager() {

    companion object {
        private const val TAG = "AdbPairingClient"
        private const val CERT_FILE = "minidex_adb.crt"
        private const val DEVICE_NAME = "MiniDex"
    }

    private val adbCrypto by lazy { AdbCrypto.loadOrGenerate(context) }

    // The certificate and every ADB authentication operation must use this same keypair.
    private val keyAndCert by lazy { loadOrGenerateKeyAndCert() }

    init {
        setApi(Build.VERSION.SDK_INT)
        setTimeout(8, TimeUnit.SECONDS)
        setThrowOnUnauthorised(true)
    }

    override fun getPrivateKey() = keyAndCert.first

    override fun getCertificate() = keyAndCert.second

    override fun getDeviceName() = DEVICE_NAME

    /**
     * Attempts to pair with the on-device ADB daemon using the proper SPAKE2 protocol.
     * The pairing code is the 6-digit PIN displayed in Developer Options → Wireless Debugging.
     */
    suspend fun pairDevice(host: String = "127.0.0.1", port: Int, pin: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Pairing via libadb-android PairingConnectionCtx to $host:$port")

            val paired = super.pair(host, port, pin)
            check(paired) { "The device rejected the pairing request" }

            Log.i(TAG, "Pairing succeeded with $host:$port")
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Wireless pairing exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun connectAdb(host: String, port: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            disconnect()
            check(super.connect(host, port)) { "ADB rejected the TLS connection" }
            true
        }
    }

    suspend fun executeShell(command: String): String = withContext(Dispatchers.IO) {
        openStream("shell:$command").use { stream ->
            stream.openInputStream().bufferedReader().use { it.readText() }
        }
    }

    /** Opens a bidirectional shell command that must remain alive (for example `hid -`). */
    fun openShellStream(command: String): AdbStream = openStream("shell:$command")

    /**
     * Loads or generates a persistent RSA private key + self-signed X.509 certificate
     * for use with PairingConnectionCtx.
     */
    private fun loadOrGenerateKeyAndCert(): Pair<java.security.PrivateKey, X509Certificate> {
        val certFile = File(context.filesDir, CERT_FILE)
        val keyPair = adbCrypto.keyPair

        if (certFile.exists()) {
            try {
                val cf = CertificateFactory.getInstance("X.509")
                val cert = FileInputStream(certFile).use { cf.generateCertificate(it) as X509Certificate }
                require(cert.publicKey.encoded.contentEquals(keyPair.public.encoded)) {
                    "Stored ADB certificate belongs to a different key"
                }

                Log.d(TAG, "Loaded existing RSA key and certificate")
                return Pair(keyPair.private, cert)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load matching certificate, regenerating", e)
                certFile.delete()
            }
        }

        val cert = generateSelfSignedCert(keyPair)

        FileOutputStream(certFile).use { it.write(cert.encoded) }

        Log.i(TAG, "Generated a certificate for the persistent ADB key")
        return Pair(keyPair.private, cert)
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
