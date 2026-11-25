package com.kreston8.islandcn;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Connected Bluetooth accessories and the battery level they report.
 *
 * <p>Android has no public battery API for accessories, so a level is hunted down in three steps,
 * cheapest first, and the first one that answers wins:
 *
 * <ol>
 *   <li>The hidden {@code BluetoothDevice.getBatteryLevel()} method, which is the same number the
 *       system's own Bluetooth screen prints. It is greylisted, so plenty of ROMs refuse it.</li>
 *   <li>The system's {@code BATTERY_LEVEL_CHANGED} broadcast, sent when a device pushes a level
 *       right after connecting. This is cached and persisted.</li>
 *   <li>A GATT read of the standard Battery Service ({@code 0x180F} / {@code 0x2A19}) for LE and
 *       dual-mode accessories. Classic-only headsets have no GATT server, so for those the first
 *       two steps are all there is.</li>
 * </ol>
 *
 * <p>A read costs the accessory a connection, so each device is asked at most once every ten
 * minutes and the connection is closed the moment the value arrives (or after nine seconds).
 * Levels are persisted, so a restart does not throw away what the system already told us.
 */
final class BtWatcher extends BroadcastReceiver {

    /** Hidden platform broadcast; there is no public constant for it. */
    private static final String ACTION_BATTERY = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED";
    private static final String EXTRA_DEVICE = "android.bluetooth.device.extra.DEVICE";
    private static final String EXTRA_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL";

    private static final String PREFS = "island_bt";
    private static final String PREFIX_LEVEL = "lv:";
    private static final String PREFIX_NAME = "nm:";

    /** Battery service and its level characteristic, the only standard way to ask a device. */
    private static final UUID SERVICE_BATTERY = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    private static final UUID CHAR_LEVEL = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
    /** A read opens a connection to the accessory, so it is not repeated more often than this. */
    private static final long ASK_GAP_MS = 10L * 60L * 1000L;
    private static final long ASK_TIMEOUT_MS = 9000L;

    private final Map<String, Integer> levels = new LinkedHashMap<>();
    private final Map<String, String> names = new LinkedHashMap<>();
    /** When each address was last asked over GATT, so a silent device is not asked every redraw. */
    private final Map<String, Long> askedAt = new LinkedHashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable onLevel;
    private boolean loaded;

    /** Called after a level arrives, so the chips on screen can be redrawn without a new card. */
    void levelListener(Runnable listener) {
        this.onLevel = listener;
    }

