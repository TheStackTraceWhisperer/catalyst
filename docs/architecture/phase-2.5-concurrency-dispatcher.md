# Catalyst MMO: Database Concurrency & Dispatcher Blueprint

## Part 1: The Database Concurrency Fix

Our network edge uses Netty and QUIC to multiplex thousands of player connections over a small pool of OS-level EventLoop threads.

**The Problem:** Our backend uses `micronaut-data-jdbc`. JDBC is inherently synchronous and blocking. If a `LoginRequest` executes `accountRepository.findByUsername(...)` directly inside a Netty `channelRead` method, the entire OS-level EventLoop physically freezes until PostgreSQL responds. This starves the server, dropping packets for all other players.

**The Solution:** We implement the **Offload Pattern** using Java 25 **Virtual Threads**.
Instead of migrating to a complex Reactive stack (R2DBC), Netty will act strictly as a "dumb pipe." It will instantly toss the decoded packet into a queue and return to listening. A background **Dispatcher** will pick up the packet and execute the blocking JDBC database call on a throwaway Virtual Thread.

### 1.1 The Database Handler Implementation (The Fix in Action)

Here is exactly how the database concurrency issue is resolved at the handler level. Notice how standard, blocking Java code is used safely.

**File:** `server/login-service/src/main/java/catalyst/server/login/handler/LoginRequestHandler.java`

```java
package catalyst.server.login.handler;

import catalyst.common.dto.LoginRequest;
import catalyst.common.dto.LoginResponse;
import catalyst.server.common.dispatch.PacketHandler;
import catalyst.server.login.repository.AccountRepository;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class LoginRequestHandler implements PacketHandler<LoginRequest> {

    // Standard synchronous Micronaut Data JDBC Repository
    private final AccountRepository accountRepository;

    @Override
    public Class<LoginRequest> getTargetPayloadClass() {
        return LoginRequest.class;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, LoginRequest payload) {
        // --- DATABASE CONCURRENCY FIX ---
        // 1. We are currently executing on a lightweight Java 25 Virtual Thread.
        // 2. The Netty EventLoop has already moved on to process other players.
        // 3. We can safely BLOCK this Virtual Thread while waiting for PostgreSQL.
        var account = accountRepository.findByUsername(payload.username());
        
        boolean success = (account != null && account.getPasswordHash().equals(payload.passwordHash()));
        
        // 4. We write the response back to Netty. 
        // Netty's pipeline is thread-safe; it automatically detects we are off 
        // the EventLoop and schedules the outbound network write safely.
        ctx.writeAndFlush(new LoginResponse(success));
    }
}

```

---

## Part 2: Shared Server Infrastructure (`server-common`)

To facilitate the database fix above, we need a routing engine. This module contains the foundational dispatching contracts shared by all backend microservices. It does *not* leak into the `client` or `common/network` modules.

### 2.1 The Game Command Envelope

Wraps the inbound payload with its Netty socket context so handlers can write responses back to the specific client.

**File:** `server/server-common/src/main/java/catalyst/server/common/dispatch/GameCommand.java`

```java
package catalyst.server.common.dispatch;

import io.netty.channel.ChannelHandlerContext;

public record GameCommand<T>(
    ChannelHandlerContext ctx, 
    T payload
) {}

```

### 2.2 The Packet Handler Interface

The strategy interface implemented by all specific game features.

**File:** `server/server-common/src/main/java/catalyst/server/common/dispatch/PacketHandler.java`

```java
package catalyst.server.common.dispatch;

import io.netty.channel.ChannelHandlerContext;

public interface PacketHandler<T> {
    Class<T> getTargetPayloadClass(); 
    void handle(ChannelHandlerContext ctx, T payload);
}

```

---

## Part 3: Stateless Servers (`login-service` & `lobby-service`)

These services execute pure database queries and do not share spatial state between players. They use a highly concurrent Virtual Thread dispatcher to maximize database lookup speed.

### 3.1 The Stateless Dispatcher

Placed in `server-common` so both `login-service` and `lobby-service` can inject it. It utilizes Micronaut's `BeanProvider` for lazy initialization to prevent circular dependencies at startup.

**File:** `server/server-common/src/main/java/catalyst/server/common/dispatch/StatelessMessageDispatcher.java`

```java
package catalyst.server.common.dispatch;

import catalyst.server.common.dispatch.GameCommand;
import io.micronaut.context.BeanProvider;
import io.netty.channel.ChannelHandlerContext;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class StatelessMessageDispatcher {

    // Lazy loads all @Singleton PacketHandlers across the classpath
    private final BeanProvider<PacketHandler<?>> availableHandlers;

    private final BlockingQueue<GameCommand<?>> inboundQueue = new LinkedBlockingQueue<>();
    
    // The core of the concurrency fix: Spawns a VT for every database hit
    private final ExecutorService gameWorkers = Executors.newVirtualThreadPerTaskExecutor();
    
    private final Map<Class<?>, PacketHandler<?>> handlerRegistry = new HashMap<>();

    @PostConstruct
    public void initialize() {
        for (PacketHandler<?> handler : availableHandlers) {
            handlerRegistry.put(handler.getTargetPayloadClass(), handler);
            log.info("Registered Stateless Handler for: {}", handler.getTargetPayloadClass().getSimpleName());
        }
        startDispatchLoop();
    }

    public void dispatch(ChannelHandlerContext ctx, Object payload) {
        inboundQueue.offer(new GameCommand<>(ctx, payload));
    }

    private void startDispatchLoop() {
        Thread.ofVirtual().name("stateless-dispatcher").start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    GameCommand<?> command = inboundQueue.take();
                    // Instantly offload to a concurrent Virtual Thread for DB processing
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

### 3.2 The Netty Adapter (Example: Login Service)

Netty handles the network protocol, then instantly unbinds via the dispatcher to protect its EventLoop.

**File:** `server/login-service/src/main/java/catalyst/server/login/transport/InboundPacketHandler.java`

```java
package catalyst.server.login.transport;

