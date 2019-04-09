import java.nio.ByteBuffer;

public class Util {
    /**
     * Converts a 32-bit int to an array of four bytes, using big-endian
     * byte order.
     * @param value The int to convert.
     * @return      An array of four bytes.
     */
    public static byte[] intToByteArray(int value) {
        return ByteBuffer.allocate(4).putInt(value).array();
    }

    /**
     * Converts an array of four bytes in big-endian ordering to a 32-bit int.
     * @param bytes     The array of four bytes to convert.
     * @return          The resulting int.
     */
    public static int byteArrayToInt(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getInt();
    }
}
