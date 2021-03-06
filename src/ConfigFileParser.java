import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

public class ConfigFileParser {
    /**
     * Any line in the config file starting with this string is a comment.
     */
    private static final String COMMENT_STRING = "//";

    /**
     * The name of the config file being parsed.
     */
    private String filename;

    /**
     * The values read from the config file.
     */
    private int routerId;
    private ArrayList<Integer> inputPorts = new ArrayList<>();
    private ArrayList<int[]> outputs = new ArrayList<>();
    private int outputPort;
    private int updatePeriod;

    /**
     * Flags to keep track of whether each parameter has been read yet,
     * to detect duplicate definitions of the same parameter in the config file.
     */
    private boolean routerIdSet = false;
    private boolean inputPortsSet = false;
    private boolean outputsSet = false;
    private boolean outputPortSet = false;
    private boolean updatePeriodSet = false;

    /**
     * Create a new ConfigFileParser to parse the given file.
     * @param filename The name of the config file to parse.
     */
    public ConfigFileParser(String filename) {
        this.filename = filename;
    }

    /**
     * Get the router ID. Should be called after parsing the file.
     * @return  The router ID.
     */
    public int getRouterId() {
        return routerId;
    }

    /**
     * Get the list of input ports. Should be called after parsing the file.
     * @return  List of input ports.
     */
    public ArrayList<Integer> getInputPorts() {
        return inputPorts;
    }

    /**
     * Get the list of outputs in the form [inputPort, metric, routerId].
     * Should be called after parsing the file.
     * @return  List of outputs.
     */
    public ArrayList<int[]> getOutputs() {
        return outputs;
    }

    /**
     * Get the output port number. Should be called after parsing the file.
     * @return  Output port number.
     */
    public int getOutputPort() {
        return outputPort;
    }

    /**
     * Get the update period. If it was not specified in the config file,
     * returns 0.
     * Should be called after parsing the file.
     * @return  Update period if it was specified, otherwise 0.
     */
    public int getUpdatePeriod() {
        if (updatePeriodSet) {
            return updatePeriod;
        } else {
            return 0;
        }
    }

    /**
     * Tries to parse the given config file. If the file doesn't exist or has
     * an invalid format, prints an error message and terminates the program.
     */
    public void parseFile() {
        try {
            Scanner sc = new Scanner(new File(this.filename));
            while (sc.hasNextLine()) {
                parseLine(sc.nextLine());
            }
            sc.close();

        } catch (FileNotFoundException e) {
            Error.error("Given configuration file doesn't exist.");
        }

        // Check that the config file specified all the mandatory parameters
        // (the update-period parameter is optional).
        if (!routerIdSet) {
            Error.error("Invalid config file: missing router-id.");
        }

        if (!inputPortsSet) {
            Error.error("Invalid config file: missing input-ports.");
        }

        if (!outputsSet) {
            Error.error("Invalid config file: missing outputs.");
        }

        if (!outputPortSet) {
            Error.error("Invalid config file: missing output port.");
        }

        // Check that the neighbours' port numbers are all different from this
        // router's input port numbers and output port number, and that the
        // neighbours' router IDs are all different from this router's ID.
        for (int[] output : this.outputs) {
            if (this.inputPorts.contains(output[0])) {
                Error.error("Invalid config file: neighbours' port " +
                        "numbers must be different from input port numbers.");
            }

            if (output[0] == this.outputPort) {
                Error.error("Invalid config file: neighbours' port " +
                        "numbers must be different from this router's output " +
                        "port number.");
            }

            if (this.routerId == output[2]) {
                Error.error("Invalid config file: output router " +
                        "IDs must be different from router-id.");
            }
        }

        // Check that this router's output port number is different from its
        // input port numbers.
        if (this.inputPorts.contains(this.outputPort)) {
            Error.error("Invalid config file: output port number " +
                    "must be different from input port numbers.");
        }
    }

