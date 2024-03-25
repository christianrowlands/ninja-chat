package eu.siacs.conversations.xmpp.jingle.stanzas;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlMessage;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.jingle.SessionDescription;

import java.util.Locale;
import java.util.Set;

public class Content extends Element {

    public Content(final Creator creator, final Senders senders, final String name) {
        super("content", Namespace.JINGLE);
        this.setAttribute("creator", creator.toString());
        this.setAttribute("name", name);
        this.setSenders(senders);
    }

    private Content() {
        super("content", Namespace.JINGLE);
    }

    public static Content upgrade(final Element element) {
        Preconditions.checkArgument("content".equals(element.getName()));
        final Content content = new Content();
        content.setAttributes(element.getAttributes());
        content.setChildren(element.getChildren());
        return content;
    }

    public String getContentName() {
        return this.getAttribute("name");
    }

    public Creator getCreator() {
        return Creator.of(getAttribute("creator"));
    }

    public Senders getSenders() {
        final String attribute = getAttribute("senders");
        if (Strings.isNullOrEmpty(attribute)) {
            return Senders.BOTH;
        }
        return Senders.of(getAttribute("senders"));
    }

    public void setSenders(final Senders senders) {
        if (senders != null && senders != Senders.BOTH) {
            this.setAttribute("senders", senders.toString());
        }
    }

    public GenericDescription getDescription() {
        final Element description = this.findChild("description");
        if (description == null) {
            return null;
        }
        final String namespace = description.getNamespace();
        if (Namespace.JINGLE_APPS_FILE_TRANSFER.equals(namespace)) {
            return FileTransferDescription.upgrade(description);
        } else if (Namespace.JINGLE_APPS_RTP.equals(namespace)) {
            return RtpDescription.upgrade(description);
        } else {
            return GenericDescription.upgrade(description);
        }
    }

    public void setDescription(final GenericDescription description) {
        Preconditions.checkNotNull(description);
        this.addChild(description);
    }

    public String getDescriptionNamespace() {
        final Element description = this.findChild("description");
        return description == null ? null : description.getNamespace();
    }

    public GenericTransportInfo getTransport() {
        final Element transport = this.findChild("transport");
        final String namespace = transport == null ? null : transport.getNamespace();
        if (Namespace.JINGLE_TRANSPORTS_IBB.equals(namespace)) {
            return IbbTransportInfo.upgrade(transport);
        } else if (Namespace.JINGLE_TRANSPORTS_S5B.equals(namespace)) {
            return SocksByteStreamsTransportInfo.upgrade(transport);
        } else if (Namespace.JINGLE_TRANSPORT_ICE_UDP.equals(namespace)) {
            return IceUdpTransportInfo.upgrade(transport);
        } else if (Namespace.JINGLE_TRANSPORT_WEBRTC_DATA_CHANNEL.equals(namespace)) {
            return WebRTCDataChannelTransportInfo.upgrade(transport);
        } else if (transport != null) {
            return GenericTransportInfo.upgrade(transport);
        } else {
            return null;
        }
    }

    public void setSecurity(final XmppAxolotlMessage xmppAxolotlMessage) {
        final String contentName = this.getContentName();
        final Element security = new Element("security", Namespace.JINGLE_ENCRYPTED_TRANSPORT);
        security.setAttribute("name", contentName);
        security.setAttribute("cipher", "urn:xmpp:ciphers:aes-128-gcm-nopadding");
        security.setAttribute("type", AxolotlService.PEP_PREFIX);
        security.addChild(xmppAxolotlMessage.toElement());
        this.addChild(security);
    }

    public XmppAxolotlMessage getSecurity(final Jid from) {
        final String contentName = this.getContentName();
        for (final Element child : this.children) {
            if ("security".equals(child.getName())
                    && Namespace.JINGLE_ENCRYPTED_TRANSPORT.equals(child.getNamespace())) {
                final String name = child.getAttribute("name");
                final String type = child.getAttribute("type");
                final String cipher = child.getAttribute("cipher");
                if (contentName.equals(name)
                        && AxolotlService.PEP_PREFIX.equals(type)
                        && "urn:xmpp:ciphers:aes-128-gcm-nopadding".equals(cipher)) {
                    final var encrypted = child.findChild("encrypted", AxolotlService.PEP_PREFIX);
                    if (encrypted != null) {
                        return XmppAxolotlMessage.fromElement(encrypted, from.asBareJid());
                    }
                }
            }
        }
        return null;
    }

    public void setTransport(GenericTransportInfo transportInfo) {
        this.addChild(transportInfo);
    }

    public enum Creator {
        INITIATOR,
        RESPONDER;

        public static Creator of(final String value) {
            return Creator.valueOf(value.toUpperCase(Locale.ROOT));
        }

        @Override
        @NonNull
        public String toString() {
            return super.toString().toLowerCase(Locale.ROOT);
        }
    }

    public enum Senders {
        BOTH,
        INITIATOR,
        NONE,
        RESPONDER;

        public static Senders of(final String value) {
            return Senders.valueOf(value.toUpperCase(Locale.ROOT));
        }

        public static Senders of(final SessionDescription.Media media, final boolean initiator) {
            final Set<String> attributes = media.attributes.keySet();
            if (attributes.contains("sendrecv")) {
                return BOTH;
            } else if (attributes.contains("inactive")) {
                return NONE;
            } else if (attributes.contains("sendonly")) {
                return initiator ? INITIATOR : RESPONDER;
            } else if (attributes.contains("recvonly")) {
                return initiator ? RESPONDER : INITIATOR;
            }
            Log.w(Config.LOGTAG, "assuming default value for senders");
            // If none of the attributes "sendonly", "recvonly", "inactive", and "sendrecv" is
            // present, "sendrecv" SHOULD be assumed as the default
            // https://www.rfc-editor.org/rfc/rfc4566
            return BOTH;
        }

        public static Set<Senders> receiveOnly(final boolean initiator) {
            return ImmutableSet.of(initiator ? RESPONDER : INITIATOR);
        }

        @Override
        @NonNull
        public String toString() {
            return super.toString().toLowerCase(Locale.ROOT);
        }

        public String asMediaAttribute(final boolean initiator) {
            final boolean responder = !initiator;
            if (this == Content.Senders.BOTH) {
                return "sendrecv";
            } else if (this == Content.Senders.NONE) {
                return "inactive";
            } else if ((initiator && this == Content.Senders.INITIATOR)
                    || (responder && this == Content.Senders.RESPONDER)) {
                return "sendonly";
            } else if ((initiator && this == Content.Senders.RESPONDER)
                    || (responder && this == Content.Senders.INITIATOR)) {
                return "recvonly";
            } else {
                throw new IllegalStateException(
                        String.format(
                                "illegal combination of initiator=%s and %s", initiator, this));
            }
        }
    }
}
