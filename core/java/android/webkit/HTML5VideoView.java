
package android.webkit;

import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.SurfaceView;
import android.webkit.HTML5VideoViewProxy;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * @hide This is only used by the browser
 */
public class HTML5VideoView implements MediaPlayer.OnPreparedListener{

    protected static final String LOGTAG = "HTML5VideoView";

    protected static final String COOKIE = "Cookie";
    protected static final String HIDE_URL_LOGS = "x-hide-urls-from-log";

    // For handling the seekTo before prepared, we need to know whether or not
    // the video is prepared. Therefore, we differentiate the state between
    // prepared and not prepared.
    // When the video is not prepared, we will have to save the seekTo time,
    // and use it when prepared to play.
    protected static final int STATE_NOTPREPARED        = 0;
    protected static final int STATE_PREPARED           = 1;

    protected int mCurrentState;

    protected HTML5VideoViewProxy mProxy;

    // Save the seek time when not prepared. This can happen when switching
    // video besides initial load.
    protected int mSaveSeekTime;

    // This is used to find the VideoLayer on the native side.
    protected int mVideoLayerId;

    // Every video will have one MediaPlayer. Given the fact we only have one
    // SurfaceTexture, there is only one MediaPlayer in action. Every time we
    // switch videos, a new instance of MediaPlayer will be created in reset().
    // Switching between inline and full screen will also create a new instance.
    protected MediaPlayer mPlayer;

    // This will be set up every time we create the Video View object.
    // Set to true only when switching into full screen while playing
    protected boolean mAutostart;

    // We need to save such info.
    protected String mUri;
    protected Map<String, String> mHeaders;

    // The timer for timeupate events.
    // See http://www.whatwg.org/specs/web-apps/current-work/#event-media-timeupdate
    protected static Timer mTimer;

    // The spec says the timer should fire every 250 ms or less.
    private static final int TIMEUPDATE_PERIOD = 250;  // ms

    // common Video control FUNCTIONS:
    public void start() {
        if (mCurrentState == STATE_PREPARED) {
            mPlayer.start();
        }
    }

    public void pause() {
        if (mCurrentState == STATE_PREPARED && mPlayer.isPlaying()) {
            mPlayer.pause();
        }
        if (mTimer != null) {
            mTimer.purge();
        }
    }

    public int getDuration() {
        if (mCurrentState == STATE_PREPARED) {
            return mPlayer.getDuration();
        } else {
            return -1;
        }
    }

    public int getCurrentPosition() {
        if (mCurrentState == STATE_PREPARED) {
            return mPlayer.getCurrentPosition();
        }
        return 0;
    }

    public void seekTo(int pos) {
        if (mCurrentState == STATE_PREPARED)
            mPlayer.seekTo(pos);
        else
            mSaveSeekTime = pos;
    }

    public boolean isPlaying() {
        return mPlayer.isPlaying();
    }

    public void release() {
        mPlayer.release();
    }

    public void stopPlayback() {
        if (mCurrentState == STATE_PREPARED) {
            mPlayer.stop();
        }
    }

    public boolean getAutostart() {
        return mAutostart;
    }

    // Every time we start a new Video, we create a VideoView and a MediaPlayer
    public void init(int videoLayerId, int position, boolean autoStart) {
        mPlayer = new MediaPlayer();
        mCurrentState = STATE_NOTPREPARED;
        mProxy = null;
        mVideoLayerId = videoLayerId;
        mSaveSeekTime = position;
        mAutostart = autoStart;
    }

    protected HTML5VideoView() {
    }

    protected static Map<String, String> generateHeaders(String url,
            HTML5VideoViewProxy proxy) {
        boolean isPrivate = proxy.getWebView().isPrivateBrowsingEnabled();
        String cookieValue = CookieManager.getInstance().getCookie(url, isPrivate);
        Map<String, String> headers = new HashMap<String, String>();
        if (cookieValue != null) {
            headers.put(COOKIE, cookieValue);
        }
        if (isPrivate) {
            headers.put(HIDE_URL_LOGS, "true");
        }

        return headers;
    }

    public void setVideoURI(String uri, HTML5VideoViewProxy proxy) {
        // When switching players, surface texture will be reused.
        mUri = uri;
        mHeaders = generateHeaders(uri, proxy);

        mTimer = new Timer();
    }

    // Listeners setup FUNCTIONS:
    public void setOnCompletionListener(HTML5VideoViewProxy proxy) {
        mPlayer.setOnCompletionListener(proxy);
    }

    public void setOnErrorListener(HTML5VideoViewProxy proxy) {
        mPlayer.setOnErrorListener(proxy);
    }

    public void setOnPreparedListener(HTML5VideoViewProxy proxy) {
        mProxy = proxy;
        mPlayer.setOnPreparedListener(this);
    }

    // Normally called immediately after setVideoURI. But for full screen,
    // this should be after surface holder created
    public void prepareDataAndDisplayMode(HTML5VideoViewProxy proxy) {
        // SurfaceTexture will be created lazily here for inline mode
        decideDisplayMode();

        setOnCompletionListener(proxy);
        setOnPreparedListener(proxy);
        setOnErrorListener(proxy);

        // When there is exception, we could just bail out silently.
        // No Video will be played though. Write the stack for debug
        try {
            mPlayer.setDataSource(mUri, mHeaders);
            mPlayer.prepareAsync();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // Common code
    public int getVideoLayerId() {
        return mVideoLayerId;
    }

    private static final class TimeupdateTask extends TimerTask {
        private HTML5VideoViewProxy mProxy;

        public TimeupdateTask(HTML5VideoViewProxy proxy) {
            mProxy = proxy;
        }

        @Override
        public void run() {
            mProxy.onTimeupdate();
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        mCurrentState = STATE_PREPARED;
        seekTo(mSaveSeekTime);
        if (mProxy != null)
            mProxy.onPrepared(mp);

        mTimer.schedule(new TimeupdateTask(mProxy), TIMEUPDATE_PERIOD, TIMEUPDATE_PERIOD);

    }

    // Pause the play and update the play/pause button
    public void pauseAndDispatch(HTML5VideoViewProxy proxy) {
        if (isPlaying()) {
            pause();
            if (proxy != null) {
                proxy.dispatchOnPaused();
            }
        }
    }

    // Below are functions that are different implementation on inline and full-
    // screen mode. Some are specific to one type, but currently are called
    // directly from the proxy.
    public void enterFullScreenVideoState(int layerId,
            HTML5VideoViewProxy proxy, WebView webView) {
    }

    public boolean isFullScreenMode() {
        return false;
    }

    public SurfaceView getSurfaceView() {
        return null;
    }

    public void decideDisplayMode() {
    }

    public void prepareForFullScreen() {
    }

    public boolean getReadyToUseSurfTex() {
        return false;
    }

    public SurfaceTexture getSurfaceTexture() {
        return null;
    }

    public void deleteSurfaceTexture() {
    }

    public int getTextureName() {
        return 0;
    }

}
