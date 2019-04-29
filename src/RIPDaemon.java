import java.time.LocalTime;
import java.util.ArrayList;

public class RIPDaemon {
    /**
     * The metric value used to represent infinity.
     */
    public static final int INFINITY = 16;

    /**
     * Range of allowed router IDs.
     */
    public static final int MIN_ROUTER_ID = 1;
    public static final int MAX_ROUTER_ID = 64000;

    /**
     * Range of allowed port numbers.
     */
    public static final int MIN_PORT_NO = 1024;
    public static final int MAX_PORT_NO = 64000;

    /**
     * The maximum allowed size in bytes for response packets.
     */
    public static final int MAX_RESPONSE_PACKET_SIZE = 512;

    /**
     * Size of the RIP response message header in bytes.
     */
    public static final int HEADER_BYTES = 4;

    /**
     * Size of each RIP entry in a response message in bytes. Each entry
     * consists of two 32-bit fields: destination router ID and metric.
     */
    public static final int RIP_ENTRY_BYTES = 8;

    /**
     * The value to put in the command field of a response message.
     */
    public static final byte RESPONSE_COMMAND = 2;

    /**
     * The value to put in the version field of the response messages.
     */
    public static final byte RIP_VERSION = 2;

    /**
     * The ratio of the timeout timer period to the periodic update
     * timer period.
     */
    private static final int TIMEOUT_PERIOD_RATIO = 6;

    /**
     * The ratio of the garbage-collection timer period to the periodic update
     * timer period.
     */
    private static final int GARBAGE_COLLECTION_PERIOD_RATIO = 4;

    /**
     * The timeout in milliseconds for the blocking select call used to
     * select readable input sockets.
     */
    private static final int INPUT_SELECT_TIMEOUT = 1000;

    /**
     * The routing table of this router, containing an entry for each known
     * destination.
     */
    private RoutingTable table;

    /**
     * Handles generating response messages for each neighbour, and sending
     * these messages every time a response is triggered.
     */
    private Output output;

    /**
     * Deals with receiving, validating and processing incoming response
     * messages received from neighbouring routers.
     */
    private Input input;

    /**
     * The time to wait between sending periodic updates (in seconds).
     * Can be specified in the config file, otherwise the default value is used.
     */
    private int updatePeriod = 30;

    /**
     * The next time that periodic update messages should be sent to neighbours.
     * Set to the current time plus a random value in the range
     * [updatePeriod * 0.8, updatePeriod * 1.2] every time that a periodic
     * update is sent.
     */
    private LocalTime nextPeriodicUpdateTime;

    /**
     * Set to true if an update has been triggered. Only happens when the
     * metric for a route is set to infinity.
     */
    private boolean updateTriggered = false;

    /**
     * Set to true when the triggered update timer is started (after sending
     * a triggered update).
     */
    private boolean triggeredUpdateTimerRunning = false;

    /**
     * The time when the triggered update timer expires. Only meaningful if
     * triggeredUpdateTimerRunning is true.
     */
    private LocalTime nextTriggeredUpdateTime;

    /**
     * Creates a new RIP daemon using the values specified in the config file.
     * @param routerId      The router ID of the router.
     * @param inputPorts    A list of the router's input port numbers.
     * @param outputs       A list of the router's neighbours, in the form
     *                          [inputPort, metric, routerId].
     * @param outputPort    The port number to use for the output socket.
     * @param updatePeriod  The update period specified in the config file, or
     *                          0 if no period was specified.
     */
    private RIPDaemon(int routerId, ArrayList<Integer> inputPorts,
                      ArrayList<int[]> outputs, int outputPort,
                      int updatePeriod) {
        // Set the update timer period to the value in the config file if it
        // was specified.
        if (updatePeriod != 0) {
            this.updatePeriod = updatePeriod;
        }

        // Initialise the routing table and display the initial contents.
        int timeoutPeriod = this.updatePeriod * TIMEOUT_PERIOD_RATIO;
        int garbageCollectionPeriod = this.updatePeriod
                * GARBAGE_COLLECTION_PERIOD_RATIO;
        this.table = new RoutingTable(this, routerId, outputs,
                timeoutPeriod, garbageCollectionPeriod);

        this.input = new Input(inputPorts, this.table);

        this.output = new Output(routerId, outputPort, outputs, this.table);

        // Send initial response messages.
        this.output.sendUpdates();
        setNextPeriodicUpdateTime();
    }

    /**
     * Schedules a triggered update to be sent, should be called whenever the
     * metric of a route is set to infinity.
     */
    public void triggerUpdate() {
        this.updateTriggered = true;
    }

    /**
     * Sets the nextPeriodicUpdateTime to the current time plus the update
     * period, offset by a small random amount.
     */
    private void setNextPeriodicUpdateTime() {
        double randomMultiplier = Math.random() * 0.4 + 0.8;
        double randomPeriodSeconds = updatePeriod * randomMultiplier;
        long randomPeriodNanos = (long) (randomPeriodSeconds * 1000000000);
        nextPeriodicUpdateTime = LocalTime.now().plusNanos(randomPeriodNanos);
    }

    /**
     * Sets the nextTriggeredUpdateTime to the current time plus a random time
     * between 1 and 5 seconds.
     */
    private void setNextTriggeredUpdateTime() {
        double waitTimeSeconds = Math.random() * 4 + 1;
        long waitTimeNanos  = (long) (waitTimeSeconds * 1000000000);
        nextTriggeredUpdateTime = LocalTime.now().plusNanos(waitTimeNanos);
    }

    /**
     * Sends an update either if it's time to send a periodic update or if an
     * update has been triggered, provided enough time has passed since the
     * last triggered update.
     */
    private void sendUpdateIfTime() {
        if (!this.triggeredUpdateTimerRunning ||
                LocalTime.now().isAfter(this.nextTriggeredUpdateTime)) {

            if (LocalTime.now().isAfter(nextPeriodicUpdateTime)) {
                // Send periodic update (suppresses any triggered updates).
                this.output.sendUpdates();
                setNextPeriodicUpdateTime();
                this.updateTriggered = false;
                this.triggeredUpdateTimerRunning = false;

            } else if (this.updateTriggered) {
                // Send triggered update
                this.output.sendUpdates();
                this.updateTriggered = false;
                this.triggeredUpdateTimerRunning = true;
                setNextTriggeredUpdateTime();
            }
        }
    }

    /**
     * Enter an infinite loop to wait for events and handle them as needed.
     */
    private void run() {
        while (true) {
            // Use a blocking select call to wait for response packets to be
            // received, then process any received packets.
            input.waitForMessages(INPUT_SELECT_TIMEOUT);

            sendUpdateIfTime();
            this.table.checkTimers();

            // Display the current state of the routing table.
            System.out.println(this.table);
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            Error.error("Usage: java RIPDaemon <config-filename>");
        }

        ConfigFileParser parser = new ConfigFileParser(args[0]);
        parser.parseFile();

        RIPDaemon daemon = new RIPDaemon(parser.getRouterId(),
                                         parser.getInputPorts(),
                                         parser.getOutputs(),
                                         parser.getOutputPort(),
                                         parser.getUpdatePeriod());

        daemon.run();
    }
}
