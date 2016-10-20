/*
 * Copyright (c) 2016 Kiall Mac Innes <kiall@macinnes.ie>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package ie.macinnes.htsp;

import android.util.Log;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * See: https://tvheadend.org/projects/tvheadend/wiki/Htsp
 * See: https://tvheadend.org/projects/tvheadend/wiki/Htsmsgbinary
 *
 * HTSP Message Field Types:
 *
 * | Name | ID | Description
 * | Map  | 1  | Sub message of type map
 * | S64  | 2  | Signed 64bit integer
 * | Str  | 3  | UTF-8 encoded string
 * | Bin  | 4  | Binary blob
 * | List | 5  | Sub message of type list
 */
public class HtspMessage extends HashMap<String, Object> {
    private static final String TAG = HtspMessage.class.getName();

    private static final byte FIELD_MAP = 1;
    private static final byte FIELD_S64 = 2;
    private static final byte FIELD_STR = 3;
    private static final byte FIELD_BIN = 4;
    private static final byte FIELD_LIST = 5;

    protected static final Map<String, Class<? extends RequestMessage>> sMessageRequestTypes = new HashMap<>();
    protected static final Map<String, Class<? extends ResponseMessage>> sMessageResponseTypes = new HashMap<>();
    protected static final Map<Long, Class<? extends ResponseMessage>> sMessageResposeTypesBySequence = new HashMap<>();

    public static void addMessageRequestType(String method, Class<? extends RequestMessage> clazz) {
        Log.v(TAG, "Registering RequestType for method " + method);
        sMessageRequestTypes.put(method, clazz);
    }

    public static void addMessageResponseType(String method, Class<? extends ResponseMessage> clazz) {
        Log.v(TAG, "Registering ResponseType for method " + method);
        sMessageResponseTypes.put(method, clazz);
    }

    public static void addMessageResponseTypeBySeq(Long seq, Class<? extends ResponseMessage> clazz) {
        sMessageResposeTypesBySequence.put(seq, clazz);
    }

    public static ResponseMessage fromWire(ByteBuffer buffer) {
        byte[] lenBytes = new byte[4];

        lenBytes[0] = buffer.get(0);
        lenBytes[1] = buffer.get(1);
        lenBytes[2] = buffer.get(2);
        lenBytes[3] = buffer.get(3);

        int length = (int) bin2long(lenBytes);

        if (length > buffer.capacity()) {
            throw new RuntimeException("Message exceeds buffer capacity: " + length);
        }

        // Set the buffer limit to the length of the message + the 4 byte message length.
        buffer.limit(length + 4);

        // Keep reading until we have the entire message
        if (buffer.position() < length + 4) {
            Log.v(TAG, "Reading more, don't have enough data yet. Need: " + length + " bytes / Have: " + buffer.position() + " bytes");
            return null;
        }

        buffer.flip();
        buffer.position(4);

        HtspMessage message = deserialize(buffer);

        Class<? extends ResponseMessage> messageClass;

        String method = message.getString("method", null);
        Long sequence = message.containsKey("seq") ? message.getLong("seq") : null;

        if (sMessageResposeTypesBySequence.containsKey(sequence)) {
            messageClass = sMessageResposeTypesBySequence.remove(sequence);

        } else if (sMessageResponseTypes.containsKey(method)) {
            messageClass = sMessageResponseTypes.get(method);

        } else {
            Log.d(TAG, "Unknown message type for seq: " + sequence + " / method: " + method);
            messageClass = ResponseMessage.class;
        }

        ResponseMessage typedMessage;

        try {
            typedMessage = messageClass.newInstance();
            typedMessage.fromHtspMessage(message);
        } catch (InstantiationException|IllegalAccessException e) {
            Log.e(TAG, "Failed to set message type", e);
            return null;
        }

        return typedMessage;
    }

    public ByteBuffer toWire() {
        ByteBuffer buffer = ByteBuffer.allocate(65535);

        // Move 4 bytes up, making room for the overall message size.
        buffer.position(4);

        // Serialize the message
        serialize(buffer, this);

        // Reset the buffer
        buffer.flip();

        // Determine and add the data length
        long dataLength = buffer.limit() - 4;

        buffer.put(long2bin(dataLength));

        // Return to position 0
        buffer.position(0);

        Log.v(TAG, "Serialized message length:  " + buffer.limit());

        return buffer;
    }

