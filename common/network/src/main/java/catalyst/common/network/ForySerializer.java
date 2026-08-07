package catalyst.common.network;

import org.apache.fory.Fory;
import org.apache.fory.ThreadSafeFory;
import org.apache.fory.config.Language;

/**
 * Centralized serialization utility using Apache Fory.
 * Ensures identical serialization config is shared across all modules.
 */
public final class ForySerializer {

    private static final ThreadSafeFory FORY = Fory.builder()
        .withLanguage(Language.JAVA)
        .requireClassRegistration(false)
        .buildThreadSafeFory();

    public static byte[] serialize(Object obj) {
        if (obj == null) {
            return new byte[0];
        }
        return FORY.serialize(obj);
    }

    public static Object deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        return FORY.deserialize(bytes);
    }

    private ForySerializer() {}
}
