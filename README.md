# Kairos

Kairos is a deterministic time-execution engine for Android. It enables precise, persistent scheduling of time-based events using exact alarms, ensuring reliable execution even under Doze mode and across process restarts or device reboots.

Kairos is designed for systems that must execute at a specific moment without relying on background polling, timers, or continuously running services.

---

## Overview

Kairos provides:

* Exact alarm scheduling
* Persistence of scheduled events
* Restoration after reboot or process death
* Expired event cleanup
* Logical grouping of events by type
* Minimal runtime overhead

It is intended for precise, single-moment execution scenarios such as:

* Time-bound workflows
* Timely actions
* Local event triggers
* Offline scheduling
* Deterministic state transitions

---

## Installation

To include the **Kairos** in your project, follow the instructions below.

```gradle
dependencies {
    implementation 'com.github.styropyr0:Kairos:1.0.0'
}
```
or for `app:build.gradle.kts`

```kotlin
dependencies {
    implementation("com.github.styropyr0:Kairos:1.0.0")
}
```

Add the following to your `settings.gradle`:

```gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```
or for `settings.gradle.kts`

```kotlin
dependencyResolutionManagement {
		repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
		repositories {
			mavenCentral()
			maven { url = uri("https://jitpack.io") }
		}
	}
```
---

## Requirements

At least one of the following must be granted:

* `SCHEDULE_EXACT_ALARM` permission (Android 12+), or
* Battery optimization exemption

Without appropriate privileges, the system may prevent exact alarm scheduling.

---

## Initialization

Kairos must be initialized once at the application level before any API usage.

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Kairos.init(this)
    }
}
```

Using Kairos before initialization will result in an `IllegalStateException`.

---

# Core Concepts

## KairosEvent

Every scheduled item must implement `KairosEvent`.

```kotlin
data class SampleEvent(
    val id: String,
    override val timeSlot: KairosTimeSlot
) : KairosEvent {

    override val uniqueId: String = id
    override val slotId: String = id
    override val type: String = KEY

    companion object {
        const val KEY = "SAMPLE_EVENT"
    }
}
```

### Properties

* `uniqueId` — Logical identifier of the event
* `slotId` — Identifier for a specific execution instance
* `type` — Logical grouping key used internally
* `timeSlot.startTime` — The execution moment

---

## KairosReceiver

Each event must provide a corresponding receiver that handles execution.

```kotlin
class SampleReceiver : KairosReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val uniqueId = intent?.getStringExtra("UNIQUE_ID") ?: return
        val slotId = intent.getStringExtra("SLOT_ID") ?: return

        // Execute domain-specific logic here
    }
}
```

---

# Scheduling an Event

```kotlin
val calendar = Calendar.getInstance().apply {
    add(Calendar.MINUTE, 5)
}

val event = SampleEvent(
    id = "123",
    timeSlot = KairosTimeSlot(
        startTime = calendar,
        endTime = calendar
    )
)

Kairos.createMilestoneEvent(
    context,
    event,
    SampleReceiver::class.java
)
```

The event will execute at the specified `startTime`.

---

# Restoring Scheduled Events

To restore persisted events after reboot or process restart:

```kotlin
Kairos.rescheduleCachedEvents(
    context,
    SampleReceiver::class.java,
    SampleEvent.KEY
)
```

This is typically invoked during application startup or inside a `BOOT_COMPLETED` receiver.

---

# Removing Events

### Cancel a Specific Event

```kotlin
Kairos.cleanUpMilestoneEvent(
    context,
    uniqueId = "123",
    slotId = "123",
    type = SampleEvent.KEY,
    receiver = SampleReceiver::class.java
)
```

### Clear All Events of a Type

```kotlin
Kairos.clearAllScheduledEvents(
    context,
    type = SampleEvent.KEY,
    receiver = SampleReceiver::class.java
)
```

Passing `"*"` as `uniqueId` clears all events under the given type.

---

# Cleaning Expired Events

To remove events whose execution time has already passed:

```kotlin
Kairos.defragment(
    context,
    SampleEvent.KEY,
    SampleReceiver::class.java
)
```

This is recommended before restoring cached events.

---

# Checking Power Optimization State

```kotlin
val isExempt = Kairos.hasPowerExemption(context)
```

This can be used to determine whether the application is exempt from battery optimization restrictions.

---

# Recommended Architecture

Kairos is intentionally designed as a low-level execution engine.

It is strongly recommended not to call Kairos APIs directly from business logic.
Instead, wrap Kairos inside a domain-specific scheduler that acts as an interface for a particular event type.

This ensures:

* Clear separation of concerns
* Domain-level abstraction
* Centralized scheduling logic
* Safer API usage
* Easier testing and maintenance

---

## Recommended Structure

```
YourDomainScheduler
 ├── schedule()
 ├── cancel()
 ├── sync()
 └── clear()

YourDomainEvent → implements KairosEvent
YourDomainReceiver → extends KairosReceiver
```

---

## Example Implementation

### 1. Define a Domain Event

```kotlin
data class OrderTimeoutEvent(
    val orderId: String,
    override val timeSlot: KairosTimeSlot
) : KairosEvent {

    override val uniqueId: String = orderId
    override val slotId: String = orderId
    override val type: String = KEY

    companion object {
        const val KEY = "ORDER_TIMEOUT_EVENT"
    }
}
```

---

### 2. Implement a Receiver

```kotlin
class OrderTimeoutReceiver : KairosReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val orderId = intent?.getStringExtra("UNIQUE_ID") ?: return

        // Perform domain-specific logic here
    }
}
```

---

### 3. Create a Domain Scheduler

```kotlin
class OrderTimeoutScheduler(private val context: Context) {

    private val receiver = OrderTimeoutReceiver::class.java

    fun schedule(event: OrderTimeoutEvent) {
        Kairos.createMilestoneEvent(context, event, receiver)
    }

    fun cancel(orderId: String) {
        Kairos.cleanUpMilestoneEvent(
            context,
            uniqueId = orderId,
            slotId = orderId,
            type = OrderTimeoutEvent.KEY,
            receiver = receiver
        )
    }

    fun sync() {
        Kairos.defragment(context, OrderTimeoutEvent.KEY, receiver)
        Kairos.rescheduleCachedEvents(context, receiver, OrderTimeoutEvent.KEY)
    }

    fun clear() {
        Kairos.clearAllScheduledEvents(context, OrderTimeoutEvent.KEY, receiver)
    }
}
```

---

## Scope

Kairos is not intended as a replacement for WorkManager. It is not suitable for:

* Long-running background work
* Heavy network tasks
* Periodic repeating jobs
* Complex dependency chains

Kairos is optimized for precise, deterministic execution at a specific moment in time.

---

## License

MIT License
