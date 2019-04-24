import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;

public class Output {
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
     * The value to put in the command field of a response message.
     */
    private static final byte RESPONSE_COMMAND = 2;

    /**
     * The value to put in the version field of the response messages.
     */
    private static final byte RIP_VERSION = 2;

    /**
     * The router ID of the router sending the updates.
     */
    private int routerId;

    /**
     * The IP address to send response messages to, initialised to the
     * localhost address in the constructor.
     */
    private InetAddress destAddress;

    /**
     * The socket used for sending response packets.
     */
    private DatagramSocket outputSocket;

    /**
     * The routing table of the router sending the updates.
     */
    private RoutingTable table;

    /**
     * The neighbours to which response messages are sent. Maps the router ID
     * of each neighbour to the port number which messages to this neighbour
     * should be sent to.
     */
    //private HashMap<Integer, Integer> neighbours = new HashMap<>();


    /**
     * Creates a new Output object for sending response messages to neighbours.
     * @param routerId      The ID of the router sending the updates.
     * @param outputSocket  A datagram socket used to send the response packets.
     * @param outputs       A list of the router's neighbours, in the form
     *                          [inputPort, metric, routerId].
     * @param table         The routing table of router sending the updates.
     */
    public Output(int routerId, DatagramSocket outputSocket, ArrayList<int[]> outputs,
                  RoutingTable table) {
        this.routerId = routerId;
        this.outputSocket = outputSocket;
        this.table = table;

        try {
            this.destAddress = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            Error.error("Could not resolve localhost address.");
        }

        // Initialise the neighbours map with the given outputs information.
        table.getNeighbours(outputs);

        // Display the neighbours to check they have been initialised correctly.
        System.out.println("Neighbours:\n");
        for (int id : neighbours.keySet()) {
            int portNo = neighbours.get(id);
            System.out.println(String.format("Id: %-5d Input port: %-5d",
                    id, portNo));
        }
        System.out.println();
    }

    /**
     * Sends a response message to each neighbour containing the current
     * information in the routing table. Split horizon with poison reverse is
     * used, so a separate message is prepared for each neighbour.
     */
    public void sendUpdates() {
        for (int id : neighbours.keySet()) {
            int portNo = neighbours.get(id);
            sendUpdate(id, portNo);
            System.out.println(String.format("Update sent to router %d", id));
        }
    }

    /**
     * Send a response message to the neighbour with given ID and port number.
     * Split horizon with poison reverse is used, so any entries in the routing
     * table which list the neighbour as their next hop will have their metric
     * set to infinity.
     * @param neighbourId   Router ID of the neighbour to send the update to.
     * @param portNo        Port number to send the response packet to.
     */
    private void sendUpdate(int neighbourId, int portNo) {
        byte[] responseMessage = createResponseMessage(neighbourId);

        DatagramPacket responsePacket = new DatagramPacket(responseMessage,
                responseMessage.length, this.destAddress, portNo);

        try {
            outputSocket.send(responsePacket);
        } catch (IOException e) {
            System.err.println(String.format("ERROR: could not send " +
                    "response message to router %d.\n", neighbourId));
        }
    }

    /**
     * Creates a response message represented as a byte array to be sent to
     * the given neighbour, based on the information in the routing table.
     * @param neighbourId   Router ID of neighbour the message is being sent to.
     * @return  A response message in the form of a byte array.
     */
    private byte[] createResponseMessage(int neighbourId) {
        // Create an array of the right size to hold the message.
        int messageSize = HEADER_BYTES + table.numEntries() * RIP_ENTRY_BYTES;
        byte[] message = new byte[messageSize];

        // Fill in the header fields.
        message[0] = RESPONSE_COMMAND;
        message[1] = RIP_VERSION;

        byte[] sourceIdBytes = Util.intToByteArray(this.routerId);
        message[2] = sourceIdBytes[2];      // Only send lower two bytes of ID.
        message[3] = sourceIdBytes[3];

        // Add an RIP entry for each entry in the routing table, setting the
        // metric to infinity if the next hop is the neighbour itself.
        int i = 4;      // Starting index of current entry in the message.
        for (RoutingTableEntry entry : table.getEntries()) {
            // Add destination router ID as 32-bit field.
            byte[] destIdBytes = Util.intToByteArray(entry.getDestId());
            // Copies destIdBytes array from index 0 to 4 in the message array
            System.arraycopy(destIdBytes, 0, message, i, 4);

            // Add metric as 32-bit field.
            int metric = entry.getMetric();
            if (entry.getNextHop() == neighbourId) {
                metric = RIPDaemon.INFINITY;
            }
            byte[] metricBytes = Util.intToByteArray(metric);
            System.arraycopy(metricBytes, 0, message, i + 4, 4);

            i += RIP_ENTRY_BYTES;
        }

        return message;
    }
}
