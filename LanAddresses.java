import java.net.*;
import java.util.*;

/**
 * LanAddresses: ฟังก์ชันช่วยค้นหาที่อยู่ IP ในเครือข่าย LAN / Wi-Fi ของเครื่องนี้
 * ใช้สำหรับแสดง IP ให้เพื่อนนำไปกรอกเพื่อเชื่อมต่อกับเครื่องโฮสต์
 */
public final class LanAddresses {

    // คลาสนี้มีแต่เมธอดแบบ static จึงไม่ต้องสร้างออบเจกต์
    private LanAddresses() {
    }

    /**
     * ค้นหาที่อยู่ IPv4 ในวง LAN / Wi-Fi ทั้งหมดของเครื่องนี้
     * ข้าม Loopback, Link-local และการ์ดเครือข่ายเสมือน
     */
    public static List<String> getLanAddresses() {
        LinkedHashMap<String, Integer> ranks = new LinkedHashMap<String, Integer>();

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();

                if (!networkInterface.isUp()
                        || networkInterface.isLoopback()
                        || networkInterface.isVirtual()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();

                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();

                    if (address.isLoopbackAddress()
                            || address.isAnyLocalAddress()
                            || address.isLinkLocalAddress()) {
                        continue;
                    }

                    // เกมนี้ใช้เฉพาะ IPv4 จึงข้าม IPv6
                    if (address instanceof Inet4Address) {
                        String ip = address.getHostAddress();
                        ranks.put(ip, addressRank(ip));
                    }
                }
            }
        } catch (SocketException e) {
            // ไม่สามารถดึงรายชื่อการ์ดเครือข่ายได้ ให้คืนรายการว่าง
        }

        ArrayList<String> result = new ArrayList<String>(ranks.keySet());

        // เรียง IP ที่เหมาะสมที่สุดขึ้นก่อน ถ้าลำดับเท่ากันให้เรียงตามตัวอักษร
        Collections.sort(result, new Comparator<String>() {
            /** เปรียบเทียบ IP จาก rank ก่อน แล้วจึงเปรียบเทียบชื่อ */
            @Override
            public int compare(String first, String second) {
                int firstRank = ranks.get(first).intValue();
                int secondRank = ranks.get(second).intValue();

                if (firstRank < secondRank) {
                    return -1;
                }
                if (firstRank > secondRank) {
                    return 1;
                }
                return first.compareTo(second);
            }
        });

        return result;
    }

    /**
     * จัดลำดับความเหมาะสมของ IP (ค่าน้อยคือเหมาะสมกว่า)
     * ให้ความสำคัญกับวง 192.168.x.x และ 10.x.x.x ก่อน
     * และลดความสำคัญของ Virtual Adapter (เช่น VirtualBox, VMware, Docker)
     */
    private static int addressRank(String ip) {
        int rank;

        if (ip.startsWith("192.168.")) {
            rank = 0;
        } else if (ip.startsWith("10.")) {
            rank = 1;
        } else if (ip.startsWith("172.")) {
            int second = 0;
            try {
                String[] parts = ip.split("\\.");
                second = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                second = 0;
            } catch (ArrayIndexOutOfBoundsException e) {
                second = 0;
            }

            if (second >= 16 && second <= 31) {
                rank = 2;
            } else {
                rank = 3;
            }
        } else {
            rank = 3;
        }

        // วง Host-only ที่มักใช้โดย VirtualBox (192.168.56.x, 192.168.99.x)
        if (ip.startsWith("192.168.56.") || ip.startsWith("192.168.99.")) {
            rank += 10;
        }

        return rank;
    }

    /**
     * ดึงที่อยู่ IP วง LAN ที่ดีที่สุดสำหรับแสดงแนะนำให้เพื่อนเชื่อมต่อ
     */
    public static String getPreferredLanAddress() {
        List<String> addresses = getLanAddresses();
        if (addresses.isEmpty()) {
            return "";
        }
        return addresses.get(0);
    }
}
