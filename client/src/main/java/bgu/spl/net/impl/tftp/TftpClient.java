package bgu.spl.net.impl.tftp;

import java.io.*;
import java.net.Socket;

public class TftpClient {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: TftpClient <server IP> <server port>");
            System.exit(1);
        }

        String serverIp = args[0];
        int serverPort;
        try {
            serverPort = Integer.parseInt(args[1]);
        } catch(NumberFormatException e) {
            System.out.println("Invalid port number");
            return;
        }

        try {
            Socket socket = new Socket(serverIp, serverPort);
            ClientHandler clientHandler = new ClientHandler(socket.getOutputStream());
            Thread keyboardThread = new Thread(new KeyboardThread(clientHandler));
            Thread listeningThread = new Thread(new ListeningThread(socket.getInputStream(), clientHandler));

            keyboardThread.start();
            listeningThread.start();

            keyboardThread.join();
            listeningThread.interrupt();
            socket.close();
        } catch (IOException | InterruptedException e) {
            System.out.println("Error connecting to server. make sure the server is running and try again.");
        }
    }
}
