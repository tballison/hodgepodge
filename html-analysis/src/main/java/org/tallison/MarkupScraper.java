package org.tallison;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Hackish (er, highly tailored) class to try to strip off html-ish markup from an InputStream.
 * This ignores contents in &lt;script&gt; and &lt;style&gt; tags.
 * It tries to leave in &lt;meta&gt; entities that include charset=
 */
class MarkupScraper {

    private static final int ENTITY_OPEN = (int) '<';
    private static final int ENTITY_CLOSE = (int) '>';
    private static final int FORWARD_SLASH = (int) '/';
    private static final int EXCLAM = (int) '!';
    private static final int DASH = (int) '-';
    private static final int EOS = -1; //end of stream
    private static final int N = (int) '\n';
    private static final int R = (int) '\r';
    private static final int S = (int) ' ';
    private static final int T = (int) '\t';

    private static final short HIT_ENTITY_NAME_END = 1;
    private static final short HIT_ENTITY_END = 2;

    private static final Pattern CHARSET_PATTERN = Pattern.compile("(?si)charset\\s*=");

    private enum TAG_TYPE {
        START,
        END,
        START_AND_END
    }

    private static final String STYLE = "style";
    private static final String SCRIPT = "script";
    private static final String META = "meta";

    private ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private ByteArrayOutputStream entityBuffer = new ByteArrayOutputStream();
    private int inStyle = 0;
    private int inScript = 0;

    private int maxToRead = 0;
    private int bytesRead = 0;


    /**
     * @param is must support mark!
     * @return
     * @throws IOException
     */
    public byte[] scrape(InputStream is) throws IOException {
        return scrape(is, -1);
    }
        /**
         * @param is must support mark!
         * @return
         * @throws IOException
         */
    public byte[] scrape(InputStream is, int maxToRead) throws IOException {
        this.maxToRead = maxToRead;
        PushbackInputStream pushbackInputStream = new PushbackInputStream(is, 3);
        try {
            _scrape(pushbackInputStream);
        } catch (HitMax e) {
            //swallow this one
        } finally {
        }
        return buffer.toByteArray();
    }

    private void _scrape(PushbackInputStream pb) throws IOException {
        int c = read(pb);

        while (c != EOS) {
            switch (c) {
                case ENTITY_OPEN:
                    if (isCommentStart(pb)) {
                        readUntilEndComment(pb);
                    } else {
                        handleTag(pb);
                    }
                    break;
                default:
                    if (inStyle == 0 && inScript == 0) {
                        buffer.write(c);
                    }
            }
            c = read(pb);
        }
    }

    private void readUntilEndComment(PushbackInputStream pb) throws IOException {
        int c1 = read(pb);
        while (c1 != EOS) {
            if (c1 == DASH) {
                int c2 = read(pb);
                if (c2 == DASH) {
                    int c3 = read(pb);
                    if (c3 == ENTITY_CLOSE) {
                        return;
                    } else {
                        tryToUnread(pb, c3, c2);
                    }
                } else {
                    tryToUnread(pb, c2);
                }
            }
            //for now don't buffer contents of comments
            //if you want to buffer comments, do that here

            c1 = read(pb);
        }

    }

