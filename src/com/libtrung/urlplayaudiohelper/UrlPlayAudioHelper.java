package com.libtrung.urlplayaudiohelper;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.widget.ImageView;
import junit.framework.Assert;
import org.apache.http.NameValuePair;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;

public final class UrlPlayAudioHelper {
    static void clog(String format, Object... args) {
        String log;
        if (args.length == 0)
            log = format;
        else
            log = String.format(format, args);
        if (Constants.LOG_ENABLED)
            Log.i(Constants.LOGTAG, log);
    }

    public static int copyStream(final InputStream input, final OutputStream output) throws IOException {
        final byte[] stuff = new byte[1024];
        int read = 0;
        int total = 0;
        while ((read = input.read(stuff)) != -1) {
            output.write(stuff, 0, read);
            total += read;
        }
        return total;
    }

    static Resources mResources;
    static DisplayMetrics mMetrics;

    private static void prepareResources(final Context context) {
        if (mMetrics != null) {
            return;
        }
        mMetrics = new DisplayMetrics();
        //final Activity act = (Activity)context;
        //act.getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
        ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay().getMetrics(mMetrics);
        final AssetManager mgr = context.getAssets();
        mResources = new Resources(mgr, mMetrics, context.getResources().getConfiguration());
    }

    private static boolean mUseBitmapScaling = true;

    /**
     * Bitmap scaling will use smart/sane values to limit the maximum
     * dimension of the bitmap during decode. This will prevent any dimension of the
     * bitmap from being larger than the dimensions of the device itself.
     * Doing this will conserve memory.
     *
     * @param useBitmapScaling Toggle for smart resizing.
     */
    public static void setUseBitmapScaling(boolean useBitmapScaling) {
        mUseBitmapScaling = useBitmapScaling;
    }

    /**
     * Bitmap scaling will use smart/sane values to limit the maximum
     * dimension of the bitmap during decode. This will prevent any dimension of the
     * bitmap from being larger than the dimensions of the device itself.
     * Doing this will conserve memory.
     */
    public static boolean getUseBitmapScaling() {
        return mUseBitmapScaling;
    }

    public static final int CACHE_DURATION_INFINITE = Integer.MAX_VALUE;
    public static final int CACHE_DURATION_ONE_DAY = 1000 * 60 * 60 * 24;
    public static final int CACHE_DURATION_TWO_DAYS = CACHE_DURATION_ONE_DAY * 2;
    public static final int CACHE_DURATION_THREE_DAYS = CACHE_DURATION_ONE_DAY * 3;
    public static final int CACHE_DURATION_FOUR_DAYS = CACHE_DURATION_ONE_DAY * 4;
    public static final int CACHE_DURATION_FIVE_DAYS = CACHE_DURATION_ONE_DAY * 5;
    public static final int CACHE_DURATION_SIX_DAYS = CACHE_DURATION_ONE_DAY * 6;
    public static final int CACHE_DURATION_ONE_WEEK = CACHE_DURATION_ONE_DAY * 7;

    /**
     * Download and shrink an Image located at a specified URL, and display it
     * in the provided {@link ImageView}.
     *
     * @param url The URL of the image that should be loaded.
     */
    public static void setUrlMediaPlayer(Context mContext, final String url) {
        setUrlDrawable(mContext, url, CACHE_DURATION_ONE_DAY);
    }

    private static boolean isNullOrEmpty(final CharSequence s) {
        return (s == null || s.equals("") || s.equals("null") || s.equals("NULL"));
    }

    private static boolean mHasCleaned = false;

    public static String getFilenameForUrl(final String url) {
        return url.hashCode() + ".urlimage";
    }

