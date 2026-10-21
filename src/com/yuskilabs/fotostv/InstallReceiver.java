package com.yuskilabs.fotostv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

/** Recibe el resultado de la instalación (y abre la pantalla de confirmación de Android). */
public class InstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { c.startActivity(confirm); } catch (Exception e) {
                    Toast.makeText(c, "No se pudo abrir el instalador de Android.", Toast.LENGTH_LONG).show();
                }
            }
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            Toast.makeText(c, "FotosTV actualizada.", Toast.LENGTH_LONG).show();
        } else if (status == PackageInstaller.STATUS_FAILURE_BLOCKED) {
            Toast.makeText(c, "Android bloquea la instalación: activa «Permitir de esta fuente» para FotosTV y vuelve a intentarlo.", Toast.LENGTH_LONG).show();
            Updater.openInstallSettings(c);
        } else {
            String msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            Toast.makeText(c, "No se pudo instalar" + (msg != null ? ": " + msg : "."), Toast.LENGTH_LONG).show();
        }
    }
}
