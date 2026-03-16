package net.decentered.nody.opcua.client.model;

/**
 * Simple value object that holds a single OPC UA node attribute.
 */
public record NodeAttribute(String name, String value) {

    @Override
    public String toString() {
        return name + " = " + value;
    }
}