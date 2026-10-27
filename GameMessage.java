import java.io.*;

/**
 * GameMessage: โครงสร้างข้อความตามโปรโตคอลของเกม
 * ประกอบด้วยประเภทข้อความ (type) และชุดข้อความอาร์กิวเมนต์ (args)
 * เขียนลง DataOutputStream ด้วยรูปแบบ writeUTF(type), writeInt(count), writeUTF(arg) * count
 * โดยทุกข้อความจะมีขนาดความยาวระบุชัดเจน เพื่อป้องกันปัญหาข้อมูลปะปนกันบน TCP Stream
 */
public class GameMessage {

    public final String type;       // ประเภทข้อความ
    public final String[] args;     // รายการข้อมูลพารามิเตอร์

    public GameMessage(String type, String... args) {
        this.type = type;
        this.args = (args == null) ? new String[0] : args;
    }

    // ดึงค่าอาร์กิวเมนต์ตามดัชนี หากเกินช่วงจะคืนค่าสตริงว่าง
    public String arg(int index) {
        return (index < 0 || index >= args.length) ? "" : args[index];
    }

    // เขียนข้อมูลข้อความลงในสตรีม
    public void writeTo(DataOutputStream out) throws IOException {
        out.writeUTF(type);
        out.writeInt(args.length);
        for (String value : args) {
            out.writeUTF(value == null ? "" : value);
        }
        out.flush();
    }

    // อ่านข้อมูลข้อความจากสตรีม
    public static GameMessage readFrom(DataInputStream in) throws IOException {
        String type = in.readUTF();
        int count = in.readInt();
        if (count < 0 || count > 1024) {
            throw new IOException("Malformed message: bad field count " + count);
        }
        var args = new String[count];
        for (int i = 0; i < count; i++) {
            args[i] = in.readUTF();
        }
        return new GameMessage(type, args);
    }
}
