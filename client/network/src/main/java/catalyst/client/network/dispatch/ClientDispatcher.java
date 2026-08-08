package catalyst.client.network.dispatch;

import lombok.extern.slf4j.Slf4j;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe message dispatcher for the client connection.
 * Receives incoming server messages asynchronously from Netty EventLoop threads
 * and queues them to be consumed sequentially on the main GLFW render thread,
 * preventing OpenGL concurrency crashes.
 */
@Slf4j
public class ClientDispatcher {

    private final Queue<Object> packetInbox = new ConcurrentLinkedQueue<>();

    /**
     * Enqueues an incoming server packet from the Netty thread.
     * Non-blocking and thread-safe.
     */
    public void enqueue(Object payload) {
        if (payload != null) {
            packetInbox.offer(payload);
        }
    }

    /**
     * Polls the next packet from the queue.
     * Call this from the GLFW render thread during each frame update.
     */
    public Object pollNextPacket() {
        return packetInbox.poll();
    }
}
