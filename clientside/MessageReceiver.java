import java.io.*;
import java.net.*;

public class MessageReceiver {
    private Integer sourcePort;
    private String sourceIP;


    public MessageReceiver(String IP, Integer port) {
        this.sourceIP = IP;
        this.sourcePort = port;
    }

    public void receiveMessage(Socket s) {
        DataInputStream dis = null;
        try {
            dis = new DataInputStream(s.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
        while(true) {
            assert dis != null;
            try {
                String receivedMessage = dis.readUTF();
                System.out.println(receivedMessage);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
}
