import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;

public class Output {
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
    private HashMap<Integer, Integer> neighbours = new HashMap<>();

    /**
     * Creates a new Output object for sending response messages to neighbours.
     * @param routerId      The ID of the router sending the updates.
     * @param outputPortNo  The port number to use for the output socket.
     * @param outputs       A list of the router's neighbours, in the form
     *                          [inputPort, metric, routerId].
     * @param table         The routing table of router sending the updates.
     */
    public Output(int routerId, int outputPortNo, ArrayList<int[]> outputs,
                  RoutingTable table) {
        this.routerId = routerId;
        this.table = table;

        try {
            this.outputSocket = new DatagramSocket(outputPortNo);
        } catch (SocketException e) {
            e.printStackTrace();
            Error.error(String.format("ERROR: could not open output " +
                    "socket with port number %d.", outputPortNo));
        }

        try {
            this.destAddress = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            Error.error("ERROR: could not resolve localhost address.");
        }

        // Initialise the neighbours map with the given outputs information.
        for (int[] neighbour : outputs) {
            int id = neighbour[2];
            int portNo = neighbour[0];
            this.neighbours.put(id, portNo);
        }
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
        }
    }

    /**
     * Send a response message to the neighbour with given ID and port number.
     * @param neighbourId   Router ID of the neighbour to send the update to.
     * @param portNo        Port number to send the response packet to.
     */
    private void sendUpdate(int neighbourId, int portNo) {
        byte[] responseMessage = createResponseMessage(neighbourId);

        if (responseMessage.length > RIPDaemon.MAX_RESPONSE_PACKET_SIZE) {
            System.err.println(String.format("ERROR: response message to " +
                    "router %d too large, packet not sent.", neighbourId));
            return;
        }

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
     * Split horizon with poison reverse is used, so any entries in the routing
     * table which list the neighbour as their next hop will have their metric
     * set to infinity.
     * @param neighbourId   Router ID of neighbour the message is being sent to.
     * @return  A response message in the form of a byte array.
     */
    private byte[] createResponseMessage(int neighbourId) {
        // Create an buffer of the right size to hold the message.
        int messageSize = RIPDaemon.HEADER_BYTES + table.numEntries()
                            * RIPDaemon.RIP_ENTRY_BYTES;
        ByteBuffer message = ByteBuffer.allocate(messageSize);

        // Fill in the header fields.
        message.put(RIPDaemon.RESPONSE_COMMAND);
        message.put(RIPDaemon.RIP_VERSION);
        message.putShort((short) this.routerId);

        // Add an RIP entry for each entry in the routing table, setting the
        // metric to infinity if the next hop is the neighbour itself.
        for (int destId : table.allDestIds()) {
            message.putInt(destId);

            int metric = table.getMetric(destId);
            if (table.getNextHop(destId) == neighbourId) {
                metric = RIPDaemon.INFINITY;
            }
            message.putInt((metric));
        }

        return message.array();
    }
}
