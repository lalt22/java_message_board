import java.io.*;
import java.net.*;
//Method referenced from: https://gist.github.com/absalomhr/ce11c2e43df517b2571b1dfc9bc9b487
public class Presenter {
    private String audienceHostAddress;
    private Integer audienceUDPPort;
    private String presenterUserName;

    public Presenter(String IP, Integer port, String presenterUserName) {
        this.audienceHostAddress = IP;
        this.audienceUDPPort = port;
        this.presenterUserName = presenterUserName;
    }

    public void sendUDPFile(File f, DatagramSocket dSocket) {
        try {
            String fileNameToSend = f.getName();
            //Add presenter name to filename
            fileNameToSend = this.presenterUserName + "_" + fileNameToSend;

            //Transform filename to bytes to be sent by datagram socket as a datagram packet
            byte[] fileNameBytes = fileNameToSend.getBytes();
            DatagramPacket fileNameBytePacket = new DatagramPacket(fileNameBytes, fileNameBytes.length, InetAddress.getByName(this.audienceHostAddress), this.audienceUDPPort);
            //Send filename to be processed first
            dSocket.send(fileNameBytePacket);

            //Then convert file to bytes and send in packets
            byte[] fullFileInBytes = convertFileToBytes(f);
            sendPacketsOfFile(dSocket, fullFileInBytes, this.audienceHostAddress, this. audienceUDPPort);
        } catch(IOException e) {e.printStackTrace();}
    }

    public static byte[] convertFileToBytes(File f) {
        FileInputStream fileInputStream;
        //Final array of bytes should be same length as file
        byte[] finalResultBuffer =  new byte[(int)f.length()];
        try {
            fileInputStream = new FileInputStream(f);
            //Read bytes from file into result buffer
            fileInputStream.read(finalResultBuffer);
            fileInputStream.close();
        } catch(IOException e) {e.printStackTrace();}
        return finalResultBuffer;
    }

    public static void sendPacketsOfFile(DatagramSocket ds, byte[] fileToByteArray, String audienceHostAddress, Integer audienceUDPPort) {
        //Orders sending of packets
        int sequenceNum = 0;
        //Checks if packets received correctly
        int ACKSequenceNum = 0;
        boolean ACKReceived = true;
        //Flags if last packet to be sent for file
        boolean lastPacket;


        //First 3 bytes for sequenceNum tracking and lastPacket flag, so increment by 1021
        for (int i = 0; i < fileToByteArray.length; i = i + 1021) {
            sequenceNum = sequenceNum + 1;

            //Each packet 1024 bytes in size
            byte[] messageByte = new byte[1024];
            //Assign first two bytes for checksum
            messageByte[0] = (byte) (sequenceNum >> 8); //If sequenceNum < 256, reset order to 0
            messageByte[1] = (byte) (sequenceNum);

            //Check if current packet will be the last packet of the file.
            //Set flag to true and set third byte of messageByte to flag that it is the last packet
            if ((i + 1021) >= fileToByteArray.length) {
                lastPacket = true;
                messageByte[2] = 1;
            }
            else {
                lastPacket = false;
                messageByte[2] = 0;
            }

            //Copy correct number of bytes from filebytearray to messagePacket
            if (lastPacket) {
                //Copy remaining length of filebytearray to messagepacket from position 3 (make up for checksum and flag bytes)
                System.arraycopy(fileToByteArray, i, messageByte, 3, fileToByteArray.length-i);
            }
            else {
                //Copies 1021 bytes from position 3 to position 1024 of messageByte
                System.arraycopy(fileToByteArray, i, messageByte, 3, 1021);
            }

            //Once packets are loaded, send them
            try {
                //Make packet to send to audience
                DatagramPacket dataPacket = new DatagramPacket(messageByte, messageByte.length, InetAddress.getByName(audienceHostAddress), audienceUDPPort);
                ds.send(dataPacket);

                //Ensure packets received correctly
                while(true) {
                    //Create acknowledgement packets
                    byte[] ACKByte = new byte[2];
                    DatagramPacket ACKPacket = new DatagramPacket(ACKByte, ACKByte.length);

                    //Timeout after 0.3s of no response
                    try {
                        ds.setSoTimeout(300);
                        ds.receive(ACKPacket);

                        //Use 1's complement to get ACK sequenceNum
                        ACKSequenceNum = ((ACKByte[0] & 0xff) << 8) + (ACKByte[1] & 0xff);

                    //Check that ACKSequenceNum and sequenceNumber are the same after one's complement
                    //If not, error has occurred. Resend past packet
                    //If yes, send next packet
                        if((ACKSequenceNum == sequenceNum)) {
                            break;
                        }
                    } catch (IOException e) {ds.send(dataPacket);}
                }
            } catch(IOException e) {e.printStackTrace();}
        }
    }
}
