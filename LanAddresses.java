import java.net.*;
import java.util.*;

/** ฟังก์ชันช่วยค้นหา IP ในวง LAN */
public final class LanAddresses {

    // ป้องกันการสร้างออบเจกต์
    private LanAddresses() {
    }

    /** รายการ IPv4 ของเครื่องนี้ในวง LAN */
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

                    // ข้าม IPv6
                    if (address instanceof Inet4Address) {
                        String ip = address.getHostAddress();
                        ranks.put(ip, addressRank(ip));
                    }
                }
            }
        } catch (SocketException e) {
            // การ์ดเครือข่ายอ่านไม่ได้
        }

        ArrayList<String> result = new ArrayList<String>(ranks.keySet());

        // เรียง IP ตามความเหมาะสม
        Collections.sort(result, new Comparator<String>() {
            /** เปรียบเทียบ IP ตาม rank แล้วตามชื่อ */
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

    /** คะแนนความเหมาะสมของ IP (น้อย = เหมาะกว่า) */
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

        // วง Host-only ของ VirtualBox
        if (ip.startsWith("192.168.56.") || ip.startsWith("192.168.99.")) {
            rank += 10;
        }

        return rank;
    }

    /** IP ที่แนะนำให้เพื่อนเชื่อมต่อ */
    public static String getPreferredLanAddress() {
        List<String> addresses = getLanAddresses();
        if (addresses.isEmpty()) {
            return "";
        }
        return addresses.get(0);
    }
}
