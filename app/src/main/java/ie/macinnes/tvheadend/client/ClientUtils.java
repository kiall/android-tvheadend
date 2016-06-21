package ie.macinnes.tvheadend.client;

import android.util.Base64;

import java.util.HashMap;
import java.util.Map;

public class ClientUtils {
    private static final String TAG = ClientUtils.class.getName();

    public static Map<String, String> createBasicAuthHeader(String username, String password) {
        Map<String, String> headerMap = new HashMap<String, String>();

        String credentials = username + ":" + password;

        String base64EncodedCredentials =
                Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);
        headerMap.put("Authorization", "Basic " + base64EncodedCredentials);

        return headerMap;
    }
}