    protected void serialize(ByteBuffer buffer, Map<String, Object> map) {
        for (Entry<String, Object> entry : map.entrySet()) {
            serialize(buffer, entry.getKey(), entry.getValue());
        }
    }

    protected void serialize(ByteBuffer buffer, Iterable<?> list) {
        for (Object value : list) {
            // Lists are just like maps, but with empty / zero length keys.
            serialize(buffer, "", value);
        }
    }

    @SuppressWarnings("unchecked") // We cast LOTS here...
    protected void serialize(ByteBuffer buffer, String key, Object value) {
        byte[] keyBytes = key.getBytes();
        ByteBuffer valueBytes = ByteBuffer.allocate(65535);

        // 1 byte type
        if (value == null) {
            // Ignore and do nothing
            return;
        } else if (value instanceof String) {
            Log.v(TAG, "Serializaing a STR with key " + key + " value " + value);
            buffer.put(FIELD_STR);
            valueBytes.put(((String) value).getBytes());
        } else if (value instanceof BigInteger) {
            Log.v(TAG, "Serializaing a S64b with key " + key + " value " + value);
            buffer.put(FIELD_S64);
            valueBytes.put(toByteArray((BigInteger) value));
        } else if (value instanceof Integer) {
            Log.v(TAG, "Serializaing a S64i with key " + key + " value " + value);
            buffer.put(FIELD_S64);
            valueBytes.put(toByteArray(BigInteger.valueOf((Integer) value)));
        } else if (value instanceof Long) {
            Log.v(TAG, "Serializaing a S64l with key " + key + " value " + value);
            buffer.put(FIELD_S64);
            valueBytes.put(toByteArray(BigInteger.valueOf((Long) value)));
        } else if (value instanceof Map) {
            Log.v(TAG, "Serializaing a MAP with key " + key);
            buffer.put(FIELD_MAP);
            serialize(valueBytes, (Map<String, Object>) value);
        } else if (value instanceof byte[]) {
            Log.v(TAG, "Serializaing a BIN with key " + key);
            buffer.put(FIELD_BIN);
            valueBytes.put((byte[]) value);
        } else if (value instanceof Iterable) {
            Log.v(TAG, "Serializaing a LIST with key " + key);
            buffer.put(FIELD_LIST);
            serialize(valueBytes, (Iterable<?>) value);
        } else {
            throw new RuntimeException("Cannot serialize unknown data type, derp: " + value.getClass().getName());
        }

        // 1 byte key length
        buffer.put((byte) (keyBytes.length & 0xFF));

        // Reset the Value Buffer and grab it's length
        valueBytes.flip();
        int valueLength = valueBytes.limit();

        // 4 bytes value length
        buffer.put(long2bin(valueLength));

        // Key + Value Bytes
        buffer.put(keyBytes);
        buffer.put(valueBytes);
    }

    protected static HtspMessage deserialize(ByteBuffer buffer) {
        HtspMessage message = new HtspMessage();

        byte fieldType;
        String key;
        byte keyLength;
        byte[] valueLengthBytes = new byte[4];
        long valueLength;
        byte[] valueBytes;
        Object value = null;

        int listIndex = 0;

        while (buffer.hasRemaining()) {
            fieldType = buffer.get();
            keyLength = buffer.get();
            buffer.get(valueLengthBytes);
            valueLength = bin2long(valueLengthBytes);

            // Deserialize the Key
            if (keyLength == 0) {
                // Working on a list...
                key = Integer.toString(listIndex++);
            } else {
                // Working on a map..
                byte[] keyBytes = new byte[keyLength];
                buffer.get(keyBytes);
                key = new String(keyBytes);
            }

            // Extract Value bytes
            valueBytes = new byte[(int) valueLength];
            buffer.get(valueBytes);

            // Deserialize the Value
            if (fieldType == FIELD_STR) {
                Log.v(TAG, "Deserializaing a STR with key " + key);
                value = new String(valueBytes);

            } else if (fieldType == FIELD_S64) {
                Log.v(TAG, "Deserializaing a S64 with key " + key + " and valueBytes length " + valueBytes.length);
                value = toBigInteger(valueBytes);

            } else if (fieldType == FIELD_MAP) {
                Log.v(TAG, "Deserializaing a MAP with key " + key);
                ByteBuffer b = ByteBuffer.allocate(valueBytes.length);
                b.put(valueBytes);
                b.flip();
                value = deserialize(b);

            } else if (fieldType == FIELD_LIST) {
                Log.v(TAG, "Deserializaing a LIST with key " + key);
                ByteBuffer b = ByteBuffer.allocate(valueBytes.length);
                b.put(valueBytes);
                b.flip();
                value = new ArrayList<>(deserialize(b).values());

            } else if (fieldType == FIELD_BIN) {
                Log.v(TAG, "Deserializaing a BIN with key " + key);
                value = valueBytes;

            } else {
                throw new RuntimeException("Cannot deserialize unknown data type, derp: " + fieldType);
            }

            if (value != null) {
                message.put(key, value);
            }
        }

        return message;
    }

