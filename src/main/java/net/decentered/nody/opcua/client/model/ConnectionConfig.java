package net.decentered.nody.opcua.client.model;

import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;

import java.nio.file.Path;
import java.util.Arrays;

/**
 * Immutable value object carrying all parameters needed to open an OPC UA
 * session: endpoint URL, security policy, message security mode, and identity.
 *
 * <h3>Identity modes</h3>
 * <ul>
 *   <li>{@link AuthMode#ANONYMOUS} – no credentials</li>
 *   <li>{@link AuthMode#USERNAME_PASSWORD} – username + password (char[])</li>
 *   <li>{@link AuthMode#USER_CERTIFICATE} – PKCS#12 keystore containing the
 *       user's X.509 certificate and private key</li>
 * </ul>
 *
 * <p>Sensitive fields ({@code password}, {@code userCertPassword}) are
 * {@code char[]} arrays so they can be zeroed after use.  Call
 * {@link #clearSecrets()} once the connection attempt is done.</p>
 */
public final class ConnectionConfig {

    private final String              endpointUrl;
    private final SecurityPolicy      securityPolicy;
    private final MessageSecurityMode securityMode;
    private final AuthMode            authMode;

    private final String  username;
    private final char[]  password;          // zeroed by clearSecrets()

    private final Path    userCertPath;      // path to PKCS#12 keystore
    private final char[]  userCertPassword;  // zeroed by clearSecrets()

    public static ConnectionConfig anonymous(String url) {
        return new ConnectionConfig(url,
                SecurityPolicy.None, MessageSecurityMode.None,
                AuthMode.ANONYMOUS, "", new char[0], null, new char[0]);
    }

    public ConnectionConfig(String url,
                            SecurityPolicy policy,
                            MessageSecurityMode mode,
                            AuthMode authMode,
                            String username,
                            char[] password,
                            Path userCertPath,
                            char[] userCertPassword) {
        this.endpointUrl      = url;
        this.securityPolicy   = policy;
        this.securityMode     = mode;
        this.authMode         = authMode;
        this.username         = (username != null) ? username : "";
        this.password         = copy(password);
        this.userCertPath     = userCertPath;
        this.userCertPassword = copy(userCertPassword);
    }

    public String              endpointUrl()      { return endpointUrl; }
    public SecurityPolicy      securityPolicy()   { return securityPolicy; }
    public MessageSecurityMode securityMode()     { return securityMode; }
    public AuthMode            authMode()         { return authMode; }
    public String              username()         { return username; }
    public char[]              password()         { return copy(password); }
    public Path                userCertPath()     { return userCertPath; }
    public char[]              userCertPassword() { return copy(userCertPassword); }

    /**
     * Zeros all sensitive char[] buffers (password and user-cert password).
     * Call this once the connect attempt has completed or failed.
     */
    public void clearSecrets() {
        Arrays.fill(password,         '\0');
        Arrays.fill(userCertPassword, '\0');
    }

    public boolean isSecurityValid() {
        if (securityPolicy == SecurityPolicy.None) {
            return securityMode == MessageSecurityMode.None;
        }
        return securityMode == MessageSecurityMode.Sign
                || securityMode == MessageSecurityMode.SignAndEncrypt;
    }

    public boolean isMissingUsername() {
        return authMode == AuthMode.USERNAME_PASSWORD && username.isBlank();
    }

    public boolean isMissingUserCert() {
        return authMode == AuthMode.USER_CERTIFICATE
                && (userCertPath == null || userCertPath.toString().isBlank());
    }

    @Override
    public String toString() {
        return endpointUrl
                + "  [" + securityPolicy.name() + " / " + securityMode + "]"
                + "  auth=" + authMode
                + switch (authMode) {
            case USERNAME_PASSWORD -> "  user=" + username;
            case USER_CERTIFICATE  -> "  certPath=" + userCertPath;
            default                -> "";
        };
    }

    private static char[] copy(char[] src) {
        return (src != null) ? Arrays.copyOf(src, src.length) : new char[0];
    }
}