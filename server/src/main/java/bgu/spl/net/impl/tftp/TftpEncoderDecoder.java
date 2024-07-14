package bgu.spl.net.impl.tftp;
import java.io.ByteArrayOutputStream;
import bgu.spl.net.api.MessageEncoderDecoder;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    private ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private int opcode = -1;
    private Integer packetSize = null; // Use an Integer to allow null-checking

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        buffer.write(nextByte);


        if (buffer.size() == 2) { // Opcode is determined after receiving the first two bytes
            opcode = ((buffer.toByteArray()[0] & 0xFF) << 8) | (buffer.toByteArray()[1] & 0xFF);
            System.out.println("Opcode: " + opcode);
        }

        switch (opcode) {
            case 3: // DATA packets require special handling to determine packet size
                if (buffer.size() == 4 && packetSize == null) { // Only calculate packetSize once
                    packetSize = ((buffer.toByteArray()[2] & 0xFF) << 8) | (buffer.toByteArray()[3] & 0xFF); // Include opcode and block number in size
                    System.out.println("Packet size: " + packetSize);
                }

                if (packetSize != null && buffer.size() >= packetSize + 6) { // Include opcode and block number in size check
                    return popBytes();
                }

                break;
            case 4: // ACK
                if (buffer.size() == 4) {
                    return popBytes();
                }
                break;
            case 6: // DIRQ
            case 10: // DISC
                if (buffer.size() == 2) { // For packets that are complete with just the opcode
                    return popBytes();
                }

                break;
            default: // Handle other opcodes or a default case if necessary
                if (nextByte == 0 && buffer.size() > 3) { // Generic completion condition for variable-length packets not covered above
                    return popBytes();
                }

                break;
        }
        
        return null; // Packet is not complete yet
    }


    @Override
    public byte[] encode(byte[] message) {
        return message;
    }

    private byte[] popBytes() {
        byte[] packet = buffer.toByteArray();
        buffer.reset(); // Clear the buffer for the next message
        opcode = -1; // Reset opcode for the next message
        packetSize = null; // Reset packet size for the next message

        return packet;
    }
}
