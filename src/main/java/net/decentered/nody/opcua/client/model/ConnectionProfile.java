package net.decentered.nody.opcua.client.model;

import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;

import java.util.Objects;
import java.util.UUID;

/**
 * A named, persisted OPC UA connection profile.
 *
 * <p>Passwords are intentionally not stored here.  They are collected
 * interactively in the {@link net.decentered.nody.opcua.client.ui.QuickConnectBar}
 * immediately before connecting.</p>
 */
public final class ConnectionProfile {

    private final String id;          // stable UUID, never changes

    private String              name;
    private String              endpointUrl;
    private SecurityPolicy      securityPolicy;
    private MessageSecurityMode securityMode;
    private AuthMode            authMode;
    private String              username;
    private String              userCertPath;

    /** Creates a new profile with a fresh UUID. */
    public ConnectionProfile(String name, String endpointUrl) {
        this.id             = UUID.randomUUID().toString();
        this.name           = name;
        this.endpointUrl    = endpointUrl;
        this.securityPolicy = SecurityPolicy.None;
        this.securityMode   = MessageSecurityMode.None;
        this.authMode       = AuthMode.ANONYMOUS;
        this.username       = "";
        this.userCertPath   = "";
    }

    /** Full constructor used by the store when loading from disk. */
    public ConnectionProfile(String id, String name, String endpointUrl,
                             SecurityPolicy policy, MessageSecurityMode mode,
                             AuthMode authMode, String username, String userCertPath) {
        this.id             = id;
        this.name           = name;
        this.endpointUrl    = endpointUrl;
        this.securityPolicy = policy;
        this.securityMode   = mode;
        this.authMode       = authMode;
        this.username       = Objects.requireNonNullElse(username, "");
        this.userCertPath   = Objects.requireNonNullElse(userCertPath, "");
    }

    // ── Accessors / mutators ──────────────────────────────────────────────────

    public String              getId()             { return id; }
    public String              getName()           { return name; }
    public String              getEndpointUrl()    { return endpointUrl; }
    public SecurityPolicy      getSecurityPolicy() { return securityPolicy; }
    public MessageSecurityMode getSecurityMode()   { return securityMode; }
    public AuthMode            getAuthMode()       { return authMode; }
    public String              getUsername()       { return username; }
    public String              getUserCertPath()   { return userCertPath; }

    public void setName(String name)                      { this.name = name; }
    public void setEndpointUrl(String url)                { this.endpointUrl = url; }
    public void setSecurityPolicy(SecurityPolicy p)       { this.securityPolicy = p; }
    public void setSecurityMode(MessageSecurityMode m)    { this.securityMode = m; }
    public void setAuthMode(AuthMode m)                   { this.authMode = m; }
    public void setUsername(String u)                     { this.username = Objects.requireNonNullElse(u, ""); }
    public void setUserCertPath(String p)                 { this.userCertPath = Objects.requireNonNullElse(p, ""); }

    /** Creates a deep copy with a new UUID (used for Clone). */
    public ConnectionProfile duplicate() {
        return new ConnectionProfile(
                UUID.randomUUID().toString(),
                name + " (copy)",
                endpointUrl, securityPolicy, securityMode,
                authMode, username, userCertPath);
    }

    /** Display text shown in the combo box. */
    @Override
    public String toString() { return name; }

    @Override
    public boolean equals(Object o) {
        return o instanceof ConnectionProfile p && id.equals(p.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }
}