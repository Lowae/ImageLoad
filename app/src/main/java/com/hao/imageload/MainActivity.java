package com.hao.imageload;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {

    private GridView gridView;
    private ImageAdapter mImageAdapter;

    private ImageLoader imageLoader;
    private List<String> urlList;

    private boolean mIsGridViewIdle = true;
    private int mImageWidth = 0;

    private String[] imageUrl = {
            "http://b.hiphotos.baidu.com/zhidao/pic/item/a6efce1b9d16fdfafee0cfb5b68f8c5495ee7bd8.jpg",
            "http://pic47.nipic.com/20140830/7487939_180041822000_2.jpg",
            "http://pic41.nipic.com/20140518/4135003_102912523000_2.jpg",
            "http://h.hiphotos.baidu.com/image/pic/item/3b87e950352ac65c0f1f6e9efff2b21192138ac0.jpg",
            "http://pic42.nipic.com/20140618/9448607_210533564001_2.jpg",
            "http://pic10.nipic.com/20101027/3578782_201643041706_2.jpg",
            "http://img2.3lian.com/2014/c7/51/d/26.jpg",
            "http://b.zol-img.com.cn/desk/bizhi/image/3/960x600/1375841395686.jpg",
            "http://pic41.nipic.com/20140518/4135003_102025858000_2.jpg",
            "http://pic.58pic.com/58pic/13/00/22/32M58PICV6U.jpg",
            "http://h.hiphotos.baidu.com/zhidao/wh%3D450%2C600/sign=429e7b1b92ef76c6d087f32fa826d1cc/7acb0a46f21fbe09cc206a2e69600c338744ad8a.jpg",
            "http://pica.nipic.com/2007-12-21/2007122115114908_2.jpg",
            "http://cdn.duitang.com/uploads/item/201405/13/20140513212305_XcKLG.jpeg",
            "http://photo.loveyd.com/uploads/allimg/080618/1110324.jpg",
            "http://img4.duitang.com/uploads/item/201404/17/20140417105820_GuEHe.thumb.700_0.jpeg",
            "http://cdn.duitang.com/uploads/item/201204/21/20120421155228_i52eX.thumb.600_0.jpeg",
            "http://img4.duitang.com/uploads/item/201404/17/20140417105856_LTayu.thumb.700_0.jpeg",
            "http://pic.dbw.cn/0/01/33/59/1335968_847719.jpg",
            "http://a.hiphotos.baidu.com/image/pic/item/a8773912b31bb051a862339c337adab44bede0c4.jpg",
            "http://h.hiphotos.baidu.com/image/pic/item/f11f3a292df5e0feeea8a30f5e6034a85edf720f.jpg",
            "http://img0.pconline.com.cn/pconline/bizi/desktop/1412/ER2.jpg",
            "http://pic.58pic.com/58pic/11/25/04/91v58PIC6Xy.jpg",
            "http://pic25.nipic.com/20121210/7447430_172514301000_2.jpg",
            "http://img02.tooopen.com/images/20140320/sy_57121781945.jpg"
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initData();
        initView();
        imageLoader = ImageLoader.build(this);
    }

    @TargetApi(Build.VERSION_CODES.N)
    private void initData() {
        urlList = Arrays.stream(imageUrl).collect(Collectors.toList());
        int screenWidth = getScreenMetrics(this).widthPixels;
        int space = (int) dp2px(this, 20f);
        mImageWidth = (screenWidth - space) / 3;

    }

    private void initView() {
        gridView = findViewById(R.id.gv_image);
        mImageAdapter = new ImageAdapter(this);
        gridView.setAdapter(mImageAdapter);
        gridView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                    mIsGridViewIdle = true;
                    mImageAdapter.notifyDataSetChanged();
                } else {
                    mIsGridViewIdle = false;
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {

            }
        });
    }

    private class ImageAdapter extends BaseAdapter {

        private LayoutInflater inflater;

        public ImageAdapter(Context context){
            inflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return urlList.size();
        }

        @Override
        public String getItem(int position) {
            return urlList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ImageHolder viewHolder;
            if (convertView == null){
                convertView = inflater.inflate(R.layout.item_image, parent, false);
                viewHolder = new ImageHolder();
                viewHolder.imageView = convertView.findViewById(R.id.image);
                convertView.setTag(viewHolder);
            }else {
                viewHolder = (ImageHolder) convertView.getTag();
            }

            ImageView imageView = viewHolder.imageView;
            String tag = (String) imageView.getTag();
            String url = getItem(position);
            if (!url.equals(tag)){
                imageView.setImageResource(R.drawable.image_default);
            }
            if (mIsGridViewIdle){
                imageView.setTag(url);
                imageLoader.bindBitmap(url, imageView, mImageWidth, mImageWidth);
            }
            return convertView;
        }
    }

    /*private class ImageAdapter extends gridView.Adapter<ImageHolder>{

        private Context mContext;

        public ImageAdapter(Context context){
            mContext = context;
        }

        @NonNull
        @Override
        public ImageHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {

            return new ImageHolder(LayoutInflater.from(mContext).inflate(R.layout.item_image,viewGroup, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ImageHolder imageHolder, int i) {
            imageLoader.bindBitmap(urlList.get(i), imageHolder.imageView);
        }

        @Override
        public int getItemCount() {
            return urlList.size();
        }
    }*/

    public static DisplayMetrics getScreenMetrics(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(dm);
        return dm;
    }

    public static float dp2px(Context context, float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics());
    }

    private class ImageHolder {
        ImageView imageView;
        
    }
}
