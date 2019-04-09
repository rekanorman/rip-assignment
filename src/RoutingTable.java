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
     * Takes a list containing information about each of the router's
     * neighbours in the form [inputPort, metric, routerId], and initialises
     * the routing table with this information.
     * @param neighbours  A list containing information about each neighbour.
     */
    public RoutingTable(ArrayList<int[]> neighbours) {
        for (int[] neighbour : neighbours) {
            int metric = neighbour[1];
            int id = neighbour[2];

            RoutingTableEntry entry = new RoutingTableEntry(id, metric, id);
            this.table.put(id, entry);
        }
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

    @Override
    public String toString() {
        String result = String.format("%-20s | %-20s | %-20s\n",
                "Destination ID", "Next Hop ID", "Metric");
        result += new String(new char[66]).replace("\0", "-");
        result += "\n";

        for (int id : table.keySet()) {
            RoutingTableEntry entry = table.get(id);
            result += String.format("%-20d | %-20d | %-20d\n",
                    id, entry.getNextHop(), entry.getMetric());
        }

        return result;
    }
}
