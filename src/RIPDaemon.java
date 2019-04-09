import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.ArrayList;

public class RIPDaemon {
    /**
     * The metric value used to represent infinity.
     */
    public static final int INFINITY = 16;

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


    private RIPDaemon(int routerId, ArrayList<Integer> inputPorts,
                     ArrayList<int[]> outputs) {
        this.id = routerId;

        // Initialise the routing table and display the initial contents.
        this.table = new RoutingTable(outputs);
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
    }

    /**
     * Enter an infinite loop to wait for events and handle them as needed.
     */
    private void run() {
        // TODO: implement
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            Error.error("Usage: java RIPDaemon <config-filename>");
        }

        ConfigFileParser parser = new ConfigFileParser(args[0]);
        parser.parseFile();

        RIPDaemon daemon = new RIPDaemon(parser.getRouterId(),
                parser.getInputPorts(), parser.getOutputs());

        daemon.run();
    }
}
