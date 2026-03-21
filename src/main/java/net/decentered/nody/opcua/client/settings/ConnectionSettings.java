package net.decentered.nody.opcua.client.settings;

import net.decentered.nody.opcua.client.model.AuthMode;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Persists and restores connection panel settings across application restarts.
 *
 * <p>Stored at {@code ~/.nody-opcua-client/connection.properties}.
 * <b>Passwords are intentionally never persisted.</b></p>
 */
public class ConnectionSettings {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectionSettings.class);

    public static final Path SETTINGS_DIR =
            Paths.get(System.getProperty("user.home"), ".nody-opcua-client");

    private static final Path SETTINGS_FILE = SETTINGS_DIR.resolve("connection.properties");

    private static final String KEY_URL           = "connection.url";
    private static final String KEY_POLICY        = "connection.securityPolicy";
    private static final String KEY_MODE          = "connection.securityMode";
    private static final String KEY_AUTH          = "connection.authMode";
    private static final String KEY_USERNAME      = "connection.username";
    private static final String KEY_USER_CERT     = "connection.userCertPath";
    // Passwords intentionally omitted.

    public static final String              DEFAULT_URL      = "opc.tcp://localhost:4840";
    public static final SecurityPolicy      DEFAULT_POLICY   = SecurityPolicy.None;
    public static final MessageSecurityMode DEFAULT_MODE     = MessageSecurityMode.None;
    public static final AuthMode            DEFAULT_AUTH     = AuthMode.ANONYMOUS;
    public static final String              DEFAULT_USERNAME = "";
    public static final String              DEFAULT_CERT     = "";

    private String              url;
    private SecurityPolicy      securityPolicy;
    private MessageSecurityMode securityMode;
    private AuthMode            authMode;
    private String              username;
    private String              userCertPath;

    public ConnectionSettings() { load(); }

    public String              getUrl()            { return url; }
    public SecurityPolicy      getSecurityPolicy() { return securityPolicy; }
    public MessageSecurityMode getSecurityMode()   { return securityMode; }
    public AuthMode            getAuthMode()       { return authMode; }
    public String              getUsername()       { return username; }
    public String              getUserCertPath()   { return userCertPath; }

    public void save(String url,
                     SecurityPolicy policy,
                     MessageSecurityMode mode,
                     AuthMode authMode,
                     String username,
                     String userCertPath) {
        this.url          = url;
        this.securityPolicy = policy;
        this.securityMode = mode;
        this.authMode     = authMode;
        this.username     = (username != null) ? username : "";
        this.userCertPath = (userCertPath != null) ? userCertPath : "";
        persist();
    }

    private void load() {
        url          = DEFAULT_URL;
        securityPolicy = DEFAULT_POLICY;
        securityMode = DEFAULT_MODE;
        authMode     = DEFAULT_AUTH;
        username     = DEFAULT_USERNAME;
        userCertPath = DEFAULT_CERT;

        if (!Files.exists(SETTINGS_FILE)) {
            LOG.debug("No settings file at {} – using defaults", SETTINGS_FILE);
            return;
        }
        Properties props = new Properties();
        try (var in = Files.newInputStream(SETTINGS_FILE)) {
            props.load(in);
        } catch (IOException e) {
            LOG.warn("Could not read settings file: {}", e.getMessage());
            return;
        }
        url          = props.getProperty(KEY_URL,      DEFAULT_URL).trim();
        if (url.isEmpty()) url = DEFAULT_URL;
        securityPolicy = parsePolicy(props.getProperty(KEY_POLICY, DEFAULT_POLICY.name()));
        securityMode   = parseMode(props.getProperty(KEY_MODE,     DEFAULT_MODE.name()));
        authMode       = parseAuthMode(props.getProperty(KEY_AUTH,  DEFAULT_AUTH.name()));
        username       = props.getProperty(KEY_USERNAME, DEFAULT_USERNAME).trim();
        userCertPath   = props.getProperty(KEY_USER_CERT, DEFAULT_CERT).trim();

        LOG.info("Loaded settings: url={}, policy={}, mode={}, auth={}, user={}, certPath={}",
                url, securityPolicy.name(), securityMode, authMode, username, userCertPath);
    }

    private void persist() {
        try {
            Files.createDirectories(SETTINGS_DIR);
            Properties props = new Properties();
            props.setProperty(KEY_URL,       url);
            props.setProperty(KEY_POLICY,    securityPolicy.name());
            props.setProperty(KEY_MODE,      securityMode.name());
            props.setProperty(KEY_AUTH,      authMode.name());
            props.setProperty(KEY_USERNAME,  username);
            props.setProperty(KEY_USER_CERT, userCertPath);
            // Passwords intentionally omitted.
            try (var out = Files.newOutputStream(SETTINGS_FILE)) {
                props.store(out, "Nody OpcUa Client – connection settings (passwords not stored)");
            }
            LOG.debug("Settings saved to {}", SETTINGS_FILE);
        } catch (IOException e) {
            LOG.warn("Could not save settings: {}", e.getMessage());
        }
    }

    private static SecurityPolicy parsePolicy(String name) {
        try   { return SecurityPolicy.valueOf(name); }
        catch (IllegalArgumentException e) {
            LOG.warn("Unknown SecurityPolicy '{}', using default", name); return DEFAULT_POLICY; }
    }
    private static MessageSecurityMode parseMode(String name) {
        for (MessageSecurityMode m : MessageSecurityMode.values())
            if (m.name().equals(name)) return m;
        LOG.warn("Unknown MessageSecurityMode '{}', using default", name); return DEFAULT_MODE;
    }
    private static AuthMode parseAuthMode(String name) {
        try   { return AuthMode.valueOf(name); }
        catch (IllegalArgumentException e) {
            LOG.warn("Unknown AuthMode '{}', using default", name); return DEFAULT_AUTH; }
    }
}