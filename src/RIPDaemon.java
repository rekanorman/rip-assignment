import java.net.DatagramSocket;
import java.net.SocketException;
import java.time.LocalTime;
import java.util.ArrayList;

public class RIPDaemon {
    /**
     * The metric value used to represent infinity.
     */
    public static final int INFINITY = 16;

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
     * The router ID of this router.
     */
    private int id;

    /**
     * The routing table of this router, containing an entry for each known
     * destination.
     */
    private RoutingTable table;

    /**
     * A list of the input sockets used to receive messages from neighbours.
     */
    private ArrayList<DatagramSocket> inputSockets = new ArrayList<>();

    /**
     * Handles generating response messages for each neighbour, and sending
     * these messages every time a response is triggered.
     */
    private Output output;

    /**
     * The time to wait between sending periodic updates (in seconds).
     * Can be specified in the config file, otherwise a default value is used.
     */
    private int updatePeriod = 10;

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


    private RIPDaemon(int routerId, ArrayList<Integer> inputPorts,
                     ArrayList<int[]> outputs, int updatePeriod) {
        this.id = routerId;

        // Set the update timer period to the value in the config file if it
        // was specified.
        if (updatePeriod != 0) {
            this.updatePeriod = updatePeriod;
        }

        // Initialise the routing table and display the initial contents.
        int timeoutPeriod = this.updatePeriod * TIMEOUT_PERIOD_RATIO;
        int garbageCollectionPeriod = this.updatePeriod * GARBAGE_COLLECTION_PERIOD_RATIO;

        this.table = new RoutingTable(this, outputs, timeoutPeriod,
                garbageCollectionPeriod);
        System.out.println("Initial routing table:\n");
        System.out.println(this.table);


        // Create a UDP socket for each input port number.
        for (int portNo : inputPorts) {
            try {
                this.inputSockets.add(new DatagramSocket(portNo));
            } catch (SocketException e) {
                Error.error(String.format("Could not bind socket to port %d.",
                        portNo));
            }
        }

        // Arbitrarily choose a socket to use for sending output messages.
        DatagramSocket outputSocket = inputSockets.get(0);
        this.output = new Output(routerId, outputSocket, outputs, this.table);

        // Socket for listening to incoming routing packets
        DatagramSocket receiveSocket = inputSockets.get(1);
        this.input = new Input(routerId, receiveSocket, outputs, this.table);

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
        this.nextPeriodicUpdateTime = LocalTime.now().plusNanos(randomPeriodNanos);
    }

    /**
     * Sets the nextTriggeredUpdateTime to the current time plus a random time
     * between 1 and 5 seconds.
     * TODO: Maybe make this time shorter for testing.
     */
    private void setNextTriggeredUpdateTime() {
        double waitTimeSeconds = Math.random() * 4 + 1;
        long waitTimeNanos  = (long) (waitTimeSeconds * 1000000000);
        this.nextTriggeredUpdateTime = LocalTime.now().plusNanos(waitTimeNanos);
    }

    /**
     * Enter an infinite loop to wait for events and handle them as needed.
     */
    private void run() {
        // TODO: Use select to block while waiting for events
        while (true) {
            // Check it's been long enough since the last triggered update.
            if (!this.triggeredUpdateTimerRunning ||
                    LocalTime.now().isAfter(this.nextTriggeredUpdateTime)) {
                if (LocalTime.now().isAfter(nextPeriodicUpdateTime)) {
                    // Periodic update suppresses any triggered updates.
                    System.out.println("Sending periodic update.");
                    this.output.sendUpdates();
                    setNextPeriodicUpdateTime();
                    this.updateTriggered = false;
                    this.triggeredUpdateTimerRunning = false;

                } else if (this.updateTriggered) {
                    System.out.println("Sending triggered update.");
                    this.output.sendUpdates();
                    this.updateTriggered = false;
                    this.triggeredUpdateTimerRunning = true;
                    setNextTriggeredUpdateTime();
                }
            }

            // Check the timers in the routing table.
            this.table.checkTimers();
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
                                         parser.getUpdatePeriod());

        daemon.run();
    }
}
