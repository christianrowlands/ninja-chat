package im.conversations.android.xmpp.model.ibb;

import com.google.common.primitives.Ints;
import im.conversations.android.annotation.XmlElement;

@XmlElement
public class Open extends InBandByteStream {

    public Open() {
        super(Open.class);
    }

    public void setBlockSize(int blockSize) {
        this.setAttribute("block-size", blockSize);
    }

    public int getBlockSize() {
        return Ints.saturatedCast(this.getLongAttribute("block-size"));
    }
}
