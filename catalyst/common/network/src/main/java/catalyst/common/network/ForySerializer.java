package catalyst.common.network;

import org.apache.fory.Fory;
import org.apache.fory.ThreadSafeFory;
import org.apache.fory.config.Language;

/**
 * Centralized serialization utility using Apache Fory.
 * Ensures identical serialization config is shared across all modules.
 */
public final class ForySerializer {

    // Ensure your ThreadSafeFory instance is configured to NOT write class names
    // to the byte array if you want absolute minimum packet sizes, since our PacketType
    // enum handles the type routing now.
    private static final ThreadSafeFory fory = Fory.builder()
      .withLanguage(Language.JAVA)
      .requireClassRegistration(true) // Excellent for security and speed
      .buildThreadSafeFory();

    private ForySerializer() {
        // Utility class
    }

    public static byte[] serialize(Object obj) {
        return fory.serialize(obj);
    }

    public static Object deserialize(byte[] bytes) {
        return fory.deserialize(bytes);
    }
}