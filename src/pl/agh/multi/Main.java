package pl.agh.multi;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import pl.edu.agh.kis.visca.ViscaResponseReader;
import pl.edu.agh.kis.visca.cmd.*;
import jssc.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class Main {
    public static int check = 0;
    public static String response = "";
    public static SerialPort port;

    private static String htmlPage =
            "<html>" + "<body>\n" +
            "<h3>** VISCA management interface **</h3>\n" +
            "<form name=\"commandForm\" method=\"POST\">\n" +
            "    Command: <br> <input type=\"text\" name=\"comm\" value=\"\"> <br>\n" +
            "    <input type=\"submit\" value=\"Execute!\">\n" +
            "</form>\n" +
            "</body></html>";

    public static void main(String[] args) throws IOException {
        String portNumber = "COM11";
        port = new SerialPort(portNumber);

        try {
            port.openPort();
            port.setParams(9600, 8, 1, 0);
        } catch (SerialPortException e) {
            e.printStackTrace();
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/test", new MyHandler()); //management endpoint
        server.setExecutor(null); // creates a default executor
        server.start();
    }

    static class MyHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange t) throws IOException {
            if(check > 0) {
                String out = executePostBody(t);
                executeCommand(out);
                byte[] bytes = htmlPage.replace("</body></html>","<div>" + response + "</div></body></html>").getBytes();
                t.sendResponseHeaders(200, bytes.length);
                OutputStream os = t.getResponseBody();
                os.write(bytes);
            }
            check = check + 1;

            byte[] response = htmlPage.getBytes();
            t.sendResponseHeaders(200, response.length);
            OutputStream os = t.getResponseBody();
            os.write(response);
            os.close();
        }
    }

    static String executePostBody(HttpExchange t) throws IOException {
        InputStreamReader isr =  new InputStreamReader(t.getRequestBody(),"utf-8");
        BufferedReader br = new BufferedReader(isr);

        int polecenie;
        StringBuilder buf = new StringBuilder(512);
        while ((polecenie = br.read()) != -1) {
            buf.append((char) polecenie);
        }
        return buf.toString().split("=")[1]; //body contains 'comm=left+10'
    }

    static void executeCommand(String str) {
        String[] command = new String[1];
        if(str.indexOf("+") != -1) {
            command = str.split("\\+");
        }else {
            command[0] = str;
        }
        execute(command);
    }

    public static void execute(String[] args) {

        String comm = args[0];
        int val = (byte) 0;
        if (args.length >1){
            val = Integer.parseInt(args[1]);
        }
        response = "Doing: " + comm + " with argument: " + val;
        byte[] ex;

        try {
            byte[] cmdData = {};
            switch (args[0]) {
                case ("left"):
                    cmdData = (new PanTiltLeftCmd()).createCommandData();
                    cmdData[3] = (byte) val;
                    break;
                case ("right"):
                    cmdData = (new PanTiltRightCmd()).createCommandData();
                    cmdData[3] = (byte) val;
                    break;
                case ("up"):
                    cmdData = (new PanTiltUpCmd()).createCommandData();
                    cmdData[3] = (byte) val;
                    break;
                case ("down"):
                    cmdData = (new PanTiltDownCmd()).createCommandData();
                    cmdData[3] = (byte) val;
                    break;
                case ("home"):
                    cmdData = (new PanTiltHomeCmd()).createCommandData();
                    break;
                case ("zoomWide"):
                    cmdData = (new ZoomWideStdCmd()).createCommandData();
                    break;
                case ("zoomTele"):
                    cmdData = (new ZoomTeleStdCmd()).createCommandData();
                    break;
                default:
                    response = "Not recognized command";
            }

            ViscaCommand vCmd = new ViscaCommand();
            vCmd.commandData = cmdData;
            vCmd.sourceAdr = 0;
            vCmd.destinationAdr = 1;
            cmdData = vCmd.getCommandData();
            System.out.println("@ " + byteArrayToString(cmdData));
            try {
                port.writeBytes(cmdData);
            } catch (SerialPortException e) {
                e.printStackTrace();
            }

            try {
                ex = ViscaResponseReader.readResponse(port);
                System.out.println("> " + byteArrayToString(ex));
            } catch (ViscaResponseReader.TimeoutException var11) {
                System.out.println("! TIMEOUT exception");
            }
        }catch(SerialPortException e){
            e.printStackTrace();
        }

        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static String byteArrayToString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        byte[] var5 = bytes;
        int var4 = bytes.length;

        for(int var3 = 0; var3 < var4; ++var3) {
            byte b = var5[var3];
            sb.append(String.format("%02X ", new Object[]{Byte.valueOf(b)}));
        }

        return sb.toString();
    }
}
