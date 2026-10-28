import java.io.*;

/** ข้อความโปรโตคอล: type, จำนวน args, ตามด้วย args */
public class GameMessage {

    public final String type;       // ประเภทข้อความ
    public final String[] args;     // รายการข้อมูลพารามิเตอร์

    /** สร้างข้อความใหม่ (args null = อาเรย์ว่าง) */
    public GameMessage(String type, String[] args) {
        this.type = type;
        if (args == null) {
            this.args = new String[0];
        } else {
            this.args = args;
        }
    }

    /** อาร์กิวเมนต์ตามตำแหน่ง (ค่าว่างถ้าเกินช่วง) */
    public String arg(int index) {
        if (index < 0 || index >= args.length) {
            return "";
        }
        return args[index];
    }

    /** เขียนข้อความลงสตรีม */
    public void writeTo(DataOutputStream out) throws IOException {
        out.writeUTF(type);
        out.writeInt(args.length);

        for (String value : args) {
            if (value == null) {
                out.writeUTF("");
            } else {
                out.writeUTF(value);
            }
        }
        out.flush();
    }

    /** อ่านข้อความจากสตรีม (สูงสุด 1024 args) */
    public static GameMessage readFrom(DataInputStream in) throws IOException {
        String type = in.readUTF();
        int count = in.readInt();

        if (count < 0 || count > 1024) {
            throw new IOException("Malformed message: bad field count " + count);
        }

        String[] args = new String[count];
        for (int i = 0; i < count; i++) {
            args[i] = in.readUTF();
        }
        return new GameMessage(type, args);
    }
}
