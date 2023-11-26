package atomlink;

import asia.ceroxe.management.bufferedFile.BufferedFile;
import asia.ceroxe.utils.config.LineConfigReader;
import atomlink.threads.CheckAliveThread;
import atomlink.threads.Transformer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static atomlink.AtomLink.say;

public class ConfigOperator {
    public static final BufferedFile CONFIG_FILE = new BufferedFile(AtomLink.CURRENT_DIR_PATH + File.separator + "config.cfg");

    private ConfigOperator() {
    }

    public static void readAndSetValue() {
        LineConfigReader lineConfigReader = new LineConfigReader(CONFIG_FILE);

        if (!CONFIG_FILE.exists()) {
            createAndSetDefaultConfig();
        } else {
            try {
                lineConfigReader.load();

                AtomLink.REMOTE_DOMAIN_NAME = lineConfigReader.get("REMOTE_DOMAIN_NAME");
                AtomLink.LOCAL_DOMAIN_NAME = lineConfigReader.get("LOCAL_DOMAIN_NAME");
                AtomLink.HOST_HOOK_PORT = Integer.parseInt(lineConfigReader.get("HOST_HOOK_PORT"));
                AtomLink.HOST_CONNECT_PORT = Integer.parseInt(lineConfigReader.get("HOST_CONNECT_PORT"));
                AtomLink.ENABLE_AUTO_RECONNECT = Boolean.parseBoolean(lineConfigReader.get("ENABLE_AUTO_RECONNECT"));
                AtomLink.RECONNECTION_INTERVAL = Integer.parseInt(lineConfigReader.get("RECONNECTION_INTERVAL"));
                AdminMgr.ADMIN_PORT = Integer.parseInt(lineConfigReader.get("ADMIN_PORT"));
                CheckAliveThread.HEARTBEAT_PACKET_DELAY = Integer.parseInt(lineConfigReader.get("HEARTBEAT_PACKET_DELAY"));
                Transformer.BUFFER_LEN = Integer.parseInt(lineConfigReader.get("BUFFER_LEN"));
            } catch (Exception e) {
                createAndSetDefaultConfig();
            }
        }
    }

    private static void createAndSetDefaultConfig() {
        CONFIG_FILE.delete();
        CONFIG_FILE.createNewFile();

        try {
            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(CONFIG_FILE));

            bufferedWriter.write("""
                    #把你要连接的AtomServerX的域名或者公网ip放到这里来
                    #Put the domain name or public network ip of the AtomServerX you want to connect to here
                    REMOTE_DOMAIN_NAME=127.0.0.1

                    #如果你不知道以下的设置意味着什么，请你不要改变它
                    #If you don't know what the following setting means, please don't change it
                    LOCAL_DOMAIN_NAME=localhost
                    HOST_HOOK_PORT=801
                    HOST_CONNECT_PORT=802
                    WINDOWS_UPDATE_PORT=803
                    LINUX_UPDATE_PORT=804
                    ADMIN_PORT=945

                    #设置发送心跳包的间隔，单位为毫秒
                    #Set the interval for sending heartbeat packets, in milliseconds
                    HEARTBEAT_PACKET_DELAY=1000

                    #是否启用自动重连当服务端暂时离线的时候
                    #Whether to enable automatic reconnection when the server is temporarily offline
                    ENABLE_AUTO_RECONNECT=true

                    #如果ENABLE_AUTO_RECONNECT设置为true，则将间隔多少秒后重连，单位为秒，且必须为大于0的整数
                    #If ENABLE_AUTO_RECONNECT is set to true, the number of seconds after which reconnection will be made in seconds and must be an integer greater than 0
                    RECONNECTION_INTERVAL=30

                    #数据包数组的长度
                    #The length of the packet array
                    BUFFER_LEN=117""");

            bufferedWriter.flush();
            bufferedWriter.close();

        } catch (IOException e) {
            say("Fail to write default config.");
            System.exit(-1);
        }

        readAndSetValue();
    }
}
