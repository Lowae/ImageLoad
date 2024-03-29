package com.hao.imageload;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import com.jakewharton.disklrucache.DiskLruCache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ImageLoader {

    private static final String TAG = "ImageLoader";

    public static final int MESSAGE_POST = 1;

    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = CPU_COUNT + 1;
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final long KEEP_ALIVE = 10L;

    private static final int TAG_KEY_URL = R.id.image_url;
    private static final long DISK_CACHE_SIZE = 1024 * 1024 * 50;
    private static final int IO_BUFFER_SIZE = 8 * 1024;
    private static final int DISK_CACHE_INDEX = 0;


    private boolean mIsDiskLruCacheCreated = false;


    private Context mContext;

    private LruCache<String, Bitmap> mMemoryCache;
    private DiskLruCache mDiskLruCache;

    private ThreadFactory sThreadFactory = new ThreadFactory() {

        private final AtomicInteger mCount = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "ImageLoader#" +mCount.getAndIncrement());
        }
    };

    private Executor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(
            CORE_POOL_SIZE, MAXIMUM_POOL_SIZE,KEEP_ALIVE, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(), sThreadFactory);

    private Handler mMainHandler = new Handler(Looper.getMainLooper()){
        @Override
        public void handleMessage(Message msg) {
            LoaderResult result = (LoaderResult) msg.obj;
            ImageView imageView = result.imageView;
            String url = (String) imageView.getTag(TAG_KEY_URL);
            if (url.equals(result.url)){
                imageView.setImageBitmap(result.bitmap);
            }else {
                Log.d(TAG, "Image url change !");
            }
        }
    };

    private ImageLoader(Context context){
        mContext = context.getApplicationContext();
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;
        mMemoryCache = new LruCache<String, Bitmap>(cacheSize){
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes() * value.getHeight() / 1024;
            }
        };

        File diskCacheDir = getDiskCacheDir(mContext, "bitmap");
        if (!diskCacheDir.exists()){
            diskCacheDir.mkdirs();
        }
        if (diskCacheDir.getUsableSpace() > DISK_CACHE_SIZE){
            try {
                mDiskLruCache = DiskLruCache.open(diskCacheDir, 1, 1, DISK_CACHE_SIZE);
                mIsDiskLruCacheCreated = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static ImageLoader build(Context context){
        return new ImageLoader(context);
    }

    public void bindBitmap(final String url, final ImageView imageView){
        bindBitmap(url, imageView, 0, 0);
    }

    public void bindBitmap(final String url, final ImageView imageView, final int reqWidth, final int reqHeight) {
        imageView.setTag(TAG_KEY_URL, url);
        Bitmap bitmap = loadBitmapFromMemCache(url);
        if (bitmap != null){
            imageView.setImageBitmap(bitmap);
        }

        Runnable loadBitmapTask = new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap = loadBitmap(url, reqWidth, reqHeight);
                if (bitmap != null){
                    LoaderResult result = new LoaderResult(imageView, url, bitmap);
                    mMainHandler.obtainMessage(MESSAGE_POST, result).sendToTarget();
                }
            }
        };
        THREAD_POOL_EXECUTOR.execute(loadBitmapTask);
    }

    /**
     * 从内存或磁盘缓存或网络中加载图片
     * @param url 图片url
     * @param width ImageView对应的widht
     * @param height ImageView对应的height
     * @return bitmap图片
     */
    public Bitmap loadBitmap(String url, int width, int height){
        Bitmap bitmap = loadBitmapFromMemCache(url);
        if (bitmap != null){
            Log.d(TAG, "loadBitmapFromMemory :"+url);
            return bitmap;
        }
        try {
            bitmap = loadBitmapFromDiskCache(hashKeyFormUrl(url), width, height);
            if (bitmap != null){
                Log.d(TAG, "loadBitmapFromDisk :"+url);
                return bitmap;
            }
            bitmap = loadBitmapFromHttp(url, width, height);
            Log.d(TAG, "loadBitmapFromHttp :"+url);
        }catch (IOException e){
            e.printStackTrace();
        }

        if (bitmap == null && !mIsDiskLruCacheCreated){
            Log.e(TAG, "DiskLruCache not created!");
            bitmap = downloadBitmapToBitmap(url);
        }

        return bitmap;
    }

    private Bitmap loadBitmapFromMemCache(String url){
        final String key = hashKeyFormUrl(url);
        return getBitmapFromMemCache(key);
    }

    //添加到内存缓存中
    private void addBitmapToMemoryCache(String key, Bitmap bitmap){
        if (getBitmapFromMemCache(key) == null){
            mMemoryCache.put(key, bitmap);
        }
    }

    private Bitmap getBitmapFromMemCache(String key) {
        return mMemoryCache.get(key);
    }

    //添加到磁盘缓存中
    private Bitmap loadBitmapFromHttp(String url, int reqWidth, int reqHeight) throws IOException {
        if (mDiskLruCache == null){
            return null;
        }

        String key = hashKeyFormUrl(url);
        DiskLruCache.Editor editor = mDiskLruCache.edit(key);
        if (editor != null){
            OutputStream outputStream = editor.newOutputStream(DISK_CACHE_INDEX);
            if (downloadBitmapToStream(url, outputStream)){
                editor.commit();
            }else {
                editor.abort();
            }
            mDiskLruCache.flush();
        }
        return loadBitmapFromDiskCache(key, reqWidth, reqHeight);
    }

    private Bitmap loadBitmapFromDiskCache(String key, int reqWidth, int reqHeight) throws IOException {
        if (mDiskLruCache == null){
            return null;
        }

        Bitmap bitmap = null;
        DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key);
        if (snapshot != null){
            FileInputStream fileInputStream = (FileInputStream) snapshot.getInputStream(DISK_CACHE_INDEX);
            FileDescriptor descriptor = fileInputStream.getFD();
            bitmap = ImageResizer.decodeSampledBitmapFromFileDescriptor(descriptor, reqWidth, reqHeight);
            if (bitmap != null){
                addBitmapToMemoryCache(key, bitmap);
            }
        }
        return bitmap;
    }

    private boolean downloadBitmapToStream(String urlString, OutputStream outputStream){
        HttpURLConnection connection = null;
        BufferedOutputStream out = null;
        BufferedInputStream in = null;
        try {
            final URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            in = new BufferedInputStream(connection.getInputStream(), IO_BUFFER_SIZE);
            out = new BufferedOutputStream(outputStream, IO_BUFFER_SIZE);

            int b;
            while ((b = in.read()) != -1){
                out.write(b);
            }
            return true;
        }catch (IOException e){
            e.printStackTrace();
        }finally {
            if (connection != null){
                connection.disconnect();
            }
            try {
                if (out != null){
                    out.close();

                }
                if (in != null){
                    in.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private Bitmap downloadBitmapToBitmap(String urlString){
        Bitmap bitmap = null;
        HttpURLConnection connection = null;
        BufferedInputStream in = null;
        try {
            final URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            in = new BufferedInputStream(connection.getInputStream(), IO_BUFFER_SIZE);
            bitmap = BitmapFactory.decodeStream(in);
        }catch (IOException e){
            e.printStackTrace();
        }finally {
            if (connection != null){
                connection.disconnect();
            }
            try{
                if (in != null){
                    in.close();
                }
            }catch (IOException e){
                e.printStackTrace();
            }
        }
        return bitmap;
    }

    private File getDiskCacheDir(Context context, String uniqueName){
        boolean externalStorageAvailable = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
        final String cachePath;
        if (externalStorageAvailable){
            cachePath = Objects.requireNonNull(context.getExternalCacheDir()).getPath();
        }else {
            cachePath = context.getCacheDir().getPath();
        }
        return new File(cachePath + File.separator + uniqueName);
    }


    private String hashKeyFormUrl(String url) {
        String cacheKey;
        try {
            final MessageDigest mDigest = MessageDigest.getInstance("MD5");
            mDigest.update(url.getBytes());
            cacheKey = bytesToHexString(mDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            cacheKey = String.valueOf(url.hashCode());
        }
        return cacheKey;
    }

    private String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte aByte : bytes) {
            String hex = Integer.toHexString(0xFF & aByte);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    private class LoaderResult {
        ImageView imageView;
        String url;
        Bitmap bitmap;

        public LoaderResult(ImageView imageView, String url, Bitmap bitmap) {
            this.imageView = imageView;
            this.url = url;
            this.bitmap = bitmap;
        }
    }
}
