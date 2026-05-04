package im.conversations.android.xmpp;

import android.util.Log;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.xml.Element;
import im.conversations.android.xmpp.model.Extension;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public final class ExtensionFactory {

    public static Element create(final Id id) {
        final Class<? extends Extension> clazz = of(id);
        if (clazz == null) {
            Log.d(Config.LOGTAG, "missing extension for [" + id.name + "#" + id.namespace + "]");
            return new Element(id.name, id.namespace);
        }
        final Constructor<? extends Element> constructor;
        try {
            constructor = clazz.getDeclaredConstructor();
        } catch (final NoSuchMethodException e) {
            throw new IllegalStateException(
                    String.format("%s has no default constructor", clazz.getName()), e);
        }
        try {
            return constructor.newInstance();
        } catch (final IllegalAccessException
                | InstantiationException
                | InvocationTargetException e) {
            throw new IllegalStateException(
                    String.format("%s has inaccessible default constructor", clazz.getName()), e);
        }
    }

    private static Class<? extends Extension> of(final Id id) {
        return Extensions.EXTENSION_CLASS_MAP.get(id);
    }

    public static Id id(final Class<? extends Extension> clazz) {
        return Extensions.EXTENSION_CLASS_MAP.inverse().get(clazz);
    }

    private ExtensionFactory() {}

    public record Id(String name, String namespace) {}
}