    void start(Context context) {
        load(context);
        IntentFilter filter = new IntentFilter(ACTION_BATTERY);
        try {
            Context app = context.getApplicationContext();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // A protected system broadcast: exported is correct, and unlike the alarm receiver
                // this one does not require the not-exported variant.
                app.registerReceiver(this, filter, Context.RECEIVER_EXPORTED);
            } else {
                app.registerReceiver(this, filter);
            }
        } catch (Throwable ignored) {
            // Registration can fail in a restricted profile; the chips then show icons only.
        }
    }

    void stop(Context context) {
        handler.removeCallbacksAndMessages(null);
        try {
            context.getApplicationContext().unregisterReceiver(this);
        } catch (Throwable ignored) {
            // Not registered, or the process is going away.
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        BluetoothDevice device = null;
        try {
            device = (BluetoothDevice) intent.getParcelableExtra(EXTRA_DEVICE);
        } catch (Throwable ignored) {
            // Malformed extra from a vendor stack.
        }
        int level = intent.getIntExtra(EXTRA_LEVEL, -1);
        if (device == null || level < 0) {
            return;
        }
        load(context);
        String address = addressOf(device);
        if (address.isEmpty()) {
            return;
        }
        remember(context, device, address, level);
    }

    /**
     * One entry per connected accessory, or null when there is nothing to show.
     *
     * <p>{@code GATT} covers watches and bands, {@code HEADSET} covers earbuds; other profiles never
     * carry a battery. Every platform call needs {@code BLUETOOTH_CONNECT} on Android 12+, so each
     * one is guarded instead of crashing the overlay.
     */
    List<IslandEvent.Bt> chips(Context context) {
        load(context);
        BluetoothManager manager;
        try {
            manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        } catch (Throwable failure) {
            return null;
        }
        if (manager == null) {
            return null;
        }
        BluetoothAdapter adapter;
        try {
            adapter = manager.getAdapter();
            if (adapter == null || !adapter.isEnabled()) {
                return null;
            }
        } catch (Throwable failure) {
            return null;
        }
        Map<String, BluetoothDevice> connected = new LinkedHashMap<>();
        collect(manager, BluetoothProfile.GATT, connected);
        collect(manager, BluetoothProfile.HEADSET, connected);
        if (connected.isEmpty()) {
            return null;
        }
        List<IslandEvent.Bt> chips = new ArrayList<>();
        for (BluetoothDevice device : connected.values()) {
            if (device == null) {
                continue;
            }
            String address = addressOf(device);
            String name = nameOf(context, device, address);
            Integer known = address.isEmpty() ? null : levels.get(address);
            if ((known == null || known < 0) && !address.isEmpty()) {
                int read = reflectLevel(device);
                if (read >= 0) {
                    levels.put(address, read);
                    persist(context, PREFIX_LEVEL + address, String.valueOf(read));
                    known = read;
                }
            }
            if (known == null || known < 0) {
                // Nothing cached, so ask the device itself; the answer arrives later and redraws.
                requestGatt(context, device, address);
            }
            chips.add(new IslandEvent.Bt(name, known == null ? -1 : known, iconOf(name)));
        }
        return chips.isEmpty() ? null : chips;
    }

    private void collect(BluetoothManager manager, int profile, Map<String, BluetoothDevice> into) {
        try {
            List<BluetoothDevice> devices = manager.getConnectedDevices(profile);
            if (devices == null) {
                return;
            }
            for (BluetoothDevice device : devices) {
                String address = addressOf(device);
                if (device != null && !address.isEmpty()) {
                    into.put(address, device);
                }
            }
        } catch (Throwable ignored) {
            // Missing BLUETOOTH_CONNECT, or a profile the ROM does not expose.
        }
    }

    /**
     * The level the platform already knows, read through a hidden method.
     *
     * <p>This is the number Settings shows, so it is tried before any connection is opened. The
     * method is greylisted: on most ROMs it either throws or answers -1, which is why the GATT
     * fallback exists at all.
     */
    private static int reflectLevel(BluetoothDevice device) {
        try {
            Object value = BluetoothDevice.class.getMethod("getBatteryLevel").invoke(device);
            if (value instanceof Integer) {
                return clamp((Integer) value);
            }
        } catch (Throwable ignored) {
            // Not exposed here, or blocked as a hidden API.
        }
        return -1;
    }

    /**
     * Reads the battery service over GATT.
     *
     * <p>Only LE and dual-mode devices answer; a classic headset has no GATT server, so its level
     * still depends on the broadcast. The result is delivered through {@link #onLevel}.
     */
    private void requestGatt(Context context, BluetoothDevice device, String address) {
        if (address.isEmpty() || !shouldAsk(System.currentTimeMillis(), address)) {
            return;
        }
        int type;
        try {
            type = device.getType();
        } catch (Throwable ignored) {
            return;
        }
        if (type != BluetoothDevice.DEVICE_TYPE_LE && type != BluetoothDevice.DEVICE_TYPE_DUAL) {
            return;
        }
        askedAt.put(address, System.currentTimeMillis());
        final Context app = context.getApplicationContext();
        final BluetoothGatt[] open = new BluetoothGatt[1];
        final boolean[] done = new boolean[1];
        BluetoothGattCallback callback = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    try {
                        gatt.discoverServices();
                        return;
                    } catch (Throwable ignored) {
                        // Fall through to the closed connection below.
                    }
                }
                if (newState != BluetoothProfile.STATE_CONNECTED) {
                    finish(app, gatt, open, done, address, -1);
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                BluetoothGattCharacteristic characteristic = levelOf(gatt);
                if (characteristic == null) {
                    finish(app, gatt, open, done, address, -1);
                    return;
                }
                try {
                    gatt.readCharacteristic(characteristic);
                } catch (Throwable ignored) {
                    finish(app, gatt, open, done, address, -1);
                }
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic,
                                             int status) {
                finish(app, gatt, open, done, address, levelFrom(characteristic));
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic,
                                             byte[] value, int status) {
                finish(app, gatt, open, done, address, levelOfBytes(value));
            }
        };
        try {
            BluetoothGatt gatt = device.connectGatt(app, false, callback, BluetoothDevice.TRANSPORT_LE);
            if (gatt == null) {
                return;
            }
            open[0] = gatt;
            handler.postDelayed(() -> finish(app, open[0], open, done, address, -1), ASK_TIMEOUT_MS);
        } catch (Throwable ignored) {
            // A refused connection leaves the previous value, or the icon-only chip, in place.
        }
    }

    /** Delivers one result and closes the connection; the first caller wins. */
    private void finish(Context context, BluetoothGatt gatt, BluetoothGatt[] open, boolean[] done,
                        String address, int level) {
        synchronized (done) {
            if (done[0]) {
                return;
            }
            done[0] = true;
        }
        if (level >= 0) {
            levels.put(address, level);
            persist(context, PREFIX_LEVEL + address, String.valueOf(level));
            notifyLevel();
        }
        BluetoothGatt target = gatt != null ? gatt : open[0];
        open[0] = null;
        if (target != null) {
            try {
                target.disconnect();
            } catch (Throwable ignored) {
                // Already gone.
            }
            try {
                target.close();
            } catch (Throwable ignored) {
                // Already closed.
            }
        }
    }

    private boolean shouldAsk(long now, String address) {
        Long last = askedAt.get(address);
        return last == null || now - last >= ASK_GAP_MS;
    }

    private static BluetoothGattCharacteristic levelOf(BluetoothGatt gatt) {
        try {
            BluetoothGattService service = gatt.getService(SERVICE_BATTERY);
            return service == null ? null : service.getCharacteristic(CHAR_LEVEL);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int levelFrom(BluetoothGattCharacteristic characteristic) {
        try {
            Integer value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            return value == null ? -1 : clamp(value);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static int levelOfBytes(byte[] value) {
        if (value == null || value.length == 0) {
            return -1;
        }
        return clamp(value[0] & 0xFF);
    }

    private static int clamp(int value) {
        return value < 0 || value > 100 ? -1 : value;
    }

    private String nameOf(Context context, BluetoothDevice device, String address) {
        String cached = address.isEmpty() ? null : names.get(address);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        String name = null;
        try {
            name = device.getName();
        } catch (Throwable ignored) {
            // Requires BLUETOOTH_CONNECT on Android 12+.
        }
        if (name == null || name.trim().isEmpty()) {
            name = context.getString(R.string.bt_unknown);
        } else {
            name = name.trim();
            if (!address.isEmpty()) {
                names.put(address, name);
                persist(context, PREFIX_NAME + address, name);
            }
        }
        return name;
    }

    private void remember(Context context, BluetoothDevice device, String address, int level) {
        Integer previous = levels.put(address, level);
        // The name usually arrives with the same broadcast, so keep it while we are here.
        nameOf(context, device, address);
        if (previous != null && previous == level) {
            return;
        }
        persist(context, PREFIX_LEVEL + address, String.valueOf(level));
        notifyLevel();
    }

    private void notifyLevel() {
        Runnable listener = onLevel;
        if (listener == null) {
            return;
        }
        try {
            listener.run();
        } catch (Throwable ignored) {
            // A dead service must not take the receiver down with it.
        }
    }

    private static int iconOf(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (lower.contains("watch") || lower.contains("band") || lower.contains("手表")
                || lower.contains("手环") || lower.contains("fit")) {
            return R.drawable.ic_bt_watch;
        }
        if (lower.contains("mouse") || lower.contains("鼠标")) {
            return R.drawable.ic_bt_mouse;
        }
        if (lower.contains("buds") || lower.contains("pods") || lower.contains("headset")
                || lower.contains("headphone") || lower.contains("earbud") || lower.contains("耳机")
                || lower.contains("sound") || lower.contains("speaker")) {
            return R.drawable.ic_bt_headset;
        }
        return R.drawable.ic_bt_generic;
    }

    private static String addressOf(BluetoothDevice device) {
        try {
            String address = device.getAddress();
            return address == null ? "" : address;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private void load(Context context) {
        if (loaded) {
            return;
        }
        loaded = true;
        SharedPreferences store;
        try {
            store = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        } catch (Throwable failure) {
            return;
        }
        try {
            for (Map.Entry<String, ?> entry : store.getAll().entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (key == null || !(value instanceof String)) {
                    continue;
                }
                String text = (String) value;
                if (key.startsWith(PREFIX_LEVEL)) {
                    try {
                        levels.put(key.substring(PREFIX_LEVEL.length()), Integer.parseInt(text));
                    } catch (NumberFormatException ignored) {
                        // A stale value; the next reading replaces it.
                    }
                } else if (key.startsWith(PREFIX_NAME)) {
                    names.put(key.substring(PREFIX_NAME.length()), text);
                }
            }
        } catch (Throwable ignored) {
            // A corrupt file only costs the numbers, not the overlay.
        }
    }

    private static void persist(Context context, String key, String value) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(key, value).apply();
        } catch (Throwable ignored) {
            // Read-only profile: the in-memory copy still drives this run.
        }
    }
}
