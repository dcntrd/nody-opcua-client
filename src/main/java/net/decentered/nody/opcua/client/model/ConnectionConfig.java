package net.decentered.nody.opcua.client.model;

import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;

/**
 * Immutable value object that carries all parameters needed to open an
 * OPC UA session: endpoint URL, security policy and message security mode.
 *
 * <p>Valid combinations (per OPC UA Part 4):</p>
 * <pre>
 *   Policy None                  -> Mode None only
 *   Policy Basic128Rsa15         -> Mode Sign | Sign &amp; Encrypt
 *   Policy Basic256              -> Mode Sign | Sign &amp; Encrypt
 *   Policy Basic256Sha256        -> Mode Sign | Sign &amp; Encrypt
 *   Policy Aes128_Sha256_RsaOaep -> Mode Sign | Sign &amp; Encrypt
 *   Policy Aes256_Sha256_RsaPss  -> Mode Sign | Sign &amp; Encrypt
 * </pre>
 */

public record ConnectionConfig(
        String endpointUrl,
        SecurityPolicy securityPolicy,
        MessageSecurityMode securityMode
) {
    /** Convenience factory for an unsecured connection. */
    public static ConnectionConfig none(String url) {
        return new ConnectionConfig(url, SecurityPolicy.None, MessageSecurityMode.None);
    }

    /**
     * Returns true when the combination is valid per the OPC UA specification.
     * None policy must use None mode; all other policies require Sign or
     * SignAndEncrypt.
     */
    public boolean isValid() {
        if (securityPolicy == SecurityPolicy.None) {
            return securityMode == MessageSecurityMode.None;
        }
        return securityMode == MessageSecurityMode.Sign
                || securityMode == MessageSecurityMode.SignAndEncrypt;
    }

    @Override
    public String toString() {
        return endpointUrl + "  [" + securityPolicy.name() + " / " + securityMode + "]";
    }
}