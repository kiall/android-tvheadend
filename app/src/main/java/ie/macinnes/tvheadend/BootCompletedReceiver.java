package ie.macinnes.tvheadend;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import ie.macinnes.tvheadend.sync.EpgSyncService;
import ie.macinnes.tvheadend.tvinput.TvInputService;

public class BootCompletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            context.startService(new Intent(context, TvInputService.class));
            context.startService(new Intent(context, EpgSyncService.class));
        }
    }
}
