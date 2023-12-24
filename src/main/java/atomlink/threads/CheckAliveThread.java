package atomlink.threads;

import atomlink.AtomLink;
import plethora.utils.Sleeper;

import static atomlink.AtomLink.IS_DEBUG_MODE;

public class CheckAliveThread implements Runnable {
    public static int HEARTBEAT_PACKET_DELAY = 1000;

    private CheckAliveThread() {
    }

    public static void startThread() {
        Thread a = new Thread(new CheckAliveThread());
        a.start();
    }

    @Override
    public void run() {
        while (true) {
            try {
                AtomLink.hookSocketWriter.writeObject("");
            } catch (Exception e) {
                if (IS_DEBUG_MODE) {
                    e.printStackTrace();
                    break;
                }
            }
            Sleeper.sleep(HEARTBEAT_PACKET_DELAY);
        }
    }
}
