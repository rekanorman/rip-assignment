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
    private ByteBuffer inBufffer = ByteBuffer.allocate(
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

        System.out.println(selector.selectedKeys().size());

        Iterator selectedKeys = selector.selectedKeys().iterator();
        while (selectedKeys.hasNext()) {
            SelectionKey key = (SelectionKey) selectedKeys.next();
            if (key.isReadable()) {
                inBufffer.clear();
                DatagramChannel channel = (DatagramChannel) key.channel();
                try {
                    if (channel.receive(inBufffer) != null) {
                        processPacket();
                        System.out.println("Received packet.");
                    } else {
                        System.err.println("ERROR: no datagram available for " +
                                "reading from input socket.");
                        System.out.println(channel.getLocalAddress());
                    }
                } catch (IOException e) {
                    System.err.println("ERROR: could not receive packet from " +
                            "input socket");
                }

            }
        }
    }

    /**
     * Processes the received response packet currently stored in the inBuffer,
     * checking it for validity, then updating the routing table if needed.
     */
    private void processPacket() {

    }


//    public void waitForMessage() {
//
//        // Parameters for receiving packet
//        int messageSize = HEADER_BYTES + table.numEntries() * RIP_ENTRY_BYTES;
//        byte[] messageBuf = new byte[messageSize];
//
//        // Constructs a DatagramPacket for receiving packets of length messageSize
//        DatagramPacket receivedPacket = new DatagramPacket(messageBuf, messageSize);
//
//        while (true) {
//            try {
//                // Port number just hardcoded for testing purposes at this stage
//                receiveSocket = new DatagramSocket(1200);
//                receiveSocket.receive(receivedPacket);
//                checkValidity(receivedPacket);
//                displayPacketInfo(receivedPacket);
//            } catch (IOException exception) {
//                System.err.println("Error: Could not receive incoming packet on router ");
//            }
//        }
//
//    }

    public void checkValidity(DatagramPacket receivedPacket) {

        // TODO: Add logic for ignoring the packet. Not sure how to ignore/discard packet or log it yet
        boolean packetValid = false;
        boolean entryValid = false;

        try {
            address = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            Error.error("Could not resolve localhost address.");
        }

        // Check that the sender is a neighbour (directly connected) to current router
        // Check that the received packet is not sent from the router itself
        int receivedPacketPort = receivedPacket.getPort();
//        if (neighbours.containsValue(receivedPacketPort) && !receivedPacket.getAddress().equals(address)) {
//            packetValid = true;
//        }

        if (packetValid) {
            byte[] message = receivedPacket.getData();

            int i = 4;
            for (byte b : message) {

                // Check that the destination address is valid (unicast)
                byte[] destinationAddress = Arrays.copyOfRange(message, 0, i);
                /*I don't think this location is right but not sure where to locate
                metric number at this point (going off of Output.createMessage())*/
                byte[] metric = Arrays.copyOfRange(message, 0, i + 4);
                int metricNo = Util.byteArrayToInt(metric);

//                if (Util.byteArrayToInt(destinationAddress) == Util.byteArrayToInt(address)){
//                    // Check that the metric is between 1 and 16
//                    if (metricNo >=1 && metricNo <=16){
//                        entryValid = true;
//                        processUpdate(receivedPacket);
//                    }
//                }

                i += RIP_ENTRY_BYTES;

            }
        }

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
