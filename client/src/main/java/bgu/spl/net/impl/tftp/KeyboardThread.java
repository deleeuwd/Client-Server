package bgu.spl.net.impl.tftp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Scanner;

public class KeyboardThread implements Runnable {
    private final ClientHandler clientHandler;

    public KeyboardThread(ClientHandler handler) {
        this.clientHandler = handler;
    }

    @Override
    public void run() {
        Scanner scanner = new Scanner(System.in);
        String command;

        try {
            while (!clientHandler.shouldTerminate()) {
                command = scanner.nextLine();
                byte[] bytes = parseCommand(command);

                if (bytes != null) {
                    clientHandler.writeToOutputStream(bytes);
                    synchronized (clientHandler.getLock()) {
                        clientHandler.getLock().wait();
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private byte[] parseCommand(String command) throws IOException {
        String[] parts = command.split(" ");

        if (parts.length < 1) {
            System.out.println("Invalid command");
            return null;
        }

        switch (parts[0]) {
            case "RRQ":
                return handleRRQ(parts);
            case "WRQ":
                return handleWRQ(parts);
            case "LOGRQ":
                return handleLOGRQ(parts);
            case "DIRQ":
                return handleDIRQ(parts);
            case "DISC":
                return handleDISC(parts);
            case "DELRQ":
                return handleDELRQ(parts);
            default:
                System.out.println("Invalid command");
                return null;
        }
    }

    private byte[] handleDISC(String[] parts) {
        clientHandler.setRunningCommand(Command.DISC);
        ByteBuffer buffer = ByteBuffer.allocate(2);
        buffer.order(java.nio.ByteOrder.BIG_ENDIAN);

        buffer.putShort((short) Command.DISC.getValue());

        return buffer.array();
    }

    private byte[] handleDIRQ(String[] parts) {
        clientHandler.setRunningCommand(Command.DIRQ);
        ByteBuffer buffer = ByteBuffer.allocate(2);
        buffer.order(java.nio.ByteOrder.BIG_ENDIAN);
        buffer.putShort((short) Command.DIRQ.getValue());

        return buffer.array();
    }

    private byte[] handleDELRQ(String[] parts) {
        clientHandler.setRunningCommand(Command.DELRQ);
        if (parts.length < 2) {
            System.out.println("DELRQ command requires a filename");
            return null;
        }

        ByteBuffer buffer = ByteBuffer.allocate(2 + parts[1].length() + 1);
        buffer.order(java.nio.ByteOrder.BIG_ENDIAN);
        buffer.putShort((short) Command.DELRQ.getValue());
        buffer.put(parts[1].getBytes());
        buffer.put((byte) 0);

        return buffer.array();
    }

    private byte[] handleLOGRQ(String[] parts) {
        clientHandler.setRunningCommand(Command.LOGRQ);
        if (parts.length < 2) {
            System.out.println("LOGRQ command requires a username");
            return null;
        }

        ByteBuffer buffer = ByteBuffer.allocate(2 + parts[1].length() + 1);
        buffer.order(java.nio.ByteOrder.BIG_ENDIAN);

        buffer.putShort((short) Command.LOGRQ.getValue());
        buffer.put(parts[1].getBytes());
        buffer.put((byte) 0);

        return buffer.array();
    }

    private byte[] handleWRQ(String[] parts) throws IOException {
        clientHandler.setRunningCommand(Command.WRQ);
        if (parts.length < 2) {
            System.out.println("WRQ command requires a filename");
            return null;
        }

        // Check if file exists
        File f = new File(parts[1]);
        if (!f.exists()) {
            System.out.println("file does not exists");
            return null;
        }

        clientHandler.setFileInputStream(new FileInputStream(f));
        ByteBuffer buffer = ByteBuffer.allocate(2 + parts[1].length() + 1);
        buffer.order(java.nio.ByteOrder.BIG_ENDIAN);

        buffer.putShort((short) Command.WRQ.getValue());
        buffer.put(parts[1].getBytes());
        buffer.put((byte) 0);

        return buffer.array();
    }

    private byte[] handleRRQ(String[] parts) {
        clientHandler.setRunningCommand(Command.RRQ);
        if (parts.length < 2) {
            System.out.println("RRQ command requires a filename");
            return null;
        }

        try {
            // Check if file exists
            File f = new File(parts[1]);
            if (f.exists()) {
                System.out.println("File already exists.");
                return null;
            }

            clientHandler.setWorkingFile(f);
            clientHandler.setFileOutputStream(new FileOutputStream(parts[1]));
            ByteBuffer buffer = ByteBuffer.allocate(2 + parts[1].length() + 1);
            buffer.order(java.nio.ByteOrder.BIG_ENDIAN);

            buffer.putShort((short) Command.RRQ.getValue());
            buffer.put(parts[1].getBytes());
            buffer.put((byte) 0);

            return buffer.array();
        } catch (Exception e) {
            System.out.println("Problem accessing file");
            return null;
        }
    }
}
