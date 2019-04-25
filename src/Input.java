import java.io.IOException;
import java.net.*;
import java.lang.Integer;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;


public class Input {

    /**
     * Size of the RIP response message header in bytes.
     */
    private static final int HEADER_BYTES = 4;

    /**
     * Size of each RIP entry in a response message in bytes.
     * TODO: For now, each RIP entry is just Dest ID and Metric, each 32 bits.
     *       This can be changed if needed.
     */
    private static final int RIP_ENTRY_BYTES = 8;

    /**
     * The value to put in the version field of the response messages.
     */
    private static final byte RIP_VERSION = 2;

    /**
     * The router ID of the router receiving the updates.
     */
    private int routerId;

    /**
     * The IP address to receive messages from, initialised to the
     * localhost address in the constructor.
     */
    private InetAddress address;

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
    public Input(ArrayList<Integer> inputPorts, int routerId, RoutingTable table) {
        this.routerId = routerId;
        this.table = table;

        try {
            this.selector = Selector.open();
        } catch (IOException e) {
            Error.error("Error: Could not instantiate input port selector");
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
                        System.out.println("Received packet.");
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
        inBuffer.rewind();
        int command = inBuffer.get();
        int version = inBuffer.get();
        int senderId = Util.byteArrayToInt(new byte[] {0, 0, inBuffer.get(), inBuffer.get()});

        System.out.println(String.format("Command: %d\nVersion: %d\nId: %d\n", command, version, senderId));

        table.resetTimeout(senderId);

        while (inBuffer.hasRemaining()) {
            int destId = inBuffer.getInt();
            if (destId == 0) {
                break;
            }
            int metric = inBuffer.getInt();
            System.out.println(String.format("Dest Id: %d\nMetric: %d\n", destId, metric));

            processEntry(senderId, destId, metric);
        }

        System.out.println(table);
    }

    private void processEntry(int senderId, int destId, int metricFromNeighbour) {
        if (destId == senderId || destId == routerId) {
            return;
        }

        int metric = metricFromNeighbour + table.getMetric(senderId);
        if (metric > RIPDaemon.INFINITY) {
            metric = RIPDaemon.INFINITY;
        }

        if (table.hasEntry(destId)) {
            if (table.getNextHop(destId) == senderId || metric < table.getMetric(destId)) {
                if (metric == RIPDaemon.INFINITY && table.getMetric(destId) != RIPDaemon.INFINITY) {
                    table.startDeletion(destId);
                }

                table.setMetric(destId, metric);
                table.setNextHop(destId, senderId);

                if (metric != RIPDaemon.INFINITY) {
                    System.out.println("Timeout reset");
                    table.resetTimeout(destId);
                }
            }
        } else {
            if (metric != RIPDaemon.INFINITY) {
                table.addEntry(destId, metric, senderId);
            }
        }
    }

    /**
     * Checks whether the received packet stored in the inBuffer is valid.
     * @return Whether the packet is valid.
     */
    private boolean packetValid() {

        // TODO: Add logic for ignoring the packet. Not sure how to ignore/discard packet or log it yet
        boolean packetValid = false;
        boolean entryValid = false;



//        int i = 4;
//        for (byte b : message) {
//
//            // Check that the destination address is valid (unicast)
//            byte[] destinationAddress = Arrays.copyOfRange(message, 0, i);
//            /*I don't think this location is right but not sure where to locate
//            metric number at this point (going off of Output.createMessage())*/
//            byte[] metric = Arrays.copyOfRange(message, 0, i + 4);
//            int metricNo = Util.byteArrayToInt(metric);
//
////                if (Util.byteArrayToInt(destinationAddress) == Util.byteArrayToInt(address)){
////                    // Check that the metric is between 1 and 16
////                    if (metricNo >=1 && metricNo <=16){
////                        entryValid = true;
////                        processUpdate(receivedPacket);
////                    }
////                }
//
//            i += RIP_ENTRY_BYTES;
//
//        }
        return false;
    }

    public void processUpdate(DatagramPacket receivedPacket) {




    }


    public void displayPacketInfo(DatagramPacket receivedPacket) {
        String message = new String(receivedPacket.getData(), receivedPacket.getOffset(), receivedPacket.getLength());

        InetAddress addr = receivedPacket.getAddress();
        System.out.println("Sent by: " + addr.getHostAddress());
        System.out.println("Sent from port: " + receivedPacket.getPort());
        System.out.println("Message: \n" + message);
    }
}
