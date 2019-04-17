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
     * The next time that update messages should be sent to neighbours.
     * Set to the current time plus a random value in the range
     * [updatePeriod * 0.8, updatePeriod * 1.2] every time that an update is
     * sent.
     */
    private LocalTime nextUpdateTime;


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

        this.table = new RoutingTable(outputs, timeoutPeriod,
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

        // Send initial response messages.
        this.output.sendUpdates();
        setNextUpdateTime();
    }

    /**
     * Sets the nextUpdateTime to the current time plus the update period,
     * offset by a random small amount.
     */
    private void setNextUpdateTime() {
        double randomMultiplier = Math.random() * 0.4 + 0.8;
        double randomPeriodSeconds = this.updatePeriod * randomMultiplier;
        long randomPeriodNanos = (long) (randomPeriodSeconds * 1000000000);
        this.nextUpdateTime = LocalTime.now().plusNanos(randomPeriodNanos);
    }

    /**
     * Enter an infinite loop to wait for events and handle them as needed.
     */
    private void run() {
        // TODO: Use select to block while waiting for events
        while (true) {
            // Check whether it's time for a periodic update.
            if (LocalTime.now().isAfter(this.nextUpdateTime)) {
                this.output.sendUpdates();
                setNextUpdateTime();
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
