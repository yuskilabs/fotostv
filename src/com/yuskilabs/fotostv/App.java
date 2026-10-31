package com.yuskilabs.fotostv;

import android.app.Application;
import android.graphics.Typeface;

/** Carga las tipografías de la app (Figtree) una vez, al arrancar el proceso. */
public class App extends Application {
    @Override public void onCreate() {
        super.onCreate();
        try {
            Ui.BLACK = Typeface.createFromAsset(getAssets(), "fonts/Figtree-ExtraBold.ttf");
            Ui.BTN = Typeface.createFromAsset(getAssets(), "fonts/Figtree-Regular.ttf");
            Ui.HAND = Typeface.createFromAsset(getAssets(), "fonts/Caveat-SemiBold.ttf");
            Ui.REG = Typeface.createFromAsset(getAssets(), "fonts/Figtree-Regular.ttf");
        } catch (RuntimeException e) { /* si falla, se queda la fuente de Android */ }
    }
}
