package net.decentered.nody.opcua.client.model;

/**
 * OPC UA session identity – what kind of credentials the client presents
 * to the server when activating the session (OPC UA Part 4 §5.6.3).
 */
public enum AuthMode {
    ANONYMOUS,
    USERNAME_PASSWORD,
    USER_CERTIFICATE
}