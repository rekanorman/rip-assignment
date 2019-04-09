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


    public RoutingTableEntry(int destId, int metric, int nextHop) {
        this.destId = destId;
        this.metric = metric;
        this.nextHop = nextHop;
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
