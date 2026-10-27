import java.net.*;
import java.util.*;

/**
 * LanAddresses: ฟังก์ชันช่วยค้นหาที่อยู่ IP ในเครือข่าย LAN / Wi-Fi ของเครื่องนี้
 */
public final class LanAddresses {

    private LanAddresses() {
    }

    /**
     * ค้นหาที่อยู่ IPv4 ในวง LAN / Wi-Fi ทั้งหมดของเครื่องนี้ที่ไม่ใช่ Loopback
     */
    public static List<String> getLanAddresses() {
        var ranks = new LinkedHashMap<String, Integer>();
        try {
            var interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                var networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }
                var addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    var address = addresses.nextElement();
                    if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress()) {
                        continue;
                    }
                    if (address instanceof Inet4Address) {
                        String ip = address.getHostAddress();
                        ranks.put(ip, addressRank(ip));
                    }
                }
            }
        } catch (SocketException e) {
            // ไม่สามารถดึงรายชื่อการ์ดเครือข่ายได้
        }
        var result = new ArrayList<>(ranks.keySet());
        // เรียงลำดับที่อยู่ IP ที่เหมาะสมที่สุดขึ้นก่อน
        result.sort((a, b) -> {
            int byRank = ranks.get(a).compareTo(ranks.get(b));
            return (byRank != 0) ? byRank : a.compareTo(b);
        });
        return result;
    }

    /**
     * จัดลำดับความเหมาะสมของ IP (ค่าน้อยคือเหมาะสมกว่า):
     * ให้ความสำคัญกับวง 192.168.x.x และ 10.x.x.x ก่อน และลดความสำคัญของ Virtual Adapter (เช่น VirtualBox, VMware, Docker)
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
                second = Integer.parseInt(ip.split("\\.")[1]);
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                second = 0;
            }
            rank = (second >= 16 && second <= 31) ? 2 : 3;
        } else {
            rank = 3;
        }

        // วง Host-only ที่มักใช้โดย VirtualBox (192.168.56.x)
        if (ip.startsWith("192.168.56.") || ip.startsWith("192.168.99.")) {
            rank += 10;
        }
        return rank;
    }

    /** ดึงที่อยู่ IP วง LAN ที่ดีที่สุดสำหรับแสดงแนะนำให้เพื่อนเชื่อมต่อ */
    public static String getPreferredLanAddress() {
        List<String> addresses = getLanAddresses();
        return addresses.isEmpty() ? "" : addresses.get(0);
    }
}
