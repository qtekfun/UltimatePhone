package com.qtekfun.ultimatephone.core.datapacks

import java.util.Base64
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

/** The key the app trusts for data manifests. The private half only exists as a secret of the data repository. */
object DataPackKeys {
    const val MANIFEST_PUBLIC_KEY = "93NQ1gRJSj3HNkz7IqVhq+BMDCbpiKuKNdFothEGvEk="
}

/**
 * Checks the detached Ed25519 signature of `manifest.json` (`manifest.json.sig`, base64 of the 64-byte signature over the
 * exact bytes of the manifest). Nothing from a manifest is used before this returns true.
 */
class ManifestVerifier(publicKeyBase64: String = DataPackKeys.MANIFEST_PUBLIC_KEY) {
    private val publicKey = Base64.getDecoder().decode(publicKeyBase64).also {
        require(it.size == Ed25519PublicKeyParameters.KEY_SIZE) { "An Ed25519 public key is 32 bytes" }
    }

    fun verify(manifest: ByteArray, signatureBase64: String): Boolean {
        val signature = try {
            Base64.getDecoder().decode(signatureBase64.trim())
        } catch (_: IllegalArgumentException) {
            return false
        }
        if (signature.size != SIGNATURE_SIZE) return false
        val verifier = Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(publicKey, 0))
        verifier.update(manifest, 0, manifest.size)
        return verifier.verifySignature(signature)
    }

    private companion object {
        const val SIGNATURE_SIZE = 64
    }
}
