package edu.osu.pcv.marslogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class for providing content for ImuViewFragment.
 *
 */
public class ImuViewContent {

    public static final List<SingleAxis> ITEMS = new ArrayList<SingleAxis>();

    /**
     * A map of items, by ID.
     */
    public static final Map<String, SingleAxis> ITEM_MAP = new HashMap<String, SingleAxis>();

    static {
        addItem(new SingleAxis("Accel X", String.valueOf(0.0),
                "X axis of accelerometer", "m/s<sup><small>2</small></sup>"));
        addItem(new SingleAxis( "Accel Y", String.valueOf(0.0),
                "Y axis of accelerometer", "m/s<sup><small>2</small></sup>"));
        addItem(new SingleAxis( "Accel Z", String.valueOf(0.0),
                "Z axis of accelerometer", "m/s<sup><small>2</small></sup>"));

        addItem(new SingleAxis( "Gyro X", String.valueOf(0.0),
                "X axis of gyroscope", "rad/s"));
        addItem(new SingleAxis( "Gyro Y", String.valueOf(0.0),
                "Y axis of gyroscope", "rad/s"));
        addItem(new SingleAxis( "Gyro Z", String.valueOf(0.0),
                "Z axis of gyroscope", "rad/s"));

        addItem(new SingleAxis("Mag X", String.valueOf(0.0),
                "X axis of magnetometer", "&mu T"));
        addItem(new SingleAxis( "Mag Y", String.valueOf(0.0),
                "Y axis of magnetometer", "&mu T"));
        addItem(new SingleAxis("Mag Z", String.valueOf(0.0),
                "Z axis of magnetometer", "&mu T"));
    }

    private static void addItem(SingleAxis item) {
        ITEMS.add(item);
        ITEM_MAP.put(item.id, item);
    }

    public static class SingleAxis {
        public final String id;
        public String content;
        public final String details;
        public final String unit;

        public SingleAxis(String id, String content, String details, String unit) {
            this.id = id;
            this.content = content;
            this.details = details;
            this.unit = unit;
        }

        @Override
        public String toString() {
            return content;
        }
    }
}
