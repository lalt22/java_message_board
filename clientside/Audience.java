import java.io.*;
import java.net.*;

//Method referenced from: https://gist.github.com/absalomhr/ce11c2e43df517b2571b1dfc9bc9b487
public class Audience {
    private String IPAddress;
    private Integer UDPPort;
    private Integer PACKET_SIZE = 1024;

    public Audience(String IP, Integer port) {
        this.IPAddress = IP;
        this.UDPPort = port;
    }

    public void receiveAndCreateUDPFile(DatagramSocket ds) {
        try {
            //First receive name of file
            byte[] UDPFileNameBuffer = new byte[1024];
            DatagramPacket fileNamePacket = new DatagramPacket(UDPFileNameBuffer, UDPFileNameBuffer.length);

            ds.receive(fileNamePacket);

            byte[] fileNameBytes = fileNamePacket.getData();
            //Trim excess bytes
            String fileNameString = new String(fileNameBytes, 0, fileNamePacket.getLength()).trim();

            //If we receive a file, create new file
            if(!fileNameString.equals("")) {
                File UDPFile = new File(fileNameString);
                FileOutputStream streamReceivedData = new FileOutputStream(UDPFile);
                receiveFileDataPackets(ds,streamReceivedData);
                System.out.println("Received P2P File: " + fileNameString);
            }

        } catch(IOException e) {e.printStackTrace();}
    }

    public void receiveFileDataPackets(DatagramSocket ds, FileOutputStream fileOutputStream) {
        int sequenceNum;
        int lastReceivedSequenceNum = 0;
        int lastPacketOfFile;

        //while receiving packets, process them
        while (true) {
            byte[] dataPacketBuffer = new byte[1024];
            //The data we stream into the file won't include the checksum bytes
            byte[] bytesToFileBuffer = new byte[1022];

            DatagramPacket receivedDataPacket = new DatagramPacket(dataPacketBuffer, dataPacketBuffer.length);

            try {
                //Receive the packet and extract data
                ds.receive(receivedDataPacket);
                dataPacketBuffer = receivedDataPacket.getData();

                //Get address and port - use this data when sending acknolwdgement
                InetAddress sourceIPAddress = receivedDataPacket.getAddress();
                Integer sourceUDPPort = receivedDataPacket.getPort();

                //Get sequence number w 1's complement
                sequenceNum = ((dataPacketBuffer[0] & 0xff) << 8) + (dataPacketBuffer[1] & 0xff);
                //Check if packet is the last packet of the file - if 1: yes, if 0: no
                lastPacketOfFile = (dataPacketBuffer[2] & 0xff);

                //If received sequence number is greater than last, then have received right packet in order
                if (sequenceNum == lastReceivedSequenceNum + 1) {
                    lastReceivedSequenceNum = sequenceNum;

                    //Copy data from packet and add to stream
                    //Remember to not add first 3 bytes - checksum and flag
                    System.arraycopy(dataPacketBuffer, 3, bytesToFileBuffer, 0, 1021);
                    fileOutputStream.write(bytesToFileBuffer);

                    //Acknowledge packet received.
                    //If correct: sequenceNum
                    sendACK(sequenceNum, ds, sourceIPAddress, sourceUDPPort);
                }

                //If incorrect: lastReceivedSequenceNum;
                sendACK(lastReceivedSequenceNum, ds, sourceIPAddress, sourceUDPPort);

                //If last packet of file has been received, close stream and break loop
                if (lastPacketOfFile == 1) {
                    fileOutputStream.close();
                    break;
                }
            }catch(IOException e) {e.printStackTrace();}
        }
    }


    public static void sendACK(int sequenceNumber, DatagramSocket ds, InetAddress IP, Integer UDPPort) {
        //Acknowledge packet received. Set sequence number for checking correct packet received.
        byte[] ACKPacketByte = new byte[2];
        ACKPacketByte[0] = (byte) (sequenceNumber >> 8);
        ACKPacketByte[1] = (byte) (sequenceNumber);

        DatagramPacket ACKPacket = new DatagramPacket(ACKPacketByte, ACKPacketByte.length, IP, UDPPort);
        try{
            ds.send(ACKPacket);
        }catch(IOException e) {e.printStackTrace();}
    }
}
