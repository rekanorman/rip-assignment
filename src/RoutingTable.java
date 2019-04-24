import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public class RoutingTable {
    /**
     * The routing table, containing an entry for each known destination.
     * Each entry maps the router ID of the destination to a RoutingTableEntry
     * which contains information about the destination such as the metric
     * and the next hop router to reach it.
     */
    private HashMap<Integer, RoutingTableEntry> table = new HashMap<>();

    /**
     * The main routing daemon object which this table belongs to.
     * Needed to allow updates to be triggered when a route is set to infinity.
     */
    private RIPDaemon daemon;

    /**
     * The time after which routing table entries timeout. Set with a fixed
     * ratio relative to the periodic update period.
     */
    private int timeoutPeriod;

    /**
     * The time after which expired routing table entries are removed from the
     * table. Set with a fixed ratio relative to the periodic update period.
     */
    private int garbageCollectionPeriod;

    /**
     * Takes a list containing information about each of the router's
     * neighbours in the form [inputPort, metric, routerId], and initialises
     * the routing table with this information.
     * @param neighbours  A list containing information about each neighbour.
     */
    public RoutingTable(RIPDaemon daemon, ArrayList<int[]> neighbours,
                        int timeoutPeriod, int garbageCollectionPeriod) {
        this.daemon = daemon;
        this.timeoutPeriod = timeoutPeriod;
        this.garbageCollectionPeriod = garbageCollectionPeriod;

        for (int[] neighbour : neighbours) {
            int metric = neighbour[1];
            int id = neighbour[2];
            int nextHop = id;
            addEntry(id, metric, nextHop);
        }
    }

    /**
     * Adds a new entry to the routing table.
     * @param destId   The router ID of the destination.
     * @param metric   The metric to reach the destination.
     * @param nextHop  The ID of the next hop router to reach the destination.
     */
    private void addEntry(int destId, int metric, int nextHop) {
        RoutingTableEntry entry = new RoutingTableEntry(destId, metric, nextHop);
        entry.setTimeoutTime(LocalTime.now().plusSeconds(timeoutPeriod));
        this.table.put(destId, entry);
    }

    public HashMap<Integer, Integer> getNeighbours(ArrayList<int[]> outputs) {

        HashMap<Integer, Integer> neighbours = new HashMap<>();

        for (int[] neighbour : outputs) {
            int id = neighbour[2];
            int portNo = neighbour[0];
            neighbours.put(id, portNo);
        }

        return neighbours;

    }

    /**
     * Returns the number of entries in the routing table.
     * @return  Number of entries in the routing table.
     */
    public int numEntries() {
        return table.size();
    }

    /**
     * Return a collection of all the entries currently in the routing table.
     * @return  A collection of the routing table entries.
     */
    public Collection<RoutingTableEntry> getEntries() {
        return table.values();
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
                startDeletion(entry.getDestId());
                System.out.println(String.format("Entry for router %d timeout.",
                        entry.getDestId()));
            }

            if (entry.garbageCollectionExpired()) {
                toDelete.add(entry.getDestId());
                System.out.println(String.format("Entry for router %d deleted.",
                        entry.getDestId()));
            }
        }

        for (int destId : toDelete) {
            this.table.remove(destId);
        }
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
        entry.setGarbageCollectionTime(
                LocalTime.now().plusSeconds(garbageCollectionPeriod));
        entry.setMetric(RIPDaemon.INFINITY);
        this.daemon.triggerUpdate();
    }

    @Override
    public String toString() {
        String result = String.format("%-20s | %-20s | %-20s\n",
                "Destination ID", "Next Hop ID", "Metric");
        result += new String(new char[66]).replace("\0", "-");
        result += "\n";

        for (RoutingTableEntry entry : this.table.values()) {
            result += String.format("%-20d | %-20d | %-20d\n",
                    entry.getDestId(), entry.getNextHop(), entry.getMetric());
        }

        return result;
    }
}