import catalyst.server.common.dispatch.StatelessMessageDispatcher;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class InboundPacketHandler extends ChannelInboundHandlerAdapter {

    private final StatelessMessageDispatcher dispatcher;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // Frees the Netty EventLoop instantly
        dispatcher.dispatch(ctx, msg); 
    }
}

```

---

## Part 4: Stateful Server (`world-service`)

The World Service handles combat, movement, and inventory.
**The Database Concurrency Caveat:** If we used the concurrent Virtual Threads from Part 3 here, two players looting the same chest at the exact same millisecond would cause a database race condition, duplicating the item. Packets must be grouped by spatial zone and executed sequentially via a 10Hz Tick.

### 4.1 The Zone Tick Dispatcher

This dispatcher lives exclusively in the `world-service`.

**File:** `server/world-service/src/main/java/catalyst/server/world/dispatch/ZoneMessageDispatcher.java`

```java
package catalyst.server.world.dispatch;

import catalyst.server.common.dispatch.GameCommand;
import catalyst.server.common.dispatch.PacketHandler;
import io.micronaut.context.BeanProvider;
import io.netty.channel.ChannelHandlerContext;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ZoneMessageDispatcher {

    private final BeanProvider<PacketHandler<?>> availableHandlers;
    private final Map<Class<?>, PacketHandler<?>> handlerRegistry = new HashMap<>();
    
    // Non-blocking concurrent queue. We do NOT use BlockingQueue.take() here.
    private final Queue<GameCommand<?>> zoneQueue = new ConcurrentLinkedQueue<>();
    
    private static final int TICK_RATE_MS = 100; // 10Hz Server Loop

    @PostConstruct
    public void initialize() {
        for (PacketHandler<?> handler : availableHandlers) {
            handlerRegistry.put(handler.getTargetPayloadClass(), handler);
            log.info("Registered Zone Handler for: {}", handler.getTargetPayloadClass().getSimpleName());
        }
        startZoneTickLoop();
    }

    public void dispatch(ChannelHandlerContext ctx, Object payload) {
        zoneQueue.offer(new GameCommand<>(ctx, payload));
    }

    private void startZoneTickLoop() {
        Thread.ofVirtual().name("zone-tick-loop").start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                long tickStart = System.currentTimeMillis();

                // 1. Process all pending network events SEQUENTIALLY to prevent DB race conditions
                processNetworkQueue();

                // 2. Process Game State (AI, Spawns, Regen)
                // updateGameState();

                // 3. Sleep until next tick interval
                long elapsed = System.currentTimeMillis() - tickStart;
                long sleepTime = TICK_RATE_MS - elapsed;
                
                if (sleepTime > 0) {
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    log.warn("Server Tick lag detected! Tick took {}ms", elapsed);
                }
            }
        });
    }

    private void processNetworkQueue() {
        GameCommand<?> command;
        while ((command = zoneQueue.poll()) != null) {
            Object payload = command.payload();
            PacketHandler<?> handler = handlerRegistry.get(payload.getClass());
            
            if (handler != null) {
                invokeHandler(handler, command.ctx(), payload);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void invokeHandler(PacketHandler<T> handler, ChannelHandlerContext ctx, Object payload) {
        try {
            handler.handle(ctx, (T) payload);
        } catch (Exception e) {
            log.error("Error processing packet", e);
        }
    }
}

```

---

## Part 5: The Game Client (`client/engine`)

The ImGui/GLFW client relies heavily on OpenGL state. If the client used Virtual Threads to process packets, it would cause OpenGL concurrency crashes when trying to update UI elements. The Client Dispatcher acts purely as an inbox that the Render Thread checks every frame.

### 5.1 The Client Dispatcher

This lives entirely in the client engine and has no concept of `GameCommand` or `ChannelHandlerContext`, as the client already maintains a global singleton connection to the server.

**File:** `client/engine/src/main/java/catalyst/client/engine/dispatch/ClientDispatcher.java`

```java
package catalyst.client.engine.dispatch;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@Singleton
public class ClientDispatcher {

    // Thread-safe inbox for incoming packets
    private final Queue<Object> packetInbox = new ConcurrentLinkedQueue<>();

    /**
     * Called by the Client's Netty EventLoop. Instantly returns.
     */
    public void enqueue(Object payload) {
        packetInbox.offer(payload);
    }

    /**
     * Called by the GLFW/ImGui Render Loop at 60 FPS.
     * Returns the next packet, or null if the inbox is empty.
     */
    public Object pollNextPacket() {
        return packetInbox.poll();
    }
}

```

### 5.2 Integration with the Engine Loop

In your main game engine update cycle, process the inbox before rendering.

**File:** `client/engine/src/main/java/catalyst/client/engine/Engine.java` *(Conceptual Implementation)*

```java
package catalyst.client.engine;

import catalyst.client.engine.dispatch.ClientDispatcher;
import jakarta.inject.Inject;

public class Engine {

    @Inject
    private ClientDispatcher dispatcher;

    public void run() {
        while (!glfwWindowShouldClose(window)) {
            
            // 1. Process Network Inbox (Runs safely on the single Render Thread!)
            Object packet;
            while ((packet = dispatcher.pollNextPacket()) != null) {
                // Route packet to UI or Scene Managers
                applicationStateService.handlePacket(packet);
            }

            // 2. Render UI & 3D Scene
            imGuiService.render();
            
            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }
}

```