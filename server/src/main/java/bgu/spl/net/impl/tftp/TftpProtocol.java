package bgu.spl.net.impl.tftp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;

public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {
    private boolean shouldTerminate = false;
    private String userName;
    private int connectionId;
    private int currentBlockNumber;
    private InputStream inputStream;
    private FileOutputStream fileOutputStream;
    private File workingFile;
    private Connections<byte[]> connections;
    private final String PATH = "Files/";
    private final int MAX_DATA_SIZE = 512;


    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
    }

    @Override
    public void process(byte[] message) {
        if (message == null) {
            end();
            return;
        }

        if (message.length < 2){
            throw new UnsupportedOperationException("No operation number");
        }

        // Extract the opcode from the message
        short opcode = (short) (((short) message[0]) << 8 | (short) message[1]);
        // If client is not connected and sending a command that is not LOGRQ
        if (userName == null && opcode != 7) {
            // Send ERROR packet to the client indicating that the username is not connected
            sendErrorPacket((short) 6, "You must login first.");
            return;
        }

        switch (opcode) {
          case 1:
            handleRRQ(message);
            break;
          case 2:
            handleWRQ(message);
            break;
          case 3:
            handleDATA(message);
            break;
          case 4:
            handleACK(message);
            break;
          case 6:
            handleDIRQ(message);
            break;
          case 7:
            handleLOGRQ(message);
            break;
          case 8:
            handleDELRQ(message);
            break;
          case 10:
            handleDISC();
            break;
          default:
            sendErrorPacket((short) 4, "Unsupported opcode.");
            break;
      }
    }

    private void end() {
        if (userName != null) {
            TftfUserManager.getInstance().removeUsername(userName);
        }

        shouldTerminate = true;
        connections.disconnect(connectionId);
        try {
            if (fileOutputStream != null) {
                fileOutputStream.close();
            }

            if (inputStream != null) {
                inputStream.close();
            }

        } catch (IOException e) {
            System.out.println("Error closing stream for connectionId: " + connectionId);
        }
    }

    private void handleDATA(byte[] message) {
        short blockNumber = (short) (((short) (message[4] & 0xFF) << 8) | (short) (message[5] & 0xFF));

        if (blockNumber == currentBlockNumber++) {
            System.out.println("here");
            byte[] data = Arrays.copyOfRange(message, 6, message.length);

            try {
                this.fileOutputStream.write(data);
                sendAckPacket(blockNumber);

                if (data.length < MAX_DATA_SIZE) {
                    // End of file, close outputStream and send BCAST packet
                    this.fileOutputStream.flush();
                    this.fileOutputStream.close();
                    sendBCASTPacket(workingFile.getName(), true);
                }

            } catch (IOException e) {
                // Send ERROR packet to the client indicating a file transfer error
                sendErrorPacket((short) 2, "File transfer error.");
            }
        }
    }

    private void handleACK(byte[] message) {
        short blockNumber = (short) (((short) (message[2] & 0xFF) << 8) | (short) (message[3] & 0xFF));

        if (blockNumber == this.currentBlockNumber++) {
            try {
                if (sendNextChunk()) {
                    // End of file, close inputStream and send BCAST packet
                    this.inputStream.close();
                }
            } catch (IOException e) {
                // Send ERROR packet to the client indicating a file transfer error
                sendErrorPacket((short) 2, "File transfer error.");
            }
        }
    }

    private void handleDISC() {
        sendAckPacket((short) 0);
        end();
    }

    private void handleDELRQ(byte[] message) {
        // convert message to filename starting from the 3rd byte
        String fileName = new String(message, 2, message.length - 3, StandardCharsets.UTF_8);
        File file= getFile(fileName);

        if (file == null) {
            return;
        }

        if (file.delete()) {
            sendAckPacket((short) 0);
            sendBCASTPacket(fileName, false);
        } else {
            sendErrorPacket((short) 2, "Error while deleting file. File was not deleted.");
        }
    }

    private void handleLOGRQ(byte[] message) {
        // Extract username from the message, without the opcode and without the null terminator
        String userName = new String(message, 2, message.length - 3, StandardCharsets.UTF_8);
        TftfUserManager usernameManager = TftfUserManager.getInstance();

        if (usernameManager.isUsernameExists(userName)) {
            // Send ERROR packet to the client indicating that the username is already in use
            sendErrorPacket((short) 7, "Login username already connected. Please use a different username.");
        } else {
            // Add username to the nickname manager
            usernameManager.addUsername(userName, connectionId);
            this.userName = userName;
            sendAckPacket((short)0);
        }
    }

    private void handleDIRQ(byte[] message) {
        File dir = new File(PATH);
        if (!dir.isDirectory()) {
            // Send ERROR packet to the client indicating that the directory does not exist
            sendErrorPacket((short) 1, "Directory not found.");
            return;
        }

        // Use ByteArrayOutputStream to collect directory contents
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            for (File file : dir.listFiles()) {
                if (file.isFile()) { // Check if it's indeed a file
                    baos.write(file.getName().getBytes(StandardCharsets.UTF_8));
                    baos.write(0); // Separate filenames with a null byte
                }
            }
    
            // Convert ByteArrayOutputStream to byte array and then to ByteArrayInputStream
            this.inputStream = new ByteArrayInputStream(baos.toByteArray());
            this.currentBlockNumber = 1;
            sendNextChunk();
        } catch(IOException e) {
            // Send ERROR packet to the client indicating a file transfer error
            sendErrorPacket((short) 2, "File transfer error.");
        }

    }

    private void handleWRQ(byte[] message) {
        String fileName = new String(message, 2, message.length - 3, StandardCharsets.UTF_8);
        File file = new File(PATH + fileName);

        if (file.exists()) {
            // Send ERROR packet to the client indicating that the file already exists
            sendErrorPacket((short) 5, "File already exists.");
            return;
        }

        // File exists, send Ack packet.
        try {
            file.createNewFile();

            this.workingFile = file;
            this.currentBlockNumber = 1;
            this.fileOutputStream = new FileOutputStream(file);
            // Read to start recieving data
            sendAckPacket((short) 0);
        } catch (IOException e) {
            // Send ERROR packet to the client indicating a file transfer error
            sendErrorPacket((short) 2, "File transfer error.");
        }
    }

    private void handleRRQ(byte[] message) {
        String fileName = new String(message, 2, message.length - 3, StandardCharsets.UTF_8);
        File file = getFile(fileName);
        
        if (file == null) {
            return;
        }

        // Setting the working file to the file requested by the client
        this.workingFile = file;
        this.currentBlockNumber = 1;
        
        try {
            this.inputStream = new FileInputStream(file);
            sendNextChunk();
        } catch (IOException e) {
            // Send ERROR packet to the client indicating a file transfer error
            sendErrorPacket((short) 2, "File transfer error.");
        }
    }

    // True if eof, false otherwise
    private boolean sendNextChunk() throws IOException {
        byte[] buffer = new byte[MAX_DATA_SIZE];
        int bytesRead;
    
        bytesRead = this.inputStream.read(buffer); // `inputStream` is now of type InputStream
        if (bytesRead == -1) {
            // End of input stream
            return true;
        }
    
        byte[] chunk = Arrays.copyOf(buffer, bytesRead);
        sendDataPacket((short) this.currentBlockNumber, chunk);
    
        return false;
    }

    private void sendDataPacket(short blockNumber, byte[] chunk) {
        ByteBuffer buffer = ByteBuffer.allocate(6 + chunk.length); // 6 bytes for opcode, block number and data length
        buffer.order(java.nio.ByteOrder.BIG_ENDIAN);
        System.out.println("Chunk length: " + chunk.length);
        buffer.putShort((short) 3); // Opcode
        buffer.putShort((short)chunk.length); // Data length (in bytes)
        buffer.putShort(blockNumber); // Block number
        buffer.put(chunk); // Data

        connections.send(connectionId, buffer.array());
    }

    private void sendBCASTPacket(String fileName, boolean isAdded) {
        ByteBuffer buffer = ByteBuffer.allocate(3 + fileName.length() + 1);
        buffer.order(java.nio.ByteOrder.BIG_ENDIAN);

        buffer.putShort((short) 9); // Opcode
        buffer.put((byte) (isAdded ? 1 : 0)); // Add/Remove
        buffer.put(fileName.getBytes(StandardCharsets.UTF_8)); // Filename
        buffer.put((byte) 0); // Null terminator

        for (Integer connection : TftfUserManager.getInstance().getAllConnections()) {
            System.out.println("Sending BCAST message to connectionId: " + connection);
            connections.send(connection, buffer.array());
        }
    }

    private void sendErrorPacket(short errorCode, String errorMsg) {
        // Calculate the size of the byte buffer
        // Opcode (2 bytes) + ErrorCode (2 bytes) + errorMsg bytes + 1 byte for the terminating 0
        int size = 2 + 2 + errorMsg.getBytes(StandardCharsets.UTF_8).length + 1;
        ByteBuffer buffer = ByteBuffer.allocate(size);
        
        // Ensure the buffer uses big endian byte order
        buffer.order(java.nio.ByteOrder.BIG_ENDIAN);
        // Opcode for error is 5
        buffer.putShort((short) 5);   
        // Put the error code
        buffer.putShort(errorCode);
        // Put the error message as bytes and add a 0 byte at the end for termination
        buffer.put(errorMsg.getBytes(StandardCharsets.UTF_8));
        buffer.put((byte) 0);
        
        // Convert the ByteBuffer to a byte array

        connections.send(connectionId, buffer.array());
    }

    private void sendAckPacket(short blockNumber) {
        // Define the size of the ACK packet (4 bytes)
        System.out.println("ACK");
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.order(java.nio.ByteOrder.BIG_ENDIAN);

        buffer.putShort((short) 4); // Opcode
        buffer.putShort(blockNumber); // Block number

        connections.send(connectionId, buffer.array());
    }

    private File getFile(String fileName) {
        File file = new File(PATH + fileName);
        System.out.println(file.getAbsolutePath());
        System.out.println(file.exists());
        if (!file.exists() || !file.isFile()) {
            // Send ERROR packet to the client indicating that the file does not exist
            sendErrorPacket((short) 1, "File not found.");
            return null;
        }

        return file;
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }     
}
