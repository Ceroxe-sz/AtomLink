package atomlink;

import atomlink.threads.CheckAliveThread;
import atomlink.threads.Transformer;
import plethora.management.bufferedFile.SizeCalculator;
import plethora.net.NetworkUtils;
import plethora.os.detect.OSDetector;
import plethora.os.windowsSystem.WindowsOperation;
import plethora.print.log.Loggist;
import plethora.print.log.State;
import plethora.security.encryption.AESUtil;
import plethora.security.encryption.RSAUtil;
import plethora.thread.ThreadManager;
import plethora.time.Time;
import plethora.utils.Sleeper;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Scanner;

public class AtomLink {
    private static int REMOTE_PORT;
    public static String REMOTE_DOMAIN_NAME = "127.0.0.1";
    public static String LOCAL_DOMAIN_NAME = "127.0.0.1";
    public static int HOST_HOOK_PORT = 801;
    public static int HOST_CONNECT_PORT = 802;
    public static int WINDOWS_UPDATE_PORT = 803;
    public static int LINUX_UPDATE_PORT = 804;
    public static final String CURRENT_DIR_PATH = System.getProperty("user.dir");
    public static Socket hookSocket;
    public static String key = null;
    public static int localPort = -1;
    public static Loggist loggist = AtomLink.initLoggist();
    public static LanguageData languageData = new LanguageData();
    ;
    public static final String CLIENT_FILE_PREFIX = "AtomLink-";

    public static ObjectOutputStream hookSocketWriter;
    public static ObjectInputStream hookSocketReader;
    public static RSAUtil rsaUtil = new RSAUtil(2048);
    public static AESUtil aesUtil;
    public static boolean IS_RECONNECTED_OPERATION = false;
    public static boolean IS_DEBUG_MODE = false;
    public static boolean ENABLE_AUTO_RECONNECT = true;
    public static int RECONNECTION_INTERVAL = 60;//s
    public static String INPUT_ADMIN_PASSWORD;

    private static Loggist initLoggist() {
        String currentDir = System.getProperty("user.dir");
        File logFile = new File(currentDir + File.separator + "logs" + File.separator + Time.getCurrentTimeAsFileName(false) + ".log");
        Loggist l = new Loggist(logFile);
        l.openWriteChannel();
        return l;
    }

    private static void checkARGS(String[] args) {
        for (String arg : args) {
            switch (arg) {
                case "--en-us" -> languageData = new LanguageData();
                case "--zh-ch" -> languageData = LanguageData.getChineseLanguage();
                case "--no-color" -> loggist.setNoColor();
                case "--debug" -> IS_DEBUG_MODE = true;
            }
            if (arg.contains(":")) {
                String[] ele = arg.split(":");
                switch (ele[0]) {//--key:aaabbb --localPort:25565
                    case "--key" -> AtomLink.key = ele[1];
                    case "--local-port" -> AtomLink.localPort = Integer.parseInt(ele[1]);
                    case "--admin" -> AtomLink.INPUT_ADMIN_PASSWORD = ele[1];
                }
            }
        }

        if (INPUT_ADMIN_PASSWORD != null) {
            //TODO Somethings
            System.exit(2);
        }
    }

