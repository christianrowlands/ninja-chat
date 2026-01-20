package eu.siacs.conversations.xml;

import com.google.common.base.CaseFormat;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.xmpp.model.stanza.Message;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.regex.Pattern;

public class Element {

    private static final Pattern ELEMENT_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]+$");

    private final String name;
    private final String namespace;

    protected Hashtable<String, String> attributes = new Hashtable<>();
    private String content;
    protected List<Element> children = new ArrayList<>();

    public Element(final String name) {
        this.name = name;
        this.namespace = null;
    }

    public Element(final String name, final String namespace) {
        this.name = name;
        this.namespace = namespace;
    }

    public Element addChild(Element child) {
        this.content = null;
        children.add(child);
        return child;
    }

    public Element addChild(final String name) {
        return addChild(name, null);
    }

    public Element addChild(final String name, final String xmlns) {
        Preconditions.checkArgument(
                ELEMENT_NAME_PATTERN.matcher(name).matches(), "Invalid element name");
        this.content = null;
        Element child = new Element(name, xmlns);
        children.add(child);
        return child;
    }

    public Element setContent(final String content) {
        this.content = content;
        this.children.clear();
        return this;
    }

    public Element findChild(String name) {
        for (Element child : this.children) {
            if (child.getName().equals(name)) {
                return child;
            }
        }
        return null;
    }

    public String findChildContent(String name) {
        Element element = findChild(name);
        return element == null ? null : element.getContent();
    }

    public Element findChild(String name, String xmlns) {
        for (Element child : this.children) {
            if (name.equals(child.getName()) && xmlns.equals(child.getNamespace())) {
                return child;
            }
        }
        return null;
    }

    public Element findChildEnsureSingle(String name, String xmlns) {
        final List<Element> results = new ArrayList<>();
        for (Element child : this.children) {
            if (name.equals(child.getName()) && xmlns.equals(child.getNamespace())) {
                results.add(child);
            }
        }
        if (results.size() == 1) {
            return results.get(0);
        }
        return null;
    }

    public boolean hasChild(final String name) {
        return findChild(name) != null;
    }

    public boolean hasChild(final String name, final String xmlns) {
        return findChild(name, xmlns) != null;
    }

    public List<Element> getChildren() {
        return this.children;
    }

    public Element setChildren(List<Element> children) {
        this.children = children;
        return this;
    }

    public final String getContent() {
        return content;
    }

    public Element setAttribute(String name, String value) {
        if (name != null && value != null) {
            this.attributes.put(name, value);
        }
        return this;
    }

    public Element setAttribute(final String name, final Enum<?> e) {
        if (e == null) {
            this.attributes.remove(name);
        } else {
            this.attributes.put(
                    name, CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_HYPHEN, e.toString()));
        }
        return this;
    }

    public Element setAttribute(final String name, final Jid value) {
        if (name != null && value != null) {
            this.attributes.put(name, value.toString());
        }
        return this;
    }

    public void setAttribute(final String name, final boolean value) {
        this.setAttribute(name, value ? "1" : "0");
    }

    public void removeAttribute(final String name) {
        this.attributes.remove(name);
    }

    public Element setAttributes(Hashtable<String, String> attributes) {
        this.attributes = attributes;
        return this;
    }

    public String getAttribute(String name) {
        if (this.attributes.containsKey(name)) {
            return this.attributes.get(name);
        } else {
            return null;
        }
    }

    public long getLongAttribute(final String name) {
        final var value = Longs.tryParse(Strings.nullToEmpty(this.attributes.get(name)));
        return value == null ? 0 : value;
    }

    public Optional<Integer> getOptionalIntAttribute(final String name) {
        final String value = getAttribute(name);
        if (value == null) {
            return Optional.absent();
        }
        return Optional.fromNullable(Ints.tryParse(value));
    }

    public Jid getAttributeAsJid(final String name) {
        final String jid = this.getAttribute(name);
        if (Strings.isNullOrEmpty(jid)) {
            return null;
        }
        return Jid.ofOrInvalid(jid, this instanceof Message);
    }

    public Hashtable<String, String> getAttributes() {
        return this.attributes;
    }

    public final String getName() {
        return name;
    }

    public void clearChildren() {
        this.children.clear();
    }

    public void setAttribute(String name, long value) {
        this.setAttribute(name, Long.toString(value));
    }

    public void setAttribute(String name, int value) {
        this.setAttribute(name, Integer.toString(value));
    }

    public boolean getAttributeAsBoolean(String name) {
        String attr = getAttribute(name);
        return (attr != null && (attr.equalsIgnoreCase("true") || attr.equalsIgnoreCase("1")));
    }

    public String getNamespace() {
        return this.namespace;
    }

    protected Instant getAttributeAsInstant(final String name) {
        final var value = getAttribute(name);
        if (Strings.isNullOrEmpty(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (final DateTimeParseException e) {
            return null;
        }
    }
}
