package org.telegram.ui.Components.voip2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.BlobDrawable;
import org.telegram.ui.Components.WaveDrawable;

public class VoIPWavesContainer extends FrameLayout {

    private BlobDrawable smallWaveDrawable;
    private BlobDrawable bigWaveDrawable;

    private int deltaCY = 0;

    private final Runnable wavesKicker = new Runnable() {
        @Override
        public void run() {
            invalidate();
            AndroidUtilities.runOnUIThread(wavesKicker, 1000 / 60);  // 60 fps
        }
    };

    public VoIPWavesContainer(@NonNull Context context) {
        super(context);

        smallWaveDrawable = new BlobDrawable(11);
        smallWaveDrawable.minRadius = AndroidUtilities.dp(36);
        smallWaveDrawable.maxRadius = AndroidUtilities.dp(40);
        smallWaveDrawable.generateBlob();
        smallWaveDrawable.paint.setColor(Color.argb(36, 255, 255, 255));

        bigWaveDrawable = new BlobDrawable(12);
        bigWaveDrawable.minRadius = AndroidUtilities.dp(44);
        bigWaveDrawable.maxRadius = AndroidUtilities.dp(48);
        bigWaveDrawable.generateBlob();
        bigWaveDrawable.paint.setColor(Color.argb(20, 255, 255, 255));

        setWillNotDraw(false);
    }

    public void setWavesRadius(float smallMin, float smallMax, float bigMin, float bigMax) {
        smallWaveDrawable.minRadius = AndroidUtilities.dp(smallMin);
        smallWaveDrawable.maxRadius = AndroidUtilities.dp(smallMax);
        smallWaveDrawable.generateBlob();

        bigWaveDrawable.minRadius = AndroidUtilities.dp(bigMin);
        bigWaveDrawable.maxRadius = AndroidUtilities.dp(bigMax);
        bigWaveDrawable.generateBlob();

        invalidate();
    }

    public void setWavesDeltaCY(float deltaCY) {
        this.deltaCY = AndroidUtilities.dp(deltaCY);

        invalidate();
    }

    public void setAmpl(float ampl) {
        ampl = (float) (Math.min(WaveDrawable.MAX_AMPLITUDE, ampl) / WaveDrawable.MAX_AMPLITUDE);

        smallWaveDrawable.setValue(ampl, false);
        //smallWaveDrawable.updateAmplitude(16);

        bigWaveDrawable.setValue(ampl, true);
        //bigWaveDrawable.updateAmplitude(16);

        invalidate();
    }

    public void playAnimation(boolean play) {
        if (play) {
            AndroidUtilities.runOnUIThread(wavesKicker);
        } else {
            AndroidUtilities.cancelRunOnUIThread(wavesKicker);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int cx = getMeasuredWidth() / 2;
        int cy = (getMeasuredHeight() - deltaCY) / 2;

        smallWaveDrawable.updateAmplitude(16);
        smallWaveDrawable.update(smallWaveDrawable.amplitude, 1);
        smallWaveDrawable.draw(cx, cy, canvas, smallWaveDrawable.paint);

        bigWaveDrawable.updateAmplitude(16);
        bigWaveDrawable.update(bigWaveDrawable.amplitude, 1);
        bigWaveDrawable.draw(cx, cy, canvas, bigWaveDrawable.paint);

        super.dispatchDraw(canvas);
    }
}
