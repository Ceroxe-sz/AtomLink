package atomlink.threads;

import java.io.*;
import java.net.Socket;

import static atomlink.AtomLink.IS_DEBUG_MODE;
import static atomlink.AtomLink.aesUtil;

public class Transformer implements Runnable {
    public static int BUFFER_LEN = 117;
    private final Socket sender;
    private final Socket receiver;
    public int mode;
    public static final int LOCAL_TO_ATOM = 0;
    public static final int ATOM_TO_LOCAL = 1;
    public final ObjectOutputStream objectOutputStream;
    public final ObjectInputStream objectInputStream;

    public Transformer(Socket sender, Socket receiver, int mode, ObjectOutputStream objectOutputStream, ObjectInputStream objectInputStream) {
        this.sender = sender;
        this.receiver = receiver;
        this.mode = mode;
        this.objectOutputStream = objectOutputStream;
        this.objectInputStream = objectInputStream;
    }

    @Override
    public void run() {//对象流一定都是AtomServer服务端的，对象流参数前面的Socket一定是通向AtomServer的
        if (this.mode == Transformer.ATOM_TO_LOCAL) {
            transferDataToLocalServer(sender, objectInputStream, receiver);
        } else {
            transferDataToAtomServer(sender, receiver, objectOutputStream);
        }
    }

    public static void transferDataToAtomServer(Socket sender, Socket receiver, ObjectOutputStream objectOutputStream) {
        try {
            BufferedInputStream bufferedInputStream = new BufferedInputStream(sender.getInputStream());

            int len;
            byte[] data = new byte[BUFFER_LEN];
            while ((len = bufferedInputStream.read(data)) != -1) {

//                System.out.println("transferDataToAtomServer---------------------------"+Thread.currentThread().getName());
//                System.out.println(new String(data));
//                System.out.println("transferDataToAtomServer---------------------------"+Thread.currentThread().getName());

                objectOutputStream.writeObject(aesUtil.encrypt(data, 0, len));
                objectOutputStream.flush();
                objectOutputStream.writeInt(len);
                objectOutputStream.flush();
            }

            objectOutputStream.writeObject(null);//tell atom server is end!
            receiver.shutdownOutput();
            sender.shutdownInput();

        } catch (Exception e) {
            if (IS_DEBUG_MODE) {
                e.printStackTrace();
            }
            try {

                objectOutputStream.writeObject(null);//tell atom server is end!
                receiver.shutdownOutput();
                sender.shutdownInput();

            } catch (IOException ignore) {
            }

        }
    }

    public static void transferDataToLocalServer(Socket sender, ObjectInputStream objectInputStream, Socket receiver) {
        try {
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(receiver.getOutputStream());

            byte[] data;
            while ((data = (byte[]) objectInputStream.readObject()) != null) {
                byte[] deRealData = aesUtil.decrypt(data);

//                System.out.println("transferDataToLocalServer---------------"+Thread.currentThread().getName());
//                System.out.println(new String(deRealData));
//                System.out.println("transferDataToLocalServer---------------"+Thread.currentThread().getName());

                bufferedOutputStream.write(deRealData, 0, objectInputStream.readInt());
                bufferedOutputStream.flush();
            }
            sender.shutdownInput();
            receiver.shutdownOutput();
        } catch (Exception e) {//EOF EXCEPTION !
            if (IS_DEBUG_MODE) {
                e.printStackTrace();
            }
            try {
                sender.shutdownInput();
                receiver.shutdownOutput();
            } catch (IOException ignore) {
            }
        }
    }
}
