import java.io.IOException;
import java.net.*;
import java.lang.Integer;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;


public class Input {
    /**
     * The router ID of the router receiving the updates.
     */
    private int routerId;

    /**
     * The selector used to select input sockets which are ready to read.
     */
    private Selector selector;

    /**
     * Routing table of receiving router.
     */
    private RoutingTable table;

    /**
     * A byte buffer to store data received from the input sockets.
     */
    private ByteBuffer inBuffer = ByteBuffer.allocate(
            RIPDaemon.MAX_RESPONSE_PACKET_SIZE);


    /**
     * Creates a new Input object for receiving update messages from neighbours.
     * @param inputPorts    A list of the port numbers to use for input sockets.
     * @param routerId      The ID of the router receiving the updates.
     * @param table         The routing table of router receiving the updates.
     */
    public Input(ArrayList<Integer> inputPorts, int routerId,
                 RoutingTable table) {
        this.routerId = routerId;
        this.table = table;

        try {
            this.selector = Selector.open();
        } catch (IOException e) {
            Error.error("Error: Could not instantiate input port " +
                    "selector");
        }

        // Register a datagram channel for each input port with the selector.
        for (int port : inputPorts) {
            try {
                DatagramChannel channel = DatagramChannel.open();
                channel.configureBlocking(false);
                channel.socket().bind(new InetSocketAddress(port));
                channel.register(selector, SelectionKey.OP_READ);

            } catch (IOException e) {
                e.printStackTrace();
                Error.error(String.format("Error opening input socket " +
                        "with port number %d", port));
            }
        }
    }

    /**
     * Waits for response messages to be received using a blocking select call,
     * then processes any messages received, updating the routing table if
     * necessary.
     * @param selectTimeout The timeout in milliseconds for the select call.
     */
    public void waitForMessages(int selectTimeout) {
        try {
            selector.select(selectTimeout);
        } catch (IOException e) {
            System.err.println("Error selecting readable input sockets.");
            return;
        }

        for (SelectionKey key : selector.selectedKeys()) {
            if (key.isReadable()) {
                inBuffer.clear();
                DatagramChannel channel = (DatagramChannel) key.channel();
                try {
                    if (channel.receive(inBuffer) != null) {
                        processPacket();
                    } else {
                        System.err.println("ERROR: no datagram available for " +
                                "reading from input socket.");
                    }
                } catch (IOException e) {
                    System.err.println("ERROR: could not receive packet from " +
                            "input socket");
                }
            }
        }

        selector.selectedKeys().clear();
    }

    /**
     * Processes the received response packet currently stored in the inBuffer,
     * checking it for validity, then updating the routing table if needed.
     */
    private void processPacket() {
        inBuffer.flip();

        // Read the values in the header fields, and check that they are valid.
        int command = inBuffer.get();
        int version = inBuffer.get();
        short senderId = inBuffer.getShort();

        if (command != RIPDaemon.RESPONSE_COMMAND) {
            System.err.println(String.format("ERROR: Packet received with " +
                    "incorrect command value of %d.", command));
            return;
        }

        if (version != RIPDaemon.RIP_VERSION) {
            System.err.println(String.format("ERROR: Packet received with " +
                    "incorrect version value of %d.", version));
            return;
        }

        if (!table.isNeighbour(senderId)) {
            System.err.println(String.format("ERROR: Packet received with " +
                    "source router ID of %d, which is not a neighbour.",
                    senderId));
            return;
        }

        System.err.println();
        System.err.println(String.format("Packet received from %d:",
                senderId));

        // If a packet if received from a neighbour, the link to the neighbour
        // must be up, so the route to the neighbour is updated.
        processEntry(senderId, senderId, 0);

        while (inBuffer.hasRemaining()) {
            int destId = inBuffer.getInt();
            int metric = inBuffer.getInt();

            if (destId < RIPDaemon.MIN_ROUTER_ID ||
                    destId > RIPDaemon.MAX_ROUTER_ID) {
                System.err.println(String.format("ERROR: received packet " +
                        "with invalid destination ID %d.", destId));
            }

            if (metric < 1 || metric > RIPDaemon.INFINITY) {
                System.err.println(String.format("ERROR: received packet " +
                        "with invalid metric %d.", metric));
            }

            System.err.println(String.format("  Dest ID: %d  Metric: %d",
                    destId, metric));
            processEntry(senderId, destId, metric);
        }

        System.err.println();

        System.out.println(table);
    }

    /**
     * Processes a single RIP entry from a received response packet, updating
     * the routing table as necessary.
     * @param senderId     ID of router which send the packet.
     * @param destId       Destination router ID of the RIP entry.
     * @param metricSent   Metric of the RIP entry.
     */
    private void processEntry(int senderId, int destId, int metricSent) {
        int metric = metricSent + table.getMetricToNeighbour(senderId);
        if (metric > RIPDaemon.INFINITY) {
            metric = RIPDaemon.INFINITY;
        }

        if (table.hasRoute(destId)) {
            int currentNextHop = table.getNextHop(destId);
            int currentMetric = table.getMetric(destId);

            if (senderId == currentNextHop) {
                table.resetTimeout(destId);
            }

            if ((senderId == currentNextHop && metric != currentMetric) ||
                    metric < currentMetric) {
                table.setMetric(destId, metric);
                table.setNextHop(destId, senderId);

                if (metric == RIPDaemon.INFINITY) {
                    table.startDeletion(destId);
                } else {
                    table.resetTimeout(destId);
                }
            }

        } else {
            if (metric != RIPDaemon.INFINITY) {
                table.addEntry(destId, metric, senderId);
            }
        }
    }
}