    public static void main(String[] args) {
        checkARGS(args);


//        System.out.println("key = " + key);
//        System.out.println("localPort = " + localPort);

        ConfigOperator.readAndSetValue();

        if (!IS_RECONNECTED_OPERATION) {
            say("""

                       _____                                    \s
                      / ____|                                   \s
                     | |        ___   _ __    ___   __  __   ___\s
                     | |       / _ \\ | '__|  / _ \\  \\ \\/ /  / _ \\
                     | |____  |  __/ | |    | (_) |  >  <  |  __/
                      \\_____|  \\___| |_|     \\___/  /_/\\_\\  \\___|
                                                                \s
                                                                 \
                    """);
        }


        try {
            if (!IS_RECONNECTED_OPERATION) {
                detectLanguage();
                speakAnnouncement();
                say(languageData.VERSION + VersionInfo.VERSION);
            }

            if (key == null) {
                sayInfoNoNewLine(languageData.PLEASE_ENTER_ACCESS_CODE);
                AtomLink.key = AtomLink.inputStr();
            }

            say(languageData.CONNECT_TO + REMOTE_DOMAIN_NAME + languageData.OMITTED);
            hookSocket = new Socket(REMOTE_DOMAIN_NAME, HOST_HOOK_PORT);
            initConnection(hookSocket);

            // zh version key
            String clientInfo = AtomLink.formateClientInfoString(languageData, key);
            sendStr(hookSocketWriter, clientInfo);
            String str = receiveStr(hookSocketReader);
            if (str.contains("nsupported") || str.contains("不") || str.contains("旧")) {
                loggist.say(new State(State.ERROR, "SERVER", str));
                String versions = str.split(":")[1];
                String[] ver = versions.split("\\|");
                String version = ver[ver.length - 1];
                if (OSDetector.isWindows()) {
                    checkUpdate(CLIENT_FILE_PREFIX + version, WINDOWS_UPDATE_PORT);//it will exit!
                } else {
                    checkUpdate(CLIENT_FILE_PREFIX + version, LINUX_UPDATE_PORT);//it will exit!
                }
            } else if (str.contains("exit") || str.contains("退") || str.contains("错误") || str.contains("denied") || str.contains("already") || str.contains("占")) {
                say(str);
                exitAndFreeze(0);
            } else {

                if (OSDetector.isWindows()) {
                    int latency = NetworkUtils.getLatency(REMOTE_DOMAIN_NAME);
                    if (latency == -1) {
                        loggist.say(new State(State.INFO, "SERVER", languageData.TOO_LONG_LATENCY_MSG));
                        loggist.say(new State(State.INFO, "SERVER", str));
                    } else {
                        loggist.say(new State(State.INFO, "SERVER", str + " " + latency + "ms"));
                    }
                } else {
                    loggist.say(new State(State.INFO, "SERVER", str));
                }
            }

            CheckAliveThread.startThread();

            if (localPort == -1) {
                sayInfoNoNewLine(languageData.ENTER_PORT_MSG);
                try {
                    localPort = Integer.parseInt(AtomLink.inputStr());
                    if (localPort < 1 || localPort > 65535) {
                        throw new IndexOutOfBoundsException();
                    }
                } catch (IndexOutOfBoundsException e) {
                    say(languageData.PORT_OUT_OF_RANGE_MSG, State.ERROR);
                    exitAndFreeze(-1);
                } catch (NumberFormatException e) {
                    say(languageData.IT_MUST_BE_INT, State.ERROR);
                    exitAndFreeze(-1);
                }
            }

            String msg;
            while ((msg = receiveStr(hookSocketReader)) != null) {
                if (msg.startsWith(":>")) {
                    msg = msg.substring(2);
                    String[] ele = msg.split(";");
                    if (ele[0].equals("sendSocket")) {//:>sendSocket;
                        int finalLocalPort = localPort;
                        new Thread(() -> AtomLink.createNewConnection(finalLocalPort, ele[1])).start();
                    } else if (ele[0].equals("exit")) {
                        exitAndFreeze(0);
                    } else {
                        AtomLink.REMOTE_PORT = Integer.parseInt(ele[0]);
                    }
                } else if (msg.contains("This access code have") || msg.contains("消耗") || msg.contains("使用链接")) {
                    loggist.say(new State(State.WARNING, "SERVER", msg));
                } else {
                    say(msg);
                }
            }
        } catch (Exception e) {
            if (IS_DEBUG_MODE) {
                e.printStackTrace();
            }

            say(languageData.FAIL_TO_BUILD_A_CHANNEL_FROM + REMOTE_DOMAIN_NAME, State.ERROR);
            if (ENABLE_AUTO_RECONNECT) {
                for (int i = 0; i < RECONNECTION_INTERVAL; i++) {
                    languageData.sayReconnectMsg(RECONNECTION_INTERVAL - i);
                    Sleeper.sleep(1000);//1s

                }
                AtomLink.IS_RECONNECTED_OPERATION = true;
                AtomLink.main(new String[]{key, String.valueOf(localPort)});
                System.exit(0);
            } else {
                exitAndFreeze(-1);
            }
        }
    }