    /**
     * Processes the given line of the config file, setting the appropriate
     * values if the line is valid, of printing an error message if it is
     * invalid. Ignores empty lines and comments.
     * @param line  The line of the config file to process.
     */
    private void parseLine(String line) {
        // Ignore empty lines and comments.
        if (line.trim().isEmpty() || line.trim().startsWith(COMMENT_STRING)) {
            return;
        }

        String[] tokens = line.split("\\s+");
        String parameter = tokens[0];

        if (parameter.equals("router-id")) {
            if (this.routerIdSet) {
                Error.error("Invalid config file: router-id defined " +
                        "more than once.");
            } else {
                parseRouterId(Arrays.copyOfRange(tokens,1, tokens.length));
            }

        } else if (parameter.equals("input-ports")) {
            if (this.inputPortsSet) {
                Error.error("Invalid config file: input-ports defined " +
                        "more than once.");
            } else {
                parseInputPorts(Arrays.copyOfRange(tokens,1, tokens.length));
            }

        } else if (parameter.equals("outputs")) {
            if (this.outputsSet) {
                Error.error("Invalid config file: outputs defined " +
                        "more than once.");
            } else {
                parseOutputs(Arrays.copyOfRange(tokens, 1, tokens.length));
            }

        } else if (parameter.equals("output-port")) {
            if (this.outputPortSet) {
                Error.error("Invalid config file: output port defined " +
                        "more than once.");
            } else {
                parseOutputPort(Arrays.copyOfRange(tokens, 1, tokens.length));
            }

        } else if (parameter.equals("update-period")) {
            if (this.updatePeriodSet) {
                Error.error("Invalid config file: update-period defined " +
                        "more than once.");
            } else {
                parseUpdatePeriod(Arrays.copyOfRange(tokens, 1, tokens.length));
            }

        } else {
            Error.error(String.format(
                    "Invalid config file: %s is not a valid parameter",
                    parameter));
        }
    }

    /**
     * Takes the list of the tokens following "router-id" in a line of the
     * config file and extracts the id, or prints an error message if the
     * contents of the line are not valid.
     * @param tokens  The tokens from the line in the config file.
     */
    private void parseRouterId(String[] tokens) {
        if (tokens.length != 1) {
            routerIdError();
        }

        try {
            int id = Integer.parseInt(tokens[0]);
            if (isValidRouterID(id)) {
                this.routerId = id;
            } else {
                routerIdError();
            }

        } catch (NumberFormatException e) {
            routerIdError();
        }

        this.routerIdSet = true;
    }

    /**
     * Takes the list of the tokens following "input-ports" in a line of the
     * config file and extracts the input port numbers, or prints an error
     * message if the contents of the line are not valid.
     * @param tokens  The tokens from the line in the config file.
     */
    private void parseInputPorts(String[] tokens) {
        if (tokens.length == 0) {
            inputPortsError();
        }

        for (String token : tokens) {
            try {
                int portNo = Integer.parseInt(token);
                if (isValidPortNo(portNo)) {
                    this.inputPorts.add(portNo);
                } else {
                    inputPortsError();
                }

            } catch (NumberFormatException e) {
                inputPortsError();
            }
        }

        this.inputPortsSet = true;
    }

    /**
     * Takes the list of the tokens following "outputs" in a line of the
     * config file and extracts the information about the outputs.
     * Prints an error message if the contents of the line are not valid.
     * @param tokens  The tokens from the line in the config file.
     */
    private void parseOutputs(String[] tokens) {
        if (tokens.length == 0) {
            outputsError();
        }

        for (String token : tokens) {
            String[] outputTokens = token.split("-");
            if (outputTokens.length != 3) {
                outputsError();
            }

            // Create an array containing the three values which specify an
            // output as integers.
            int[] outputValues = new int[3];
            for (int i = 0; i < 3; i++) {
                try {
                    int outputValue = Integer.parseInt(outputTokens[i]);
                    outputValues[i] = outputValue;
                } catch (NumberFormatException e) {
                    outputsError();
                }
            }

            // Check that input port of neighbour is in the right range.
            int neighbourInputPort = outputValues[0];
            if (!isValidPortNo(neighbourInputPort)) {
                Error.error(String.format("Invalid config file: " +
                        "port numbers of outputs must be " +
                        "between %d and %d.",
                        RIPDaemon.MIN_PORT_NO,
                        RIPDaemon.MAX_PORT_NO));
            }

            // Check that router ID of neighbour is in the right range.
            int neighbourId = outputValues[2];
            if (!isValidRouterID(neighbourId)) {
                Error.error(String.format("Invalid confing file: " +
                        "router IDs of outputs must be " +
                        "between %d and %d.",
                        RIPDaemon.MIN_ROUTER_ID,
                        RIPDaemon.MAX_ROUTER_ID));
            }

            // If the output values are valid, add them to the list of outputs.
            this.outputs.add(outputValues);
        }

        this.outputsSet = true;
    }

