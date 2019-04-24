import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;

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
     * The router ID of the router receiving the updates
     */
    private int routerId;

    /**
     * The IP address to receive messages from, initialised to the
     * localhost address in the constructor.
     */
    private InetAddress destAddress;

    /**
     * The socket used for listening for incoming packets
     */
    private DatagramSocket receiveSocket;

    //TODO: Create multiple input sockets for each of the iput port numbers specified in config, all need to be listening for incoming packets at the same time

    /**
     * Routing table of receiving router
     */
    private RoutingTable table;

    /**
     * Output ports of the router
     */
    private ArrayList<int[]> outputs;

    private ArrayList<int[]> inputPorts;

    private HashMap<Integer, Integer> neighbours = table.getNeighbours(outputs);

    /**
     * Creates a new Input object for receiving update messages from neighbours
     * @param routerId       The ID of the router receiving the updates.
     * @param outputSocket  A datagram socket used to listen for the response packets.
     * @param outputs       A list of the router's neighbours, in the form
     *                          [inputPort, metric, routerId].
     * @param table         The routing table of router receiving the updates.
     */
    public Input(int routerId, DatagramSocket inputSocket, RoutingTable table) {
        this.routerId = routerId;
        this.inputSocket = inputSocket;
        this.table = table;

        try {
            this.destAddress = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            Error.error("Could not resolve localhost address.");
        }

        waitForMessage();

    }


    public void waitForMessage() {

        // Parameters for receiving packet
        int messageSize = HEADER_BYTES + table.numEntries() * RIP_ENTRY_BYTES;
        byte[] messageBuf = new byte[messageSize];

        // Constructs a DatagramPacket for receiving packets of length messageSize
        DatagramPacket receivedPacket = new DatagramPacket(messageBuf, messageSize);

        while (true) {
            try {
                receiveSocket.receive(receivedPacket);
                checkValidity(receivedPacket);
                displayPacketInfo(receivedPacket);
            } catch {
                System.err.println("Error: Could not receive incoming packet on router ")
            }
        }

    }

    public void checkValidity(DatagramPacket receivedPacket) {

        // TODO: Add logic for ignoring the packet. Not sure how to ignore/discard packet or log it yet
        bool packetValid = false;
        bool entryValid = false;

        // Check that the sender is a neighbour (directly connected) to current router
        // Check that the received packet is not sent from the router itself
        int receivedPacketPort = receivedPacket.getPort();
        if (neighbours.containsValue(receivedPacketPort) && !receivedPacket.getAddress().equals(InetAddress.getLocalHost())) {
            packetValid = true;
        }

        if (packetValid) {
            byte[] message = receivedPacket.getData();

            int i = 4;
            for (byte b : message) {

                // Check that the destination address is valid (unicast)
                byte[] destinationAddress = copyOfRange( byte[] message, 0, i);
                byte[] metric = copyOfRange( byte[] message, 0, i + 4)
                if (Util.byteArrayToInt( byte[] destinationAddress).equals(InetAddress.getLocalHost())){
                    // Check that the metric is between 1 and 16
                    int metricNo = Util.byteArrayToInt(byte[] metric);
                    if (metricNo >=1 && metricNo <=16){
                        entryValid = true;
                        processUpdate(receivedPacket);
                    }
                }

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
        System.out.println("Message: \n" + messsage);
    }
}