    private static byte[] long2bin(long l) {
        /**
         * return chr(i >> 24 & 0xFF) + chr(i >> 16 & 0xFF) + chr(i >> 8 & 0xFF) + chr(i & 0xFF)
         */
        byte[] result = new byte[4];

        result[0] = (byte) ((l >> 24) & 0xFF);
        result[1] = (byte) ((l >> 16) & 0xFF);
        result[2] = (byte) ((l >> 8) & 0xFF);
        result[3] = (byte) (l & 0xFF);

        return result;
    }

    private static long bin2long(byte[] bytes) {
        /**
         *  return (ord(d[0]) << 24) + (ord(d[1]) << 16) + (ord(d[2]) <<  8) + ord(d[3])
         */
        long result = 0;

        result ^= (bytes[0] & 0xFF) << 24;
        result ^= (bytes[1] & 0xFF) << 16;
        result ^= (bytes[2] & 0xFF) << 8;
        result ^= bytes[3] & 0xFF;

        return result;
    }

    private static byte[] toByteArray(BigInteger big) {
        byte[] b = big.toByteArray();
        byte b1[] = new byte[b.length];

        for (int i = 0; i < b.length; i++) {
            b1[i] = b[b.length - 1 - i];
        }

        return b1;
    }

    private static BigInteger toBigInteger(byte b[]) {
        byte b1[] = new byte[b.length + 1];

        for (int i = 0; i < b.length; i++) {
            b1[i + 1] = b[b.length - 1 - i];
        }

        return new BigInteger(b1);
    }

//    @Override
//    public String toString() {
//        StringBuilder builder = new StringBuilder();
//
//        return toString(builder).toString();
//    }
//
//    protected StringBuilder toString(StringBuilder builder) {
//        return builder;
//    }

    // Field Manipulation Methods
    public ArrayList getArrayList(String key) {
        Object value = get(key);

        return (ArrayList<String>) value;
    }

    public byte[] getByteArray(String key) {
        Object value = get(key);

        return (byte[]) value;
    }

    public Object[] getObjectArray(String key) {
        ArrayList value = getArrayList(key);

        return value.toArray();
    }

    public HtspMessage[] getHtspMessageArray(String key) {
        ArrayList value = getArrayList(key);

        return (HtspMessage[]) value.toArray(new HtspMessage[value.size()]);
    }

    public void putLong(String key, long value) {
        put(key, BigInteger.valueOf(value));
    }

    public long getLong(String key) {
        return ((BigInteger) get(key)).longValue();
    }

    public long getLong(String key, long defaultValue) {
        if (containsKey(key)) {
            return getLong(key);
        }

        return defaultValue;
    }

    public void putInt(String key, int value) {
        put(key, BigInteger.valueOf(value));
    }

    public int getInt(String key) {
        return ((BigInteger) get(key)).intValue();
    }

    public int getInt(String key, int defaultValue) {
        if (containsKey(key)) {
            return getInt(key);
        }

        return defaultValue;
    }

    public void putBoolean(String key, boolean value) {
        if (value) {
            put(key, 1);
        } else {
            put(key, 0);
        }
    }

    public boolean getBoolean(String key) {
        return getInt(key) == 1;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        if (containsKey(key)) {
            return getBoolean(key);
        }

        return defaultValue;
    }

    public void putString(String key, String value) {
        put(key, value);
    }

    public String getString(String key) {
        Object value = get(key);

        return (String) value;
    }

    public String getString(String key, String defaultValue) {
        if (containsKey(key)) {
            return getString(key);
        }

        return defaultValue;
    }

    public String[] getStringArray(String key) {
        ArrayList value = getArrayList(key);

        return (String[]) value.toArray(new String[value.size()]);
    }
}
