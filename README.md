# ImageLoad
异步加载网络图片，采用LruCache实现内存缓存，DiskLruCache实现磁盘缓存，线程池和Handler来完成加载Bitmap到ImageView。
使用BitmapFactory对Bitmap进行缩放处理，避免加载不必要的分辨率。

