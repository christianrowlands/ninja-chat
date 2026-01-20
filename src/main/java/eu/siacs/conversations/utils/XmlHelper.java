package eu.siacs.conversations.utils;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import eu.siacs.conversations.xml.Element;
import java.util.Collections;
import java.util.List;

public class XmlHelper {

    public static String printElementNames(final Element element) {
        final List<String> features =
                element == null
                        ? Collections.emptyList()
                        : Lists.transform(
                                element.getChildren(),
                                child -> child != null ? child.getName() : null);
        return Joiner.on(", ").join(features);
    }
}
