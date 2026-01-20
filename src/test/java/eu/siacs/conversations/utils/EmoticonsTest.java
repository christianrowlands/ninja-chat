package eu.siacs.conversations.utils;

import org.junit.Assert;
import org.junit.Test;

public class EmoticonsTest {

    @Test
    public void testUpAndDown() {
        Assert.assertTrue(Emoticons.isEmoji("↕\uFE0F"));
    }

    @Test
    public void testHeadShakingVertically() {
        Assert.assertTrue(Emoticons.isEmoji("\uD83D\uDE42\u200D↕\uFE0F"));
    }

    @Test
    public void rightArrowCurvingLeft() {
        Assert.assertTrue(Emoticons.isEmoji("↩\uFE0F"));
    }

    @Test
    public void blackLargeSquare() {
        Assert.assertTrue(Emoticons.isEmoji("\u2b1b"));
    }

    @Test
    public void cat() {
        Assert.assertTrue(Emoticons.isEmoji("\uD83D\uDC08"));
    }

    @Test
    public void blackCat() {
        Assert.assertTrue(Emoticons.isEmoji("\uD83D\uDC08\u200d\u2b1b"));
    }

    @Test
    public void a() {
        Assert.assertFalse(Emoticons.isEmoji("a"));
    }

    @Test
    public void thumbsUpThumbsDown() {
        Assert.assertFalse(Emoticons.isEmoji("\uD83D\uDC4D\uD83D\uDC4E"));
    }

    @Test
    public void thumbsUpThumbsDownIsOnly() {
        Assert.assertTrue(Emoticons.isOnlyEmoji("\uD83D\uDC4D\uD83D\uDC4E"));
    }

    @Test
    public void thumbsUpAndA() {
        Assert.assertFalse(Emoticons.isOnlyEmoji("\uD83D\uDC4Da"));
    }
}