    //< has been bytesRead and we have ruled out <!--
    private void handleTag(PushbackInputStream pb) throws IOException {
        TAG_TYPE tagType = TAG_TYPE.START;
        entityBuffer.reset();
        ignoreWhiteSpace(pb);
        int c = read(pb);
        if (c == FORWARD_SLASH) {
            //this is an end entityName
            ignoreWhiteSpace(pb);
            c = read(pb);
            tagType = TAG_TYPE.END;

        }

        //bytesRead the entity name first
        short hitEnd = 0;
        while (c != EOS && hitEnd == 0) {
            switch (c) {
                case T:
                case R:
                case N:
                case S:
                    hitEnd = HIT_ENTITY_NAME_END;
                    continue;
                case ENTITY_CLOSE:
                    hitEnd = HIT_ENTITY_END;
                    continue;
                case FORWARD_SLASH:
                    //ignore whitespace may incorrectly
                    //gobble whitespace: arg="something/ or other" -> arg="something/or other"
                    //but this shouldn't cause a problem for the current use case
                    ignoreWhiteSpace(pb);
                    int next = read(pb);
                    if (next == ENTITY_CLOSE) {
                        hitEnd = HIT_ENTITY_END;
                        if (!tagType.equals(TAG_TYPE.END)) {
                            tagType = TAG_TYPE.START_AND_END;
                        }
                        continue;
                    } else {
                        tryToUnread(pb, next);
                    }
                default:
                    entityBuffer.write(c);
            }
            c = read(pb);
        }

        String entityName = new String(entityBuffer.toByteArray(), StandardCharsets.ISO_8859_1);
        entityName = entityName.trim().toLowerCase(Locale.US);

        //if you're in a script check that this entity name = "script"
        //if not, then it probably isn't an entity: if (1 < 4)
        if (inScript > 0) {
            if (! SCRIPT.equalsIgnoreCase(entityName)) {
                return;
            }
        }
        if (hitEnd != HIT_ENTITY_END) {

            entityBuffer.write(S);
            ignoreWhiteSpace(pb);
            c = read(pb);
            while (c != EOS && hitEnd != HIT_ENTITY_END) {
                switch (c) {
                    case ENTITY_CLOSE:
                        hitEnd = HIT_ENTITY_END;
                        continue;
                    case FORWARD_SLASH:
                        //ignore whitespace may incorrectly
                        //gobble whitespace: arg="something/ or other" -> arg="something/or other"
                        //but this shouldn't cause a problem
                        ignoreWhiteSpace(pb);
                        int next = read(pb);
                        if (next == ENTITY_CLOSE) {
                            hitEnd = HIT_ENTITY_END;
                            if (!tagType.equals(TAG_TYPE.END)) {
                                tagType = TAG_TYPE.START_AND_END;
                            }
                            continue;
                        } else {
                            tryToUnread(pb, next);
                        }
                    default:
                        entityBuffer.write(c);
                }
                c = read(pb);
            }
        }


        if (tagType.equals(TAG_TYPE.START)) {
            if (STYLE.equals(entityName)) {
                inStyle++;
            } else if (SCRIPT.equals(entityName)) {
                inScript++;
            }
        } else if (tagType.equals(TAG_TYPE.END)) {
            if (STYLE.equals(entityName)) {
                inStyle--;
            } else if (SCRIPT.equals(entityName)) {
                inScript--;
            }
        }
        if (META.equals(entityName)) {
            String fullEntity = new String(entityBuffer.toByteArray(), StandardCharsets.ISO_8859_1);
            if (CHARSET_PATTERN.matcher(fullEntity).find()) {
                buffer.write(ENTITY_OPEN);
                buffer.write(entityBuffer.toByteArray());
                if (tagType.equals(TAG_TYPE.START_AND_END)) {
                    buffer.write(FORWARD_SLASH);
                }
                buffer.write(ENTITY_CLOSE);
            }
        }
    }

    private void ignoreWhiteSpace(PushbackInputStream pb) throws IOException {
        int c = read(pb);
        while (c != EOS) {
            switch (c) {
                case T: //fall through
                case N:
                case R:
                case S:
                    break;
                default:
                    tryToUnread(pb, c);
                    return;
            }
            c = read(pb);
        }
    }


    // < has already been bytesRead, see if !-- immediately follows
    private boolean isCommentStart(PushbackInputStream pb) throws IOException {
        ignoreWhiteSpace(pb);
        int n1 = read(pb);
        if (n1 == EXCLAM) {
            int n2 = read(pb);
            if (n2 == DASH) {
                int n3 = read(pb);
                if (n3 == DASH) {
                    return true;
                } else {
                    tryToUnread(pb, n3, n2, n1);
                }
            } else {
                tryToUnread(pb, n2, n1);
            }
        } else {
            tryToUnread(pb, n1);
        }
        return false;
    }

    private void tryToUnread(PushbackInputStream pb, int... chars) throws IOException {
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == EOS) {
                return;
            } else {
                pb.unread(chars[i]);
                bytesRead--;
            }
        }
    }

    private int read(PushbackInputStream pb) throws IOException {
        if (maxToRead > 0 && bytesRead >= maxToRead) {
            throw new HitMax();
        }
        int c = pb.read();
        bytesRead++;
        return c;
    }

    private static class HitMax extends IOException {

    }
}