    /**
     * Takes the list of the tokens following "output-port" in a line of the
     * config file and extracts the output port number, or prints an error
     * message if the contents of the line are not valid.
     * @param tokens  The tokens from the line in the config file.
     */
    private void parseOutputPort(String[] tokens) {
        if (tokens.length != 1) {
            outputPortError();
        }

        try {
            int outputPort = Integer.parseInt(tokens[0]);
            if (isValidPortNo(outputPort)) {
                this.outputPort = outputPort;
            } else {
                outputPortError();
            }

        } catch (NumberFormatException e) {
            outputPortError();
        }

        this.outputPortSet = true;
    }

    /**
     * Takes the list of the tokens following "update-period" in a line of the
     * config file and extracts the update period.
     * Prints an error message if the contents of the line are not valid.
     * @param tokens  The tokens from the line in the config file.
     */
    private void parseUpdatePeriod(String[] tokens) {
        if (tokens.length != 1) {
            updatePeriodError();
        }

        try {
            int period = Integer.parseInt(tokens[0]);
            if (period > 0) {
                this.updatePeriod = period;
            } else {
                updatePeriodError();
            }

        } catch (NumberFormatException e) {
            updatePeriodError();
        }

        this.updatePeriodSet = true;
    }

    private boolean isValidRouterID(int id) {
        return id >= RIPDaemon.MIN_ROUTER_ID && id <= RIPDaemon.MAX_ROUTER_ID;
    }

    private boolean isValidPortNo(int portNo) {
        return portNo >= RIPDaemon.MIN_PORT_NO && portNo <= RIPDaemon.MAX_PORT_NO;
    }

    /**
     * Prints an error message explaining the usage of the router-id parameter
     * and terminates the program.
     */
    private void routerIdError() {
        Error.error(String.format(
                "Invalid config file: router-id must be " +
                        "a single integer between %d and %d.",
                RIPDaemon.MIN_ROUTER_ID,
                RIPDaemon.MAX_ROUTER_ID));
    }

    /**
     * Prints an error message explaining the usage of the input-ports parameter
     * and terminates the program.
     */
    private void inputPortsError() {
        Error.error(String.format(
                "Invalid config file: input-ports must be " +
                        "a non-empty list of integers between %d and %d, " +
                        "separated by spaces.",
                RIPDaemon.MIN_PORT_NO,
                RIPDaemon.MAX_PORT_NO));
    }

    /**
     * Prints an error message explaining the usage of the outputs parameter
     * and terminates the program.
     */
    private void outputsError() {
        Error.error("Invalid config file: outputs must be a non-empty " +
                "space-separated list of entries in the form " +
                "inputPort-metric-routerId, where each value is an integer.");
    }

    /**
     * Prints an error message explaining the usage of the output-port parameter
     * and terminates the program.
     */
    private void outputPortError() {
        Error.error(String.format(
                "Invalid config file: output-port must be " +
                        "a single integer between %d and %d.",
                RIPDaemon.MIN_PORT_NO,
                RIPDaemon.MAX_PORT_NO));
    }

    /**
     * Prints an error message explaining the usage of the update-period parameter
     * and terminates the program.
     */
    private void updatePeriodError() {
        Error.error("Invalid config file: update-period must be a " +
                "single positive integer.");
    }
}
