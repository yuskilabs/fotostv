package com.yuskilabs.fotostv;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;

/**
 * Preajustes del pase («Tranquilo», «Fiesta», «Solo recuerdos»). Se guardan en los ajustes de cada perfil.
 * Al aplicar uno se recuerda cómo estaba todo (solo la primera vez), para poder volver al estado inicial.
 */
final class Presets {
    private Presets() { }

    /** Preajuste elegido, o «» si no hay ninguno o se ha cambiado algo a mano (personalizado). */
    static String current(Context c) {
        SharedPreferences sp = Prefs.sp(c);
        String id = sp.getString(Extras.PRESET, "");
        if (id.length() == 0) return "";
        return FxLogic.presetMatches(sp.getAll(), id) ? id : "";
    }

    static boolean canRevert(Context c) { return Prefs.sp(c).getString(Extras.PRESET_BASE, "").length() > 0; }

    static void apply(Context c, String id) {
        Map<String, Object> vals = FxLogic.presetValues(id);
        if (vals.isEmpty()) return;
        SharedPreferences sp = Prefs.sp(c);
        SharedPreferences.Editor e = sp.edit();
        if (!canRevert(c)) e.putString(Extras.PRESET_BASE, FxLogic.encodeBase(sp.getAll(), vals.keySet()));
        for (Map.Entry<String, Object> en : vals.entrySet()) put(e, en.getKey(), en.getValue());
        e.putString(Extras.PRESET, id);
        e.commit();
    }

    /** Vuelve a como estaba todo antes del primer preajuste. */
    static void revert(Context c) {
        SharedPreferences sp = Prefs.sp(c);
        SharedPreferences.Editor e = sp.edit();
        for (Map.Entry<String, Object> en : FxLogic.decodeBase(sp.getString(Extras.PRESET_BASE, "")).entrySet()) {
            if (en.getValue() == null) e.remove(en.getKey());
            else put(e, en.getKey(), en.getValue());
        }
        e.remove(Extras.PRESET).remove(Extras.PRESET_BASE);
        e.commit();
    }

    private static void put(SharedPreferences.Editor e, String k, Object v) {
        if (v instanceof Boolean) e.putBoolean(k, (Boolean) v);
        else if (v instanceof Integer) e.putInt(k, (Integer) v);
        else if (v instanceof String) e.putString(k, (String) v);
    }
}
