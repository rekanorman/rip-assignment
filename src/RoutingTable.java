import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

public class RoutingTable {
    /**
     * The routing table, containing an entry for each known destination.
     * Each entry maps the router ID of the destination to a RoutingTableEntry
     * which contains information about the destination such as the metric
     * and the next hop router to reach it.
     */
    private HashMap<Integer, RoutingTableEntry> table = new HashMap<>();

    /**
     * The neighbours of this router, represented as a map from router ID to
     * metric. This map is populated with the values in the config file,
     * then never modified, and allows the router to keep a record of its
     * neighbours even if one of them crashes and is therefore removed from
     * the routing table.
     */
    private HashMap<Integer, Integer> neighbours = new HashMap<>();

    /**
     * The main routing daemon instance which this table belongs to.
     * Needed to allow updates to be triggered when a route is set to infinity.
     */
    private RIPDaemon daemon;

    /**
     * The router ID of this router.
     */
    private int routerId;

    /**
     * The time in seconds after which routing table entries timeout.
     */
    private int timeoutPeriod;

    /**
     * The time in seconds after which expired routing table entries are
     * removed from the table.
     */
    private int garbageCollectionPeriod;

    /**
     * Takes a list containing information about each of the router's
     * neighbours in the form [inputPort, metric, routerId], and initialises
     * the routing table and neighbours map with this information.
     * @param daemon        The RIP daemon instance which this table belongs to.
     * @param routerId      The router ID of the router this table belongs to.
     * @param neighbours    A list containing information about each neighbour.
     * @param timeoutPeriod Time after which routing table entries timeout.
     * @param garbageCollectionPeriod  Time after which expired entries are
     *                                 deleted from the routing table.
     */
    public RoutingTable(RIPDaemon daemon, int routerId,
                        ArrayList<int[]> neighbours, int timeoutPeriod,
                        int garbageCollectionPeriod) {
        this.daemon = daemon;
        this.routerId = routerId;
        this.timeoutPeriod = timeoutPeriod;
        this.garbageCollectionPeriod = garbageCollectionPeriod;

        for (int[] neighbour : neighbours) {
            int metric = neighbour[1];
            int id = neighbour[2];
            addEntry(id, metric, id);

            this.neighbours.put(id, metric);
        }
    }

    /**
     * Adds a new entry to the routing table, initialising the timeout timer.
     * @param destId   The router ID of the destination.
     * @param metric   The metric to reach the destination.
     * @param nextHop  The ID of the next hop router to reach the destination.
     */
    public void addEntry(int destId, int metric, int nextHop) {
        RoutingTableEntry entry = new RoutingTableEntry(destId, metric, nextHop);
        entry.resetTimeout();
        this.table.put(destId, entry);
    }

    /**
     * Checks the timeout and garbage-collection timers of each routing table
     * entry, and performs the appropriate actions if any timers have expired.
     *
     */
    public void checkTimers() {
        ArrayList<Integer> toDelete = new ArrayList<>();

        for (RoutingTableEntry entry : this.table.values()) {
            if (entry.timeoutExpired()) {
                startDeletion(entry.destId);
            }

            if (entry.garbageCollectionExpired()) {
                toDelete.add(entry.destId);
            }
        }

        for (int destId : toDelete) {
            this.table.remove(destId);
        }
    }

    /**
     * Resets the timeout timer for the entry with the given ID, also stopping
     * the garbage collection timer for the entry if it has been started.
     * @param destId    The dest ID of the entry for which to reset timeout.
     */
    public void resetTimeout(int destId) {
        table.get(destId).resetTimeout();
    }

    /**
     * Starts the deletion process for the routing table entry with the given
     * destination ID. Should be called both when the timeout timer for an entry
     * expires, and when an update is received with the metric for an existing
     * route set to infinity.
     * @param destId    The destination ID of the routing table entry to delete.
     */
    public void startDeletion(int destId) {
        RoutingTableEntry entry = this.table.get(destId);
        entry.startGarbageCollectionTimer();
        entry.metric = RIPDaemon.INFINITY;
        this.daemon.triggerUpdate();
    }

