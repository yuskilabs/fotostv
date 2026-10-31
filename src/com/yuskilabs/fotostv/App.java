package com.yuskilabs.fotostv;

import android.app.Application;
import android.graphics.Typeface;

/** Carga las tipografías de la app (Quicksand) una vez, al arrancar el proceso. */
public class App extends Application {
    @Override public void onCreate() {
        super.onCreate();
        try {
            Ui.BLACK = Typeface.createFromAsset(getAssets(), "fonts/Quicksand-Bold.ttf");
            Ui.BTN = Typeface.createFromAsset(getAssets(), "fonts/Quicksand-Medium.ttf");
            Ui.REG = Typeface.createFromAsset(getAssets(), "fonts/Quicksand-Regular.ttf");
        } catch (RuntimeException e) { /* si falla, se queda la fuente de Android */ }
    }
}
