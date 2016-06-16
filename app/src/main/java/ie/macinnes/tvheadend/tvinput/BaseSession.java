package ie.macinnes.tvheadend.tvinput;

import android.content.Context;
import android.media.tv.TvInputManager;
import android.util.Log;
import android.view.Surface;

import java.util.concurrent.atomic.AtomicInteger;

abstract public class BaseSession extends android.media.tv.TvInputService.Session {
    protected static final String TAG = BaseSession.class.getName();
    protected static AtomicInteger sSessionCounter = new AtomicInteger();

    protected final Context mContext;
    protected final int mSessionNumber;
    private final TvInputManager mTvInputManager;

    protected Surface mSurface;
    protected float mVolume;

    public BaseSession(Context context) {
        super(context);
        mContext = context;
        mSessionNumber = sSessionCounter.getAndIncrement();
        mTvInputManager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
    }
}