    @Override
    public String toString() {
        String separator = new String(new char[77]).replace("\0", "-") + "\n";
        String result = "";
        result += separator;
        result += String.format("Router %d\n", routerId);
        result += separator;
        result += String.format("%-13s | %-13s | %-13s | %-13s | %-13s\n",
                "Dest ID", "Next Hop ID", "Metric", "Timeout Timer", "GC Timer");
        result += separator;

        for (RoutingTableEntry entry : this.table.values()) {
            String timeoutTime = "-";
            if (entry.secondsUntilTimeout() >= 0) {
                timeoutTime = Long.toString(entry.secondsUntilTimeout());
            }

            String garbageCollectionTime = "-";
            if (entry.secondsUntilGC() >= 0) {
                garbageCollectionTime = Long.toString(entry.secondsUntilGC());
            }

            result += String.format("%-13s | %-13s | %-13s | %-13s | %-13s\n",
                    entry.destId, entry.nextHop, entry.metric,
                    timeoutTime, garbageCollectionTime);
        }

        return result;
    }

    /**
     * Return a set of all the destination IDs which currently have a route in
     * the table.
     * @return  A set of all dest IDs currently in the routing table.
     */
    public Set<Integer> allDestIds() {
        return table.keySet();
    }

    /**
     * Returns the number of entries in the routing table.
     * @return  Number of entries in the routing table.
     */
    public int numEntries() {
        return table.size();
    }

    /**
     * Checks whether there is an existing route to the given destination ID.
     * @param destId    The ID of the destination to check the table for.
     * @return          True if there is an entry for the given ID in the table,
     *                  even if it has timed out.
     */
    public boolean hasRoute(int destId) {
        return table.containsKey(destId);
    }

    public int getMetric(int destId) {
        return table.get(destId).metric;
    }

    public void setMetric(int destId, int metric) {
        table.get(destId).metric = metric;
    }

    public int getNextHop(int destId) {
        return table.get(destId).nextHop;
    }

    public void setNextHop(int destId, int nextHop) {
        table.get(destId).nextHop = nextHop;
    }

    public boolean isNeighbour(int id) {
        return this.neighbours.containsKey(id);
    }

    public int getMetricToNeighbour(int id) {
        return this.neighbours.get(id);
    }


    /**
     * Instances of RoutingTableEntry represent a single entry in the routing
     * table, containing the destination router ID, metric, next hop router ID,
     * and timeout the garbage collection timers.
     */
    private class RoutingTableEntry {

        private int destId;

        private int metric;

        private int nextHop;

        /**
         * The time when the timeout timer for this entry will expire.
         */
        private LocalTime timeoutTime;

        /**
         * The time when the garbage-collection timer will expire.
         * Only meaningful if garbageCollectionStarted is true.
         */
        private LocalTime garbageCollectionTime;

        /**
         * Whether or not the garbage collection timer for this entry has
         * been started.
         */
        private boolean garbageCollectionStarted = false;

        private RoutingTableEntry(int destId, int metric, int nextHop) {
            this.destId = destId;
            this.metric = metric;
            this.nextHop = nextHop;
        }

        /**
         * Resets the timeout timer for this entry, also cancelling the
         * garbage collection timer if running.
         */
        private void resetTimeout() {
            this.timeoutTime = LocalTime.now().plusSeconds(timeoutPeriod);
            this.garbageCollectionStarted = false;
        }

        /**
         * Checks whether the timeout timer for this entry has just expired.
         * If the garbage collection timer has been started, then the timeout
         * was already expired, so return false.
         * @return  Whether the timeout timer for this entry has just expired.
         */
        private boolean timeoutExpired() {
            return !garbageCollectionStarted &&
                    LocalTime.now().isAfter(this.timeoutTime);
        }

        /**
         * Starts the garbage collection timer for this entry.
         */
        private void startGarbageCollectionTimer() {
            this.garbageCollectionStarted = true;
            this.garbageCollectionTime = LocalTime.now().plusSeconds(
                    garbageCollectionPeriod);
        }

        /**
         * Checks whether the garbage collection timer has expired.
         * @return True the garbage collection timer is running and has expired.
         */
        private boolean garbageCollectionExpired() {
            return this.garbageCollectionStarted &&
                    LocalTime.now().isAfter(this.garbageCollectionTime);
        }

        /**
         * Returns the time in seconds until the timeout timer will expire,
         * or -1 if the timeout timer is not running (garbage collection has
         * started).
         * @return  Time until the timeout timer will expire.
         */
        private long secondsUntilTimeout() {
            if (garbageCollectionStarted) {
                return -1;
            }
            return LocalTime.now().until(timeoutTime, ChronoUnit.SECONDS);
        }

        /**
         * Returns the time in seconds until the garbage collection timer will
         * expire, or -1 if the garbage collection timer has not been started.
         * @return  Time until the garbage collection timer will expire.
         */
        private long secondsUntilGC() {
            if (!garbageCollectionStarted) {
                return -1;
            }
            return LocalTime.now().until(garbageCollectionTime,
                    ChronoUnit.SECONDS);
        }
    }
}
