# Phase 2.5: The Concurrency & Dispatcher Architecture

## 1. The Core Problem

Our network edge uses Netty and QUIC to multiplex thousands of player connections over a small pool of OS-level EventLoop threads. If we execute standard, synchronous database queries (via `micronaut-data-jdbc`) directly inside a Netty `channelRead` method, the EventLoop physically freezes until PostgreSQL responds. This starves the server and prevents other players' packets from being processed.

## 2. The Solution: Virtual Threads + Message Dispatcher

Instead of migrating to complex Reactive Programming (R2DBC), we will utilize Java 25's **Virtual Threads**.

To implement this cleanly and prepare for MMO-scale state management (like World Zone thread-safety), we are implementing the **Message Dispatcher Pattern**. Netty will act as a "dumb pipe" that simply decodes bytes and drops the payload into a concurrent queue. A background Dispatcher will pull packets from the queue, spin up a lightweight Virtual Thread, and dynamically route the packet to the correct handler using a Micronaut-injected Strategy Pattern.

---

## 3. Implementation Guide

### Step 1: The Command Envelope

First, define a standard envelope to pass from Netty into the game loop. This holds the decoded packet and the Netty context (so the game logic can reply).

**`common/network/src/main/java/catalyst/common/network/dispatch/GameCommand.java`**

```java
package catalyst.common.network.dispatch;

import io.netty.channel.ChannelHandlerContext;

public record GameCommand<T>(
    ChannelHandlerContext ctx, 
    T payload
) {}
```

### Step 2: The Universal Handler Interface

Define the strict contract that all specific game features must implement.

**`server/server-common/src/main/java/catalyst/server/common/dispatch/PacketHandler.java`**

```java
package catalyst.server.common.dispatch;

import io.netty.channel.ChannelHandlerContext;

public interface PacketHandler<T> {
    
    /**
     * Tells the registry which DTO class this handler processes.
     */
    Class<T> getTargetPayloadClass(); 

    /**
     * The actual execution logic (runs safely on a Virtual Thread).
     */
    void handle(ChannelHandlerContext ctx, T payload);
}
```

### Step 3: The Auto-Wiring Message Dispatcher

This is the core engine. It manages the queue, the Virtual Threads, and automatically builds a routing map of every `PacketHandler` in the codebase.

**`server/server-common/src/main/java/catalyst/server/common/dispatch/MessageDispatcher.java`**

```java
package catalyst.server.common.dispatch;

import catalyst.common.network.dispatch.GameCommand;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Singleton
public class MessageDispatcher {
    private static final Logger log = LoggerFactory.getLogger(MessageDispatcher.class);

    private final BlockingQueue<GameCommand<?>> inboundQueue = new LinkedBlockingQueue<>();
    private final ExecutorService gameWorkers = Executors.newVirtualThreadPerTaskExecutor();
    
    // The Strategy Registry: Maps a DTO Class to its concrete Handler
    private final Map<Class<?>, PacketHandler<?>> handlerRegistry = new HashMap<>();

    // Micronaut automatically injects every @Singleton PacketHandler into this list
    public MessageDispatcher(List<PacketHandler<?>> availableHandlers) {
        for (PacketHandler<?> handler : availableHandlers) {
            handlerRegistry.put(handler.getTargetPayloadClass(), handler);
            log.info("Registered handler for: {}", handler.getTargetPayloadClass().getSimpleName());
        }
        startDispatchLoop();
    }

    /**
     * Called by Netty. Instantly drops the packet in the queue and returns.
     */
    public void dispatch(ChannelHandlerContext ctx, Object payload) {
        inboundQueue.offer(new GameCommand<>(ctx, payload));
    }

    private void startDispatchLoop() {
        Thread.ofVirtual().name("game-dispatcher").start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    GameCommand<?> command = inboundQueue.take();
                    // Offload game/DB logic to a throwaway Virtual Thread
                    gameWorkers.submit(() -> routeAndExecute(command));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    private void routeAndExecute(GameCommand<?> command) {
        Object payload = command.payload();
        
        // O(1) dynamic routing without if/else instanceOf chains
        PacketHandler<?> handler = handlerRegistry.get(payload.getClass());

        if (handler != null) {
            invokeHandler(handler, command.ctx(), payload);
        } else {
            log.warn("Dropped packet! No handler registered for: {}", payload.getClass().getName());
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void invokeHandler(PacketHandler<T> handler, ChannelHandlerContext ctx, Object payload) {
        try {
            handler.handle(ctx, (T) payload);
        } catch (Exception e) {
            log.error("Fatal error processing packet {}", payload.getClass().getSimpleName(), e);
        }
    }
}
```

### Step 4: The "Dumb" Netty Pipeline

Replace your existing complex Netty handlers with a single, ultra-thin adapter that just feeds the Dispatcher.

**`server/login-service/src/main/java/catalyst/server/login/transport/InboundPacketHandler.java`**

```java
package catalyst.server.login.transport;

import catalyst.server.common.dispatch.MessageDispatcher;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class InboundPacketHandler extends ChannelInboundHandlerAdapter {

    private final MessageDispatcher dispatcher;

    public InboundPacketHandler(MessageDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // Frees the Netty EventLoop instantly
        dispatcher.dispatch(ctx, msg); 
    }
}
```

### Step 5: Creating a Game Feature

To add new functionality (like logging in), simply implement the interface and mark it as a `@Singleton`. The Dispatcher will find it, and Netty will never block.

**`server/login-service/src/main/java/catalyst/server/login/handler/LoginRequestHandler.java`**

```java
package catalyst.server.login.handler;

import catalyst.common.dto.LoginRequest;
import catalyst.common.dto.LoginResponse;
import catalyst.server.common.dispatch.PacketHandler;
import catalyst.server.login.repository.AccountRepository;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Singleton;

@Singleton
public class LoginRequestHandler implements PacketHandler<LoginRequest> {

    private final AccountRepository accountRepository;

    public LoginRequestHandler(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public Class<LoginRequest> getTargetPayloadClass() {
        return LoginRequest.class;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, LoginRequest payload) {
        // 1. You are running on a lightweight Virtual Thread.
        // 2. Synchronous JDBC calls here are completely safe.
        var account = accountRepository.findByUsername(payload.username());
        
        // 3. Write the response. Netty automatically detects it is off the 
        //    EventLoop and safely schedules the write back to the socket.
        ctx.writeAndFlush(new LoginResponse(true));
    }
}
```