    /**
     * Clear out cached images.
     *
     * @param context
     * @param age     The max age of a file. Files older than this age
     *                will be removed.
     */
    public static void cleanup(final Context context, long age) {
        if (mHasCleaned) {
            return;
        }
        mHasCleaned = true;
        try {
            // purge any *.urlimage files over a week old
            final String[] files = context.getFilesDir().list();
            if (files == null) {
                return;
            }
            for (final String file : files) {
                if (!file.endsWith(".urlimage")) {
                    continue;
                }

                final File f = new File(context.getFilesDir().getAbsolutePath() + '/' + file);
                if (System.currentTimeMillis() > f.lastModified() + CACHE_DURATION_ONE_WEEK) {
                    f.delete();
                }
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Clear out all cached images older than a week.
     * The same as calling cleanup(context, CACHE_DURATION_ONE_WEEK);
     *
     * @param context
     */
    public static void cleanup(final Context context) {
        cleanup(context, CACHE_DURATION_ONE_WEEK);
    }

    private static boolean checkCacheDuration(File file, long cacheDurationMs) {
        return cacheDurationMs == CACHE_DURATION_INFINITE || System.currentTimeMillis() < file.lastModified() + cacheDurationMs;
    }


    /**
     * Download and shrink an Image located at a specified URL, and display it
     * in the provided {@link ImageView}.
     *
     * @param context         A {@link Context} to allow setUrlDrawable to load and save
     * files.
     * @param mediaPlayer     The {@link MediaPlayer} to display the image to after it
     * is loaded.
     * @param url             The URL of the image that should be loaded.
     * @param cacheDurationMs The length of time, in milliseconds, that this
     * image should be cached locally.
     */
    static MediaPlayer mediaPlayer;

    private static void setUrlDrawable(final Context context, final String url, final long cacheDurationMs) {
        Assert.assertTrue("setUrlDrawable and loadUrlDrawable should only be called from the main thread.", Looper.getMainLooper().getThread() == Thread.currentThread());
        cleanup(context);
        // disassociate this ImageView from any pending downloads
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
        }
        if (isNullOrEmpty(url)) {
            if (mediaPlayer != null) {
                mPendingViews.remove(mediaPlayer);
            }
            return;
        }

        final String filename = context.getFileStreamPath(getFilenameForUrl(url)).getAbsolutePath();
        final File file = new File(filename);

        if (mDeadCache == null) {
            mDeadCache = new UrlLruCache(getHeapSize(context) / 8);
        }
        StringURL ObjectCache;
        final String bd = mDeadCache.remove(url);
        if (bd != null) {
            // this drawable was resurrected, it should not be in the live cache
            clog("zombie load: " + url);
//            Assert.assertTrue(url, !mAllCache.contains(bd));
            ObjectCache = new ZombieObject(url, bd);
        } else {
            ObjectCache = mLiveCache.get(url);
        }

        if (ObjectCache != null) {
            clog("Cache hit on: " + url);
            // if the file age is older than the cache duration, force a refresh.
            // note that the file must exist, otherwise it is using a default.
            // not checking for file existence would do a network call on every
            // 404 or failed load.
            if (file.exists() && !checkCacheDuration(file, cacheDurationMs)) {
                clog("Cache hit, but file is stale. Forcing reload: " + url);
                if (ObjectCache instanceof ZombieObject)
                    ((ZombieObject) ObjectCache).headshot();
                ObjectCache = null;
            } else {
                clog("Using cached: " + url);
            }
        }

        if (ObjectCache != null) {
            if (mediaPlayer != null) {
                mPendingViews.remove(mediaPlayer);
                try {
                    clog("Load media file: " + ObjectCache.urlSound);
                    mediaPlayer.setDataSource(ObjectCache.urlSound);
                    mediaPlayer.prepare();
                    mediaPlayer.start();
                    mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            mediaPlayer.release();
                            mediaPlayer = null;
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return;
        }

        // oh noes, at this point we definitely do not have the file available in memory
        // let's prepare for an asynchronous load of the image.

        // null it while it is downloading
        // since listviews reuse their views, we need to
        // take note of which url this view is waiting for.
        // This may change rapidly as the list scrolls or is filtered, etc.
        clog("Waiting for " + url + " " + mediaPlayer);
        if (mediaPlayer != null) {
            mPendingViews.put(mediaPlayer, url);
        }

        final ArrayList<MediaPlayer> currentDownload = mPendingDownloads.get(url);
        if (currentDownload != null) {
            // Also, multiple vies may be waiting for this url.
            // So, let's maintain a list of these views.
            // When the url is downloaded, it sets the imagedrawable for
            // every view in the list. It needs to also validate that
            // the imageview is still waiting for this url.
            if (mediaPlayer != null) {
                currentDownload.add(mediaPlayer);
            }
            return;
        }

        final ArrayList<MediaPlayer> downloads = new ArrayList<MediaPlayer>();
        if (mediaPlayer != null) {
            downloads.add(mediaPlayer);
        }
        mPendingDownloads.put(url, downloads);

        final Loader loader = new Loader() {
            @Override
            public void onDownloadComplete(UrlDownloader downloader, InputStream in, String existingFilename) {
                try {
                    Assert.assertTrue(in == null || existingFilename == null);
                    if (in == null && existingFilename == null)
                        return;
                    String targetFilename = filename;
                    if (in != null) {
                        FileOutputStream fout = new FileOutputStream(filename);
                        copyStream(in, fout);
                        fout.close();
                    } else {
                        targetFilename = existingFilename;
                    }
                    result = new StringURL(targetFilename);
                } catch (final Exception ex) {
                    // always delete busted files when we throw.
                    new File(filename).delete();
                    if (Constants.LOG_ENABLED)
                        Log.e(Constants.LOGTAG, "Error loading " + url, ex);
                } finally {
                    // if we're not supposed to cache this thing, delete the temp file.
                    if (downloader != null && !downloader.allowCache())
                        new File(filename).delete();
                }
            }
        };

        final Runnable completion = new Runnable() {
            @Override
            public void run() {
                Assert.assertEquals(Looper.myLooper(), Looper.getMainLooper());
                StringURL usableResult = loader.result;
                if (usableResult == null) {
                    clog("No usable result, defaulting " + url);
                    usableResult = null;
                    mLiveCache.put(url, usableResult);
                }
                mPendingDownloads.remove(url);
                int waitingCount = 0;
                for (final MediaPlayer iv : downloads) {
                    // validate the url it is waiting for
                    final String pendingUrl = mPendingViews.get(iv);
                    if (!url.equals(pendingUrl)) {
                        clog("Ignoring out of date request to update view for " + url + " " + pendingUrl + " " + iv);
                        continue;
                    }
                    waitingCount++;
                    mPendingViews.remove(iv);
                    if (usableResult != null) {
//                        System.out.println(String.format("imageView: %dx%d, %dx%d", imageView.getMeasuredWidth(), imageView.getMeasuredHeight(), imageView.getWidth(), imageView.getHeight()));
                        try {
                            iv.setDataSource(usableResult.urlSound);
                            iv.prepare();
                            iv.start();
                            iv.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                                @Override
                                public void onCompletion(MediaPlayer mp) {
                                    iv.release();
                                }
                            });
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                clog("Populated: " + waitingCount);
            }
        };


        if (file.exists()) {
            try {
                if (checkCacheDuration(file, cacheDurationMs)) {
                    clog("File Cache hit on: " + url + ". " + (System.currentTimeMillis() - file.lastModified()) + "ms old.");

                    final AsyncTask<Void, Void, Void> fileloader = new AsyncTask<Void, Void, Void>() {
                        @Override
                        protected Void doInBackground(final Void... params) {
                            loader.onDownloadComplete(null, null, filename);
                            return null;
                        }

                        @Override
                        protected void onPostExecute(final Void result) {
                            completion.run();
                        }
                    };
                    executeTask(fileloader);
                    return;
                } else {
                    clog("File cache has expired. Refreshing.");
                }
            } catch (final Exception ex) {
            }
        }

        for (UrlDownloader downloader : mDownloaders) {
            if (downloader.canDownloadUrl(url)) {
                downloader.download(context, url, filename, loader, completion);
                return;
            }
        }
    }

    private static abstract class Loader implements UrlDownloader.UrlDownloaderCallback {
        StringURL result;
    }

    private static HttpUrlDownloader mHttpDownloader = new HttpUrlDownloader();
    private static ContentUrlDownloader mContentDownloader = new ContentUrlDownloader();
    private static ContactContentUrlDownloader mContactDownloader = new ContactContentUrlDownloader();
    private static FileUrlDownloader mFileDownloader = new FileUrlDownloader();
    private static ArrayList<UrlDownloader> mDownloaders = new ArrayList<UrlDownloader>();

    public static ArrayList<UrlDownloader> getDownloaders() {
        return mDownloaders;
    }

    static {
        mDownloaders.add(mHttpDownloader);
        mDownloaders.add(mContactDownloader);
        mDownloaders.add(mContentDownloader);
        mDownloaders.add(mFileDownloader);
    }

    public static interface RequestPropertiesCallback {
        public ArrayList<NameValuePair> getHeadersForRequest(Context context, String url);
    }

    private static RequestPropertiesCallback mRequestPropertiesCallback;

    public static RequestPropertiesCallback getRequestPropertiesCallback() {
        return mRequestPropertiesCallback;
    }

    public static void setRequestPropertiesCallback(final RequestPropertiesCallback callback) {
        mRequestPropertiesCallback = callback;
    }

    private static UrlAudioCache mLiveCache = UrlAudioCache.getInstance();

    private static UrlLruCache mDeadCache;
    private static HashSet<String> mAllCache = new HashSet<String>();

    private static int getHeapSize(final Context context) {
        return ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass() * 1024 * 1024;
    }


    public static class ZombieObject extends StringURL {

        public ZombieObject(final String url, final String _urlSound) {
            super(_urlSound);
            mUrl = url;

            mAllCache.add(urlSound);
            mDeadCache.remove(mUrl);
            mLiveCache.put(mUrl, this);
        }

        String mUrl;

        @Override
        protected void finalize() throws Throwable {
            super.finalize();
            if (!mHeadshot)
                mDeadCache.put(mUrl, urlSound);
            mAllCache.remove(urlSound);
            mLiveCache.remove(mUrl);
            clog("Zombie GC event " + mUrl);
        }

        // kill this zombie, forever.
        private boolean mHeadshot = false;

        public void headshot() {
            clog("BOOM! Headshot: " + mUrl);
            mHeadshot = true;
            mLiveCache.remove(mUrl);
            mAllCache.remove(urlSound);
        }
    }

    static void executeTask(final AsyncTask<Void, Void, Void> task) {
        if (Build.VERSION.SDK_INT < Constants.HONEYCOMB) {
            task.execute();
        } else {
            executeTaskHoneycomb(task);
        }
    }

    @TargetApi(Constants.HONEYCOMB)
    private static void executeTaskHoneycomb(final AsyncTask<Void, Void, Void> task) {
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private static Hashtable<MediaPlayer, String> mPendingViews = new Hashtable<MediaPlayer, String>();
    private static Hashtable<String, ArrayList<MediaPlayer>> mPendingDownloads = new Hashtable<String, ArrayList<MediaPlayer>>();
}
