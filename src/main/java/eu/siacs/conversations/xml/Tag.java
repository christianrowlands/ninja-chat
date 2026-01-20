package eu.siacs.conversations.xml;

import java.util.Hashtable;

public class Tag {

    protected final Type type;
    protected final String name;
    protected final String namespace;
    protected Hashtable<String, String> attributes = new Hashtable<String, String>();

    protected Tag(final Type type, final String name, final String namespace) {
        this.type = type;
        this.name = name;
        this.namespace = namespace;
    }

    public static Tag no(String text) {
        return new Tag(Type.NO, text, null);
    }

    public static Tag start(final String name, final String namespace) {
        return new Tag(Type.START, name, namespace);
    }

    public static Tag end(String name) {
        return new Tag(Type.END, name, null);
    }

    public static Tag empty(final String name, final String namespace) {
        return new Tag(Type.EMPTY, name, namespace);
    }

    public String getName() {
        return this.name;
    }

    public String getNamespace() {
        return this.namespace;
    }

    public Type getType() {
        return this.type;
    }

    public String identifier() {
        return String.format("%s#%s", name, namespace);
    }

    public String getAttribute(final String attrName) {
        return this.attributes.get(attrName);
    }

    public Tag setAttribute(final String attrName, final String attrValue) {
        this.attributes.put(attrName, attrValue);
        return this;
    }

    public void setAttributes(final Hashtable<String, String> attributes) {
        this.attributes = attributes;
    }

    public boolean isStart(final String needle) {
        if (needle == null) {
            return false;
        }
        return (this.type == Type.START) && (needle.equals(this.name));
    }

    public boolean isStart(final String name, final String namespace) {
        return isStart(name) && namespace != null && namespace.equals(this.namespace);
    }

    public boolean isEnd(String needle) {
        if (needle == null) return false;
        return (this.type == Type.END) && (needle.equals(this.name));
    }

    public boolean isNo() {
        return (this.type == Type.NO);
    }

    public Hashtable<String, String> getAttributes() {
        return this.attributes;
    }

    public enum Type {
        NO,
        START,
        END,
        EMPTY
    }
}
