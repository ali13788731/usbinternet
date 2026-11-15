package app.usbnet

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicLong

enum class LinkState { Idle, Starting, WaitingForPc, Connected }

data class Snapshot(
    val state: LinkState = LinkState.Idle,
    val downBps: Long = 0,
    val upBps: Long = 0,
    val totalDown: Long = 0,
    val totalUp: Long = 0,
    /** Epoch millis of the first successful connection in this session, 0 = none yet. */
    val connectedSinceMs: Long = 0,
    val downHistory: List<Long> = emptyList(),
    val upHistory: List<Long> = emptyList(),
)

/**
 * Process-wide bridge between the VPN service (writer) and the Compose UI (reader).
 * Direction naming is from the phone's point of view:
 * "down" = bytes coming from the computer, "up" = bytes sent to the computer.
 */
object TunnelMonitor {
    const val HISTORY = 60

    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot

    private val down = AtomicLong()
    private val up = AtomicLong()
    private var lastDown = 0L
    private var lastUp = 0L
    private var lastTickMs = 0L

    fun addDown(n: Int) {
        down.addAndGet(n.toLong())
    }

    fun addUp(n: Int) {
        up.addAndGet(n.toLong())
    }

    @Synchronized
    fun reset() {
        down.set(0)
        up.set(0)
        lastDown = 0
        lastUp = 0
        lastTickMs = System.currentTimeMillis()
        _snapshot.value = Snapshot()
    }

    @Synchronized
    fun setState(state: LinkState) {
        val cur = _snapshot.value
        if (state == LinkState.Idle) {
            _snapshot.value = Snapshot()
            return
        }
        val since = if (state == LinkState.Connected && cur.connectedSinceMs == 0L) {
            System.currentTimeMillis()
        } else {
            cur.connectedSinceMs
        }
        _snapshot.value = cur.copy(state = state, connectedSinceMs = since)
    }

    /** Called about once per second by the service. */
    @Synchronized
    fun tick() {
        val now = System.currentTimeMillis()
        val elapsed = (now - lastTickMs).coerceAtLeast(1)
        lastTickMs = now
        val d = down.get()
        val u = up.get()
        val downBps = (d - lastDown) * 1000 / elapsed
        val upBps = (u - lastUp) * 1000 / elapsed
        lastDown = d
        lastUp = u
        val cur = _snapshot.value
        _snapshot.value = cur.copy(
            downBps = downBps,
            upBps = upBps,
            totalDown = d,
            totalUp = u,
            downHistory = (cur.downHistory + downBps).takeLast(HISTORY),
            upHistory = (cur.upHistory + upBps).takeLast(HISTORY),
        )
    }
}
