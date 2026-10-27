import java.io.*;

/**
 * GameMessage: โครงสร้างข้อความตามโปรโตคอลของเกม
 * ประกอบด้วยประเภทข้อความ (type) และชุดข้อความอาร์กิวเมนต์ (args)
 *
 * รูปแบบที่ส่งบนเครือข่าย:
 *   writeUTF(type)
 *   writeInt(จำนวนอาร์กิวเมนต์)
 *   writeUTF(อาร์กิวเมนต์) จำนวนครั้งตามที่ระบุ
 *
 * การระบุความยาวไว้ล่วงหน้าทำให้ฝั่งรับรู้ว่าต้องอ่านกี่ค่า
 * และป้องกันข้อมูลของข้อความถัดไปปะปนกันบน TCP Stream
 */
public class GameMessage {

    public final String type;       // ประเภทข้อความ
    public final String[] args;     // รายการข้อมูลพารามิเตอร์

    /**
     * สร้างข้อความใหม่
     * ถ้าไม่ส่งอาร์กิวเมนต์มา จะเก็บเป็นอาร์เรย์ว่างแทน null
     */
    public GameMessage(String type, String... args) {
        this.type = type;
        if (args == null) {
            this.args = new String[0];
        } else {
            this.args = args;
        }
    }

    /**
     * ดึงค่าอาร์กิวเมนต์ตามตำแหน่งที่ระบุ
     * ถ้าตำแหน่งเกินช่วง จะคืนค่าสตริงว่างเพื่อให้ผู้เรียกใช้งานต่อได้ปลอดภัย
     */
    public String arg(int index) {
        if (index < 0 || index >= args.length) {
            return "";
        }
        return args[index];
    }

    /**
     * เขียนข้อความลงในสตรีม
     */
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

    /**
     * อ่านข้อความจากสตรีม
     * จำกัดจำนวนอาร์กิวเมนต์ไว้ที่ 1024 ค่า เพื่อไม่ให้อ่านข้อมูลผิดรูปแบบจนค้าง
     */
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