    public static void checkUpdate(String fileName, int port) {
        try {
            File clientFile;
            if (port == WINDOWS_UPDATE_PORT) {
                clientFile = new File(System.getProperty("user.dir") + File.separator + fileName + ".exe");
            } else {
                clientFile = new File(System.getProperty("user.dir") + File.separator + fileName + ".jar");
            }

            if (clientFile.exists()) {
                if (port==WINDOWS_UPDATE_PORT){
                    clientFile.renameTo(new File(clientFile.getParent()+File.separator+fileName+" - copy"+ ".exe"));
                }else{
                    clientFile.renameTo(new File(clientFile.getParent()+File.separator+fileName+" - copy"+ ".jar"));
                }
                clientFile.createNewFile();
            } else {
                clientFile.createNewFile();
            }

            Socket socket = new Socket(REMOTE_DOMAIN_NAME, port);
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(clientFile));
            BufferedInputStream bufferedInputStream = new BufferedInputStream(socket.getInputStream());

            sayInfoNoNewLine(languageData.START_TO_DOWNLOAD_UPDATE);

            byte[] data = new byte[(int) SizeCalculator.mibToByte(5)];
            int len;
            while ((len = bufferedInputStream.read(data)) != -1) {
                bufferedOutputStream.write(data, 0, len);
                bufferedOutputStream.flush();
                System.out.print(".");
            }
            bufferedOutputStream.close();
            bufferedInputStream.close();

            System.out.println();

            say(languageData.DOWNLOAD_SUCCESS);

            if (port == WINDOWS_UPDATE_PORT) {
                String command = "cmd.exe /c start \"" + clientFile.getName() + "\" " + "\"" + clientFile.getAbsolutePath() + "\"";
                if (key != null) {
                    command = command + " --key:" + key;
                }
                if (localPort != -1) {
                    command = command + " --local-port:" + localPort;
                }
                WindowsOperation.run(command);
                System.exit(0);
            } else {
                say(languageData.PLEASE_RUN + clientFile.getAbsolutePath());
            }

            exitAndFreeze(0);
        } catch (IOException e) {
            if (IS_DEBUG_MODE) {
                e.printStackTrace();
            }
            say("Fail to check updates.", State.ERROR);
            exitAndFreeze(0);
        }
    }

    private static String formateClientInfoString(LanguageData languageData, String key) {
        // zh version key
        return languageData.getCurrentLanguage() + ";" +
                VersionInfo.VERSION +
                ";" +
                key;
    }

    private static void detectLanguage() {
        Locale l = Locale.getDefault();
        if (l.getLanguage().contains("zh")) {
            AtomLink.languageData = LanguageData.getChineseLanguage();
            say("使用zh-ch作为备选语言");

        }
    }

    public static void initConnection(Socket hookSocket) {
        try {
            hookSocketReader = new ObjectInputStream(hookSocket.getInputStream());
            hookSocketWriter = new ObjectOutputStream(hookSocket.getOutputStream());

            hookSocketWriter.writeObject(rsaUtil.getPublicKey());
            hookSocketWriter.flush();

            SecretKey secretKey = new SecretKeySpec(rsaUtil.decrypt((byte[]) hookSocketReader.readObject()), "AES");
            aesUtil = new AESUtil(secretKey);

        } catch (Exception e) {
            if (IS_DEBUG_MODE) {
                e.printStackTrace();
            }
            exitAndFreeze(-1);
        }
    }

    public static void exitAndFreeze(int exitCode) {
        say("Press enter to exit the program...");
        Scanner scanner = new Scanner(System.in);
        scanner.nextLine();
        System.exit(exitCode);
    }


    private static void speakAnnouncement() {
        say(languageData.IF_YOU_SEE_EULA);
        VersionInfo.outPutEula();
    }

    public static String inputStr() {
        Scanner scanner = new Scanner(System.in);
        return scanner.nextLine();
    }

    public static void createNewConnection(int innerPort, String remoteAddress) {
        try {
            Socket server = new Socket(LOCAL_DOMAIN_NAME, innerPort);
            Socket transferChannelServer = new Socket(REMOTE_DOMAIN_NAME, HOST_CONNECT_PORT);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(transferChannelServer.getOutputStream());
            ObjectInputStream objectInputStream = new ObjectInputStream(transferChannelServer.getInputStream());
            objectOutputStream.writeInt(REMOTE_PORT);
            objectOutputStream.flush();

            say(languageData.A_CONNECTION + remoteAddress + " -> " + LOCAL_DOMAIN_NAME + ":" + server.getPort() + languageData.BUILD_UP);

            Transformer serverToTransferChannelServerThread = new Transformer(server, transferChannelServer, Transformer.LOCAL_TO_ATOM, objectOutputStream, objectInputStream);
            Transformer transferChannelServerToServerThread = new Transformer(transferChannelServer, server, Transformer.ATOM_TO_LOCAL, objectOutputStream, objectInputStream);
            ThreadManager threadManager = new ThreadManager(serverToTransferChannelServerThread, transferChannelServerToServerThread);
            threadManager.startAll();

            closeSocket(server);
            closeSocket(transferChannelServer);

            say(languageData.A_CONNECTION + remoteAddress + " -> " + LOCAL_DOMAIN_NAME + ":" + server.getPort() + languageData.DESTROY);

        } catch (Exception e) {
            if (IS_DEBUG_MODE) {
                e.printStackTrace();
            }
            say(languageData.FAIL_TO_CONNECT_LOCALHOST + innerPort, State.ERROR);

        }
    }

    public static void say(String str) {
        loggist.say(new State(State.INFO, "HOST-CLIENT", str));
    }

    public static void say(String str, int type) {
        loggist.say(new State(type, "HOST-CLIENT", str));
    }

    public static void sayInfoNoNewLine(String str) {
        loggist.sayNoNewLine(new State(State.INFO, "HOST-CLIENT", str));
    }

    public static void sendStr(ObjectOutputStream objectOutputStream, String str) throws IOException {
        objectOutputStream.writeObject(aesUtil.encrypt(str.getBytes(StandardCharsets.UTF_8)));
        objectOutputStream.flush();
    }

    public static String receiveStr(ObjectInputStream objectInputStream) throws ClassNotFoundException, IOException {
        Object enData = objectInputStream.readObject();
        if (enData == null) {
            return null;
        }
        return new String(aesUtil.decrypt((byte[]) enData), StandardCharsets.UTF_8);
    }

    public static void closeSocket(Socket... socket) {
        for (Socket socket1 : socket) {
            try {
                socket1.close();
            } catch (IOException e) {
                if (IS_DEBUG_MODE) {
                    e.printStackTrace();
                }
            }
        }
    }
}
