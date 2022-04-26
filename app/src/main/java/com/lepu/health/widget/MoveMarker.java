package com.lepu.health.widget;


import android.os.Handler;

import com.autonavi.amap.mapcore.IPoint;
import com.autonavi.amap.mapcore.MapProjection;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.SphericalUtil;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MoveMarker {
    private GoogleMap mAMap;
    private Handler handler;
    private long duration = 10000L;
    private long mStepDuration = 20L;
    private LinkedList<LatLng> points = new LinkedList();
    private LinkedList<Double> eachDistance = new LinkedList();
    private double totalDistance = 0.0D;
    private double remainDistance = 0.0D;
    private ExecutorService mThreadPools;
    private Object mLock = new Object();
    private Marker marker = null;
    private BitmapDescriptor descriptor;
    private int index = 0;
    private boolean useDefaultDescriptor = false;
    AtomicBoolean exitFlag = new AtomicBoolean(false);
    private MoveListener moveListener;
    private a moveStatus;
    private long pauseMillis;
    private long mAnimationBeginTime;

    public MoveMarker(GoogleMap var1, Handler handler) {
        this.moveStatus = a.a;
        this.mAnimationBeginTime = System.currentTimeMillis();
        this.mAMap = var1;
        this.handler = handler;
        this.mThreadPools = new ThreadPoolExecutor(1, 2, 5L, TimeUnit.SECONDS, new SynchronousQueue(), new b());
    }

    public void setPoints(List<LatLng> var1) {
        synchronized (this.mLock) {
            try {
                if (var1 == null || var1.size() < 2) {
                    return;
                }

                this.stopMove();
                this.points.clear();
                Iterator var3 = var1.iterator();

                while (var3.hasNext()) {
                    LatLng var4 = (LatLng) var3.next();
                    if (var4 != null) {
                        this.points.add(var4);
                    }
                }

                this.eachDistance.clear();
                this.totalDistance = 0.0D;

                for (int var9 = 0; var9 < this.points.size() - 1; ++var9) {
                    double var11 = SphericalUtil.computeDistanceBetween(this.points.get(var9), this.points.get(var9 + 1));
                    this.eachDistance.add(var11);
                    this.totalDistance += var11;
                }

                this.remainDistance = this.totalDistance;
                LatLng var10 = this.points.get(0);
                if (this.marker != null) {
                    this.marker.setPosition(var10);
                    this.checkMarkerIcon();
                } else {
                    if (this.descriptor == null) {
                        this.useDefaultDescriptor = true;
                    }

                    this.marker = this.mAMap.addMarker((new MarkerOptions()).position(var10).icon(this.descriptor).title("").anchor(0.5F, 0.5F));
                }

                this.reset();
            } catch (Throwable var7) {
                var7.printStackTrace();
            }

        }
    }

    private void reset() {
        try {
            if (this.moveStatus == a.c || this.moveStatus == a.d) {
                this.exitFlag.set(true);
                this.mThreadPools.awaitTermination(this.mStepDuration + 20L, TimeUnit.MILLISECONDS);
                this.moveStatus = a.a;
            }
        } catch (InterruptedException var2) {
            var2.printStackTrace();
        }
    }

    private void checkMarkerIcon() {
        if (this.useDefaultDescriptor) {
            if (this.descriptor == null) {
                this.useDefaultDescriptor = true;
            } else {
                this.marker.setIcon(this.descriptor);
                this.useDefaultDescriptor = false;
            }
        }

    }

    public void setTotalDuration(int var1) {
        this.duration = var1 * 1000;
    }

    public void startMove() {
        if (this.moveStatus == a.d) {
            this.moveStatus = a.c;
            long var1 = System.currentTimeMillis() - this.pauseMillis;
            this.mAnimationBeginTime += var1;
        } else {
            if (this.moveStatus == a.a || this.moveStatus == a.e) {
                if (this.points.size() < 1) {
                    return;
                }

                this.index = 0;

                try {
                    this.mThreadPools.execute(new c());
                } catch (Throwable var3) {
                    var3.printStackTrace();
                }
            }

        }
    }

    private LatLng getCurPosition(long var1) {
        if (var1 > this.duration) {
            this.exitFlag.set(true);
            this.index = this.points.size() - 1;
            LatLng var4 = this.points.get(this.index);
            --this.index;
            this.index = Math.max(this.index, 0);
            this.remainDistance = 0.0D;
            if (this.moveListener != null) {
                this.moveListener.move(this.remainDistance);
            }

            return var4;
        } else {
            double var3 = (double) var1 * this.totalDistance / (double) this.duration;
            this.remainDistance = this.totalDistance - var3;
            int var5 = 0;
            double var6 = 1.0D;

            for (int var8 = 0; var8 < this.eachDistance.size(); ++var8) {
                double var9 = this.eachDistance.get(var8);
                if (var3 <= var9) {
                    if (var9 > 0.0D) {
                        var6 = var3 / var9;
                    }

                    var5 = var8;
                    break;
                }

                var3 -= var9;
            }

            if (var5 != this.index && this.moveListener != null) {
                this.moveListener.move(this.remainDistance);
            }

            this.index = var5;
            LatLng var18 = this.points.get(var5);
            LatLng var19 = this.points.get(var5 + 1);
            IPoint var10 = new IPoint();
            MapProjection.lonlat2Geo(var18.longitude, var18.latitude, var10);
            IPoint var11 = new IPoint();
            MapProjection.lonlat2Geo(var19.longitude, var19.latitude, var11);
            int var12 = var11.x - var10.x;
            int var13 = var11.y - var10.y;
            float var14 = (float) SphericalUtil.computeDistanceBetween(var18, var19);
            if (var14 > 5.0F) {
                float var15 = this.getRotate(var10, var11);
                if (this.mAMap != null) {
                    CameraPosition var16 = this.mAMap.getCameraPosition();
                    if (var16 != null) {
                        this.marker.setRotation(360.0F - var15 + var16.bearing);
                    }
                }
            }

            return var19;
        }
    }

    private float getRotate(IPoint var1, IPoint var2) {
        if (var1 != null && var2 != null) {
            double var3 = var2.y;
            double var5 = var1.y;
            double var7 = var1.x;
            double var9 = var2.x;
            float var11 = (float) (Math.atan2(var9 - var7, var5 - var3) / 3.141592653589793D * 180.0D);
            return var11;
        } else {
            return 0.0F;
        }
    }

    public void stopMove() {
        if (this.moveStatus == a.c) {
            this.moveStatus = a.d;
            this.pauseMillis = System.currentTimeMillis();
        }

    }

    public Marker getMarker() {
        return this.marker;
    }

    public LatLng getPosition() {
        return this.marker == null ? null : this.marker.getPosition();
    }

    public int getIndex() {
        return this.index;
    }

    public void resetIndex() {
        this.index = 0;
    }

    public void destroy() {
        try {
            this.reset();
            this.mThreadPools.shutdownNow();
            synchronized (this.mLock) {
                this.points.clear();
                this.eachDistance.clear();
            }
        } catch (Throwable var4) {
            var4.printStackTrace();
        }

    }

    public void removeMarker() {
        if (this.marker != null) {
            this.marker.remove();
            this.marker = null;
        }

        this.points.clear();
        this.eachDistance.clear();
    }

    public void setPosition(LatLng var1) {
        if (this.marker != null) {
            this.marker.setPosition(var1);
            this.checkMarkerIcon();
        } else {
            if (this.descriptor == null) {
                this.useDefaultDescriptor = true;
            }

            this.marker = this.mAMap.addMarker((new MarkerOptions()).position(var1).icon(this.descriptor).title("").anchor(0.5F, 0.5F));
        }

    }

    public void setDescriptor(BitmapDescriptor var1) {
        this.descriptor = var1;
        if (this.marker != null) {
            this.marker.setIcon(var1);
        }

    }

    public void setRotate(float var1) {
        if (this.marker != null && this.mAMap != null) {
            CameraPosition var2 = this.mAMap.getCameraPosition();
            if (var2 != null) {
                this.marker.setRotation(360.0F - var1 + var2.bearing);
            }
        }

    }

    public void setVisible(boolean var1) {
        if (this.marker != null) {
            this.marker.setVisible(var1);
        }

    }

    public void setMoveListener(MoveListener var1) {
        this.moveListener = var1;
    }

    private class c implements Runnable {
        private c() {
        }

        public void run() {
            try {
                MoveMarker.this.mAnimationBeginTime = System.currentTimeMillis();
                MoveMarker.this.moveStatus = a.b;
                MoveMarker.this.exitFlag.set(false);

                for (; !MoveMarker.this.exitFlag.get() && MoveMarker.this.index <= MoveMarker.this.points.size() - 1; Thread.sleep(MoveMarker.this.mStepDuration)) {
                    synchronized (MoveMarker.this.mLock) {
                        if (MoveMarker.this.exitFlag.get()) {
                            return;
                        }

                        if (MoveMarker.this.moveStatus != a.d) {
                            long var2 = System.currentTimeMillis() - MoveMarker.this.mAnimationBeginTime;
                            if (MoveMarker.this.marker != null) {
                                handler.post(() -> {
                                    LatLng var4 = MoveMarker.this.getCurPosition(var2);
                                    MoveMarker.this.marker.setPosition(var4);
                                });

                            }

                            MoveMarker.this.moveStatus = a.c;
                        }
                    }
                }

                MoveMarker.this.moveStatus = a.e;
            } catch (Throwable var7) {
                var7.printStackTrace();
            }

        }
    }

    private static class b implements ThreadFactory {
        private b() {
        }

        public Thread newThread(Runnable var1) {
            Thread var2 = new Thread(var1, "MoveThread");
            return var2;
        }
    }

    public interface MoveListener {
        void move(double var1);
    }

    private static enum a {
        a,
        b,
        c,
        d,
        e;

        private a() {
        }
    }
}
