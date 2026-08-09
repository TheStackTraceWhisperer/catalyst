# Task: World Tick Rate Optimization

**Priority:** Low (gameplay responsiveness)  
**Area:** World Service / Game Loop  
**Effort:** Small (1-2 days)  

## Problem
In `ZoneMessageDispatcher.java`, the tick rate is currently hardcoded to 10 Hz (100ms):

```java
private static final int TICK_RATE_MS = 100;
```

While 100ms is sufficient for basic early development to avoid thread/CPU overhead, a production-grade action game loop requires a higher resolution (e.g. 30 Hz or 60 Hz / 16.67ms) to handle smooth positional tracking, physics simulation, and low-latency combat ticks.

## What Needs to Happen
1. Refactor `TICK_RATE_MS` to be configurable via `ServerProperties`.
2. Optimize the zone entity updating and broadcasting loops to handle execution under 16ms without dropping ticks.
3. Decouple network broadcast rates (which can remain at 10-20 Hz) from the internal state update rate (60 Hz) using dirty state flags to minimize outbound network packet traffic.

## Acceptance Criteria
- Game loop tick rate can be set to 60 Hz (16.6ms) in configurations.
- Ticks complete on schedule without causing CPU spikes or thread starvation in local development.
