package com.kreston8.islandcn;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;

import android.os.Build;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Connected Bluetooth accessories and the battery level they last reported.
 *
 * <p>Android has no public battery API for accessories. The only source is the platform's own
 * {@code BATTERY_LEVEL_CHANGED} broadcast, which the system sends when a device pushes its level
 * (typically right after connecting, and then every so often). Two consequences are visible in the
 * UI on purpose:
 *
 * <ul>
 *   <li>A device that has never reported keeps its icon and shows {@code --}; the level is never
 *       guessed, because a wrong percentage on a watch is worse than none.</li>
 *   <li>After a fresh install the numbers only appear once a device reports again, so the first
 *       connect after installing may be icon-only for a while.</li>
 * </ul>
 *
 * <p>Levels are persisted so a reboot or a service restart does not throw away what the system
 * already told us. Only a handful of entries are ever stored.
 */
final class BtWatcher extends BroadcastReceiver {

    /** Hidden platform broadcast; there is no public constant for it. */
    private static final String ACTION_BATTERY = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED";
    private static final String EXTRA_DEVICE = "android.bluetooth.device.extra.DEVICE";
    private static final String EXTRA_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL";

    private static final String PREFS = "island_bt";
    private static final String PREFIX_LEVEL = "lv:";
    private static final String PREFIX_NAME = "nm:";

    private final Map<String, Integer> levels = new LinkedHashMap<>();
    private final Map<String, String> names = new LinkedHashMap<>();
    private boolean loaded;

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
     * carry a battery. Both calls need {@code BLUETOOTH_CONNECT} on Android 12+, so every lookup is
     * guarded instead of crashing the overlay.
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
            chips.add(new IslandEvent.Bt(
                    name, known == null ? -1 : known, iconOf(name)));
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
        levels.put(address, level);
        persist(context, PREFIX_LEVEL + address, String.valueOf(level));
        // The name usually arrives with the same broadcast, so keep it while we are here.
        nameOf(context, device, address);
    }

    private static int iconOf(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (lower.contains("watch") || lower.contains("band") || lower.contains("手表")
                || lower.contains("手环") || lower.contains("fit")) {
            return R.drawable.ic_bt_watch;
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
                        // A stale value; the next broadcast replaces it.
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
