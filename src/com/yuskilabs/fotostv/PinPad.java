package com.yuskilabs.fotostv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Código de 4 cifras de un perfil, tecleado solo con el mando: arriba/abajo cambian la cifra, derecha/izquierda se mueven, OK confirma. */
class PinPad {
    interface OnCode { void code(String code); }

    /** Pide el código del perfil (si lo tiene) y, si es correcto, ejecuta «ok». Sin código, ejecuta «ok» directamente. */
    static void enter(final Activity a, final int id, final String name, final Runnable ok) {
        if (!Prefs.hasCode(a, id)) { ok.run(); return; }
        tryEnter(a, id, name, ok, 0);
    }

    private static void tryEnter(final Activity a, final int id, final String name, final Runnable ok, final int fails) {
        pad(a, "Código de «" + name + "»", fails == 0 ? "" : "Código incorrecto. Prueba otra vez.", new OnCode() {
            public void code(String c) {
                if (Prefs.checkCode(a, id, c)) { ok.run(); return; }
                if (fails >= 2) {
                    Toast.makeText(a, "Código incorrecto. Demasiados intentos.", Toast.LENGTH_LONG).show();
                    return;
                }
                tryEnter(a, id, name, ok, fails + 1);
            }
        });
    }

    /** Pone un código nuevo: se teclea dos veces para evitar errores. Después ejecuta «done». */
    static void setNew(final Activity a, final int id, final String name, final Runnable done) {
        pad(a, "Código nuevo para «" + name + "»", "Elige 4 cifras.", new OnCode() {
            public void code(final String first) {
                pad(a, "Repite el código", "Las mismas 4 cifras.", new OnCode() {
                    public void code(String second) {
                        if (!first.equals(second)) {
                            Toast.makeText(a, "No coinciden. No se ha cambiado nada.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        Prefs.setCode(a, id, first);
                        Toast.makeText(a, "Código de «" + name + "» guardado.", Toast.LENGTH_SHORT).show();
                        if (done != null) done.run();
                    }
                });
            }
        });
    }

    /** Teclado de 4 cifras en un diálogo. Atrás cancela. */
    private static void pad(final Activity a, String title, String msg, final OnCode cb) {
        final int[] dig = new int[4];
        final int[] pos = new int[]{0};
        final TextView[] box = new TextView[4];
        final float d = a.getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding((int) (24 * d), (int) (8 * d), (int) (24 * d), (int) (8 * d));

        TextView m = new TextView(a);
        m.setText(msg.length() > 0 ? msg : " ");
        m.setTextColor(0xFFFFB4A9);
        m.setTextSize(14);
        m.setGravity(Gravity.CENTER);
        root.addView(m, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        for (int i = 0; i < 4; i++) {
            TextView t = new TextView(a);
            t.setTextColor(Color.WHITE);
            t.setTextSize(30);
            t.setGravity(Gravity.CENTER);
            box[i] = t;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (56 * d), (int) (64 * d));
            lp.setMargins((int) (6 * d), (int) (12 * d), (int) (6 * d), (int) (12 * d));
            row.addView(t, lp);
        }
        root.addView(row, new LinearLayout.LayoutParams(-2, -2));

        TextView h = new TextView(a);
        h.setText("Arriba y abajo: cifra. Derecha: siguiente. OK: confirmar. Atrás: cancelar.");
        h.setTextColor(0xFFB8C4D6);
        h.setTextSize(13);
        h.setGravity(Gravity.CENTER);
        root.addView(h, new LinearLayout.LayoutParams(-1, -2));

        final Runnable paint = new Runnable() {
            public void run() {
                for (int i = 0; i < 4; i++) {
                    box[i].setText(i == pos[0] ? String.valueOf(dig[i]) : "•");
                    GradientDrawable g = new GradientDrawable();
                    g.setCornerRadius(10 * d);
                    g.setColor(i == pos[0] ? 0x33FFFFFF : 0x1AFFFFFF);
                    g.setStroke((int) ((i == pos[0] ? 3 : 1) * d), i == pos[0] ? 0xFF3D8BFD : 0x33FFFFFF);
                    box[i].setBackground(g);
                }
            }
        };
        paint.run();

        final AlertDialog dlg = new AlertDialog.Builder(a, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle(title)
            .setView(root)
            .create();
        dlg.setOnKeyListener(new DialogInterface.OnKeyListener() {
            public boolean onKey(DialogInterface di, int key, KeyEvent e) {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return key != KeyEvent.KEYCODE_BACK && isOurs(key);
                if (key == KeyEvent.KEYCODE_DPAD_UP) { dig[pos[0]] = (dig[pos[0]] + 1) % 10; paint.run(); return true; }
                if (key == KeyEvent.KEYCODE_DPAD_DOWN) { dig[pos[0]] = (dig[pos[0]] + 9) % 10; paint.run(); return true; }
                if (key == KeyEvent.KEYCODE_DPAD_LEFT) { if (pos[0] > 0) pos[0]--; paint.run(); return true; }
                if (key == KeyEvent.KEYCODE_DPAD_RIGHT) { if (pos[0] < 3) pos[0]++; paint.run(); return true; }
                if (key >= KeyEvent.KEYCODE_0 && key <= KeyEvent.KEYCODE_9) {   // por si hay teclado
                    dig[pos[0]] = key - KeyEvent.KEYCODE_0;
                    if (pos[0] < 3) pos[0]++;
                    paint.run();
                    return true;
                }
                if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                    if (pos[0] < 3) { pos[0]++; paint.run(); return true; }
                    di.dismiss();
                    cb.code("" + dig[0] + dig[1] + dig[2] + dig[3]);
                    return true;
                }
                return false;
            }
        });
        dlg.show();
    }

    private static boolean isOurs(int key) {
        return key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN || key == KeyEvent.KEYCODE_DPAD_LEFT
            || key == KeyEvent.KEYCODE_DPAD_RIGHT || key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
            || key == KeyEvent.KEYCODE_NUMPAD_ENTER || (key >= KeyEvent.KEYCODE_0 && key <= KeyEvent.KEYCODE_9);
    }
}
