import java.util.Arrays;

public class RIPDaemon {

    public static void main(String[] args) {
        if (args.length != 1) {
            Error.error("Usage: java RIPDaemon <config-filename>");
        }

        ConfigFileParser parser = new ConfigFileParser(args[0]);
        parser.parseFile();

        // Check that parser is working
        System.out.println(parser.getRouterId());
        System.out.println(parser.getInputPorts());
        for (int[] output : parser.getOutputs()) {
            System.out.println(Arrays.toString(output));
        }
    }
}
