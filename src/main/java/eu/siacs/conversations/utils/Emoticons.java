/*
 * Copyright (c) 2017, Daniel Gultsch All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package eu.siacs.conversations.utils;

import com.google.common.base.Joiner;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.util.Set;
import java.util.regex.Pattern;
import net.fellbaum.jemoji.EmojiManager;

public class Emoticons {

    private static final int MAX_EMOJIS = 42;

    private static final int VARIATION_16 = 0xFE0F;
    private static final int VARIATION_15 = 0xFE0E;
    private static final String VARIATION_16_STRING = new String(new char[] {VARIATION_16});
    private static final String VARIATION_15_STRING = new String(new char[] {VARIATION_15});

    private static final Set<String> TEXT_DEFAULT_TO_VS16 =
            ImmutableSet.of(
                    "❤",
                    "✔",
                    "✖",
                    "➕",
                    "➖",
                    "➗",
                    "⭐",
                    "⚡",
                    "\uD83C\uDF96",
                    "\uD83C\uDFC6",
                    "\uD83E\uDD47",
                    "\uD83E\uDD48",
                    "\uD83E\uDD49",
                    "\uD83D\uDC51",
                    "⚓",
                    "⛵",
                    "✈",
                    "⚖",
                    "⛑",
                    "⚒",
                    "⛏",
                    "☎",
                    "⛄",
                    "⛅",
                    "⚠",
                    "⚛",
                    "✡",
                    "☮",
                    "☯",
                    "☀",
                    "⬅",
                    "➡",
                    "⬆",
                    "⬇");

    private static final LoadingCache<CharSequence, Pattern> CACHE =
            CacheBuilder.newBuilder()
                    .maximumSize(256)
                    .build(
                            new CacheLoader<>() {
                                @Override
                                public Pattern load(final CharSequence key) {
                                    return generatePattern(key);
                                }
                            });

    public static String normalizeToVS16(final String input) {
        return TEXT_DEFAULT_TO_VS16.contains(input) && !input.endsWith(VARIATION_15_STRING)
                ? input + VARIATION_16_STRING
                : input;
    }

    public static String existingVariant(final String original, final Set<String> existing) {
        if (existing.contains(original) || original.endsWith(VARIATION_15_STRING)) {
            return original;
        }
        final var variant =
                original.endsWith(VARIATION_16_STRING)
                        ? original.substring(0, original.length() - 1)
                        : original + VARIATION_16_STRING;
        return existing.contains(variant) ? variant : original;
    }

    public static Pattern getEmojiPattern(final CharSequence input) {
        return CACHE.getUnchecked(input);
    }

    private static Pattern generatePattern(final CharSequence input) {
        final var emojis = EmojiManager.extractEmojis(CharSequences.nullToEmpty(input));
        return Pattern.compile(
                Joiner.on('|')
                        .join(
                                Iterables.transform(
                                        Iterables.limit(emojis, MAX_EMOJIS),
                                        e -> Pattern.quote(e.getEmoji()))));
    }

    public static boolean isEmoji(final String input) {
        return EmojiManager.isEmoji(input);
    }

    public static boolean isOnlyEmoji(final String input) {
        return EmojiManager.removeAllEmojis(input).isEmpty();
    }
}
