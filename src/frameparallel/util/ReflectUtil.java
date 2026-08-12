package frameparallel.util;

import arc.util.*;
import java.lang.reflect.Field;

public class ReflectUtil {

    @SuppressWarnings("unchecked")
    public static <T> T getPrivate(Object target, String fieldName) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return (T) f.get(target);
        } catch (Exception e) {
            Log.err("[FrameParallel] Failed to get private field: " + fieldName, e);
            return null;
        }
    }

    public static boolean setPrivate(Object target, String fieldName, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
            return true;
        } catch (Exception e) {
            Log.err("[FrameParallel] Failed to set private field: " + fieldName, e);
            return false;
        }
    }
}
