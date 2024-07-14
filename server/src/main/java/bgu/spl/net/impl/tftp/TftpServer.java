package bgu.spl.net.impl.tftp;
import bgu.spl.net.srv.Server;

public class TftpServer {
    
    public static void main(String[] args) {
        int port;
        // Check if a port number is passed as an argument
        if (args.length < 1) {
            port = 7777; // Default port
        } else {
            try {
                port = Integer.parseInt(args[0]); // Convert the first argument to an integer
            } catch (NumberFormatException e) {
                System.out.println("Invalid port number, using default port 7777");
                port = 7777; // Default port
            }
        }

        Server.threadPerClient(
                port, // Use the passed port
                () -> new TftpProtocol(), //protocol factory
                TftpEncoderDecoder::new //message encoder decoder factory
        ).serve();

    }
}