import java.time.LocalTime;

public class RoutingTableEntry {
    /**
     * The router ID of the destination.
     */
    private int destId;

    /**
     * The metric to reach this destination.
     */
    private int metric;

    /**
     * Router ID of the next hop router in the path to this destination.
     * If destId == nextHop, this destination is one of the router's directly
     * attached neighbours.
     */
    private int nextHop;

    /**
     * The time when the timeout timer will expire.
     */
    private LocalTime timeoutTime;

    /**
     * The time when the garbage-collection timer will expire.
     */
    private LocalTime garbageCollectionTime;

    /**
     * Whether or not the garbage collection timer is currently running.
     * Set to true when the deletion process for the entry is started.
     */
    private boolean garbageCollectionRunning = false;


    public RoutingTableEntry(int destId, int metric, int nextHop) {
        this.destId = destId;
        this.metric = metric;
        this.nextHop = nextHop;
    }

    public boolean timeoutExpired() {
        return !garbageCollectionRunning &&
                LocalTime.now().isAfter(this.timeoutTime);
    }

    public void setTimeoutTime(LocalTime timeoutTime) {
        this.timeoutTime = timeoutTime;
        this.garbageCollectionRunning = false;
    }

    public boolean garbageCollectionExpired() {
        return this.garbageCollectionRunning &&
                LocalTime.now().isAfter(this.garbageCollectionTime);
    }

    public void setGarbageCollectionTime(LocalTime garbageCollectionTime) {
        this.garbageCollectionTime = garbageCollectionTime;
        this.garbageCollectionRunning = true;
    }

    public int getDestId() {
        return this.destId;
    }

    public void setDestId(int destId) {
        this.destId = destId;
    }

    public int getMetric() {
        return this.metric;
    }

    public void setMetric(int metric) {
        this.metric = metric;
    }

    public int getNextHop() {
        return this.nextHop;
    }

    public void setNextHop(int nextHop) {
        this.nextHop = nextHop;
    }
}
