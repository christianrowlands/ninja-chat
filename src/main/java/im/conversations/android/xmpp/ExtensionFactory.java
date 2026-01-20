package im.conversations.android.xmpp;

import android.util.Log;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.xml.Element;
import im.conversations.android.xmpp.model.Extension;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public final class ExtensionFactory {

    public static Element create(final String name, final String namespace) {
        final Class<? extends Extension> clazz = of(name, namespace);
        if (clazz == null) {
            Log.d(Config.LOGTAG, "missing extension for [" + name + "#" + namespace + "]");
            return new Element(name, namespace);
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

    private static Class<? extends Extension> of(final String name, final String namespace) {
        return Extensions.EXTENSION_CLASS_MAP.get(new Id(name, namespace));
    }

    public static Id id(final Class<? extends Extension> clazz) {
        return Extensions.EXTENSION_CLASS_MAP.inverse().get(clazz);
    }

    private ExtensionFactory() {}

    public record Id(String name, String namespace) {}
}
