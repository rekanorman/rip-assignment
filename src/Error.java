public class Error {
    /**
     * Prints the given error message to stderr and terminates the program.
     * @param errorMessage  The error message to print.
     */
    public static void error(String errorMessage) {
        System.err.println(errorMessage);
        System.exit(0);
    }
}
