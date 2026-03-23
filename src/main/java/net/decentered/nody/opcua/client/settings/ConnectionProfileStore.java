package net.decentered.nody.opcua.client.settings;

import net.decentered.nody.opcua.client.model.AuthMode;
import net.decentered.nody.opcua.client.model.ConnectionProfile;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Persists and loads the list of {@link ConnectionProfile} objects.
 *
 * <p>Storage format: a plain {@link Properties} file at
 * {@code ~/.nody-opcua-client/connections.properties}.  Each profile is
 * stored as a group of keys prefixed with the profile index, e.g.:
 * <pre>
 *   profile.0.id       = &lt;uuid&gt;
 *   profile.0.name     = Prosys Simulation
 *   profile.0.url      = opc.tcp://localhost:53530/OPCUA/SimulationServer
 *   profile.0.policy   = Basic256Sha256
 *   profile.0.mode     = SignAndEncrypt
 *   profile.0.auth     = USERNAME_PASSWORD
 *   profile.0.username = john
 *   profile.0.certPath =
 *   ...
 *   profile.count = 1
 *   profile.selected = 0
 * </pre>
 * Passwords are never persisted.</p>
 */
public class ConnectionProfileStore {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectionProfileStore.class);

    private static final Path SETTINGS_DIR  =
            Paths.get(System.getProperty("user.home"), ".nody-opcua-client");
    private static final Path PROFILES_FILE = SETTINGS_DIR.resolve("connections.properties");

    private static final String KEY_COUNT    = "profile.count";
    private static final String KEY_SELECTED = "profile.selected";

    private final List<ConnectionProfile> profiles = new ArrayList<>();
    private int selectedIndex = 0;

    public ConnectionProfileStore() { load(); }

    // ── Public API ────────────────────────────────────────────────────────────

    public List<ConnectionProfile> getProfiles() {
        return Collections.unmodifiableList(profiles);
    }

    public ConnectionProfile getSelected() {
        if (profiles.isEmpty()) return null;
        return profiles.get(Math.min(selectedIndex, profiles.size() - 1));
    }

    public int getSelectedIndex() {
        return Math.min(selectedIndex, Math.max(0, profiles.size() - 1));
    }

    public void setSelectedIndex(int idx) {
        this.selectedIndex = Math.max(0, Math.min(idx, profiles.size() - 1));
        save();
    }

    public void addProfile(ConnectionProfile p) {
        profiles.add(p);
        selectedIndex = profiles.size() - 1;
        save();
    }

    public void updateProfile(ConnectionProfile updated) {
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).getId().equals(updated.getId())) {
                profiles.set(i, updated);
                save();
                return;
            }
        }
    }

    public void deleteProfile(String id) {
        profiles.removeIf(p -> p.getId().equals(id));
        selectedIndex = Math.min(selectedIndex, Math.max(0, profiles.size() - 1));
        save();
    }

    public void save() {
        try {
            Files.createDirectories(SETTINGS_DIR);
            Properties props = new Properties();
            props.setProperty(KEY_COUNT,    String.valueOf(profiles.size()));
            props.setProperty(KEY_SELECTED, String.valueOf(selectedIndex));
            for (int i = 0; i < profiles.size(); i++) {
                ConnectionProfile p = profiles.get(i);
                String pfx = "profile." + i + ".";
                props.setProperty(pfx + "id",       p.getId());
                props.setProperty(pfx + "name",     p.getName());
                props.setProperty(pfx + "url",      p.getEndpointUrl());
                props.setProperty(pfx + "policy",   p.getSecurityPolicy().name());
                props.setProperty(pfx + "mode",     p.getSecurityMode().name());
                props.setProperty(pfx + "auth",     p.getAuthMode().name());
                props.setProperty(pfx + "username", p.getUsername());
                props.setProperty(pfx + "certPath", p.getUserCertPath());
            }
            try (var out = Files.newOutputStream(PROFILES_FILE)) {
                props.store(out, "Nody OpcUa Client – connection profiles (passwords not stored)");
            }
            LOG.debug("Saved {} profiles to {}", profiles.size(), PROFILES_FILE);
        } catch (IOException e) {
            LOG.warn("Could not save profiles: {}", e.getMessage());
        }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void load() {
        if (!Files.exists(PROFILES_FILE)) {
            LOG.debug("No profiles file found – creating default profile");
            profiles.add(defaultProfile());
            return;
        }
        Properties props = new Properties();
        try (var in = Files.newInputStream(PROFILES_FILE)) {
            props.load(in);
        } catch (IOException e) {
            LOG.warn("Could not read profiles file: {}", e.getMessage());
            profiles.add(defaultProfile());
            return;
        }
        int count = parseInt(props.getProperty(KEY_COUNT, "0"));
        selectedIndex = parseInt(props.getProperty(KEY_SELECTED, "0"));
        for (int i = 0; i < count; i++) {
            String pfx = "profile." + i + ".";
            String id       = props.getProperty(pfx + "id",       UUID.randomUUID().toString());
            String name     = props.getProperty(pfx + "name",     "Connection " + (i + 1));
            String url      = props.getProperty(pfx + "url",      "opc.tcp://localhost:4840");
            String policy   = props.getProperty(pfx + "policy",   SecurityPolicy.None.name());
            String mode     = props.getProperty(pfx + "mode",     MessageSecurityMode.None.name());
            String auth     = props.getProperty(pfx + "auth",     AuthMode.ANONYMOUS.name());
            String username = props.getProperty(pfx + "username", "");
            String certPath = props.getProperty(pfx + "certPath", "");
            profiles.add(new ConnectionProfile(id, name, url,
                    parsePolicy(policy), parseMode(mode), parseAuth(auth),
                    username, certPath));
        }
        if (profiles.isEmpty()) profiles.add(defaultProfile());
        selectedIndex = Math.min(selectedIndex, profiles.size() - 1);
        LOG.info("Loaded {} profiles, selected={}", profiles.size(), selectedIndex);
    }

    private static ConnectionProfile defaultProfile() {
        return new ConnectionProfile("localhost:4840", "opc.tcp://localhost:4840");
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    private static SecurityPolicy parsePolicy(String s) {
        try { return SecurityPolicy.valueOf(s); }
        catch (Exception e) { return SecurityPolicy.None; }
    }
    private static MessageSecurityMode parseMode(String s) {
        for (var m : MessageSecurityMode.values()) if (m.name().equals(s)) return m;
        return MessageSecurityMode.None;
    }
    private static AuthMode parseAuth(String s) {
        try { return AuthMode.valueOf(s); }
        catch (Exception e) { return AuthMode.ANONYMOUS; }
    }
}