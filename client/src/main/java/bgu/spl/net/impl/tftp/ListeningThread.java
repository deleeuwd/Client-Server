package bgu.spl.net.impl.tftp;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import bgu.spl.net.api.MessageEncoderDecoder;

public class ListeningThread implements Runnable {
    private final int MAX_DATA_SIZE = 512;
    private final BufferedInputStream in;
    private final MessageEncoderDecoder<byte[]> encdec;
    private final ClientHandler clientHandler;
    private ByteArrayOutputStream directoryData;
    private int currentBlockNumber;

    public ListeningThread(InputStream in, ClientHandler clientHandler) {
        this.directoryData = new ByteArrayOutputStream();
        this.currentBlockNumber = 1;
        this.encdec = new TftpEncoderDecoder();
        this.clientHandler = clientHandler;
        this.in = new BufferedInputStream(in);
    }

    @Override
    public void run() {
        int read;

        try {
            while (!Thread.currentThread().isInterrupted() && (read = in.read()) >= 0 && !clientHandler.shouldTerminate()) {
                byte[] nextMessage = encdec.decodeNextByte((byte) read);
                if (nextMessage != null) {
                    handlePacket(nextMessage);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handlePacket(byte[] packetData) {
        short opcode = (short) ((packetData[0] << 8) | (packetData[1] & 0xff));

        switch (opcode) {
            case 3:
                if (clientHandler.getRunningCommand() == Command.RRQ) {
                    handleRRQDATA(packetData);
                } else {
                    handleDIRQDATA(packetData);
                }
                break;
            case 4:
                handleACK(packetData);
                break;
            case 5:
                handleERROR(packetData);
                break;
            case 9:
                handleBCAST(packetData);
                break;
        }
    }

    private void handleBCAST(byte[] packetData) {
        byte deletedOrAdded = packetData[2];
        String fileName = new String(Arrays.copyOfRange(packetData, 3, packetData.length - 1));
        String operation = deletedOrAdded == 0 ? "del" : "add";

        System.out.println("BCAST " + operation + " " + fileName);
    }

    private void handleERROR(byte[] packetData) {
        short errorCode = (short) ((packetData[2] << 8) | (packetData[3] & 0xff));
        String errorMsg = new String(Arrays.copyOfRange(packetData, 4, packetData.length - 1));

        System.out.println("Error " + errorCode + " " + errorMsg);
        freeLock();
    }

    private void handleACK(byte[] packetData) {
        short blockNumber = (short) (((short) (packetData[2] & 0xFF) << 8) | (short) (packetData[3] & 0xFF));
        
        System.out.println("ACK " + blockNumber);
        if (blockNumber == 0) {
            if (clientHandler.getRunningCommand() == Command.WRQ) {
                try {
                    sendNextChunk();
                    return;
                } catch (IOException e) {
                    freeLock();
                }
            }

            if (clientHandler.getRunningCommand() == Command.DISC) {
                clientHandler.setShouldTerminate(true);
            }

            freeLock();
            return;
        }

        if (blockNumber == this.currentBlockNumber++) {
            try {
                if (sendNextChunk()) {
                    currentBlockNumber = 1;
                    clientHandler.getInputStream().close();
                    freeLock();
                }
            } catch (IOException e) {
                System.out.println("Error sending next data chunk.");
                freeLock();
            }
        }
    }

    private void handleRRQDATA(byte[] packetData) {
        short blockNumber = (short) (((short) (packetData[4] & 0xFF) << 8) | (short) (packetData[5] & 0xFF));

        if (blockNumber == currentBlockNumber++) {
            byte[] data = Arrays.copyOfRange(packetData, 6, packetData.length);

            try {
                clientHandler.getFileOutputStream().write(data);
                sendAckPacket(blockNumber);

                if (data.length < MAX_DATA_SIZE) {
                    System.out.println("RRQ " + clientHandler.getWorkingFile().getName() + " complete");
                    currentBlockNumber = 1;
                    clientHandler.getFileOutputStream().flush();
                    clientHandler.getFileOutputStream().close();
                    freeLock();
                }

            } catch (IOException e) {
                System.out.println("Error writing to file. Deleting it.");
                clientHandler.getWorkingFile().delete();
                freeLock();
            }
        }
    }

    private void handleDIRQDATA(byte[] packetData) {
        short blockNumber = (short) (((short) (packetData[4] & 0xFF) << 8) | (short) (packetData[5] & 0xFF));

        if (blockNumber == currentBlockNumber++) {
            byte[] data = Arrays.copyOfRange(packetData, 6, packetData.length);

            try {
                directoryData.write(data);
                sendAckPacket(blockNumber);
                if (data.length < MAX_DATA_SIZE) {
                    // print the file namees in the direcotryData byte array, separated by '0'
                    currentBlockNumber = 1;
                    printDirectoryData();
                    directoryData.reset();
                    freeLock();
                }

            } catch (IOException e) {
                freeLock();
            }
        }
    }

    private void printDirectoryData() {
        int start = 0; // Start index for each filename
        byte[] directoryDataBytes = directoryData.toByteArray();

        for (int i = 0; i < directoryDataBytes.length; i++) {
            if (directoryDataBytes[i] == 0) {
                // Create a string from the bytes of one filename
                String fileName = new String(directoryDataBytes, start, i - start);
                System.out.println(fileName);
                start = i + 1; // Move start to the byte after the null terminator
            }
        }
    }

    private void sendAckPacket(short blockNumber) {
        // Define the size of the ACK packet (4 bytes)
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.order(java.nio.ByteOrder.BIG_ENDIAN);

        buffer.putShort((short) 4); // Opcode
        buffer.putShort(blockNumber); // Block number

        clientHandler.writeToOutputStream(buffer.array());
    }

    private boolean sendNextChunk() throws IOException {
        byte[] buffer = new byte[MAX_DATA_SIZE];
        int bytesRead;
    
        bytesRead = clientHandler.getInputStream().read(buffer); // `inputStream` is now of type InputStream
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
        buffer.putShort((short) 3); // Opcode
        buffer.putShort((short)chunk.length); // Data length (in bytes)
        buffer.putShort(blockNumber); // Block number
        buffer.put(chunk); // Data

        clientHandler.writeToOutputStream(buffer.array());
    }

    private void freeLock() {
        synchronized (clientHandler.getLock()) {
            clientHandler.getLock().notify();
        }
    }
}
