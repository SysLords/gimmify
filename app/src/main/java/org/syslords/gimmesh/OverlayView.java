package org.syslords.gimmesh;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import androidx.annotation.NonNull;

public class OverlayView extends SurfaceView implements SurfaceHolder.Callback, Runnable
{
    Float[][] coordinates;
    private Paint paint;
    Canvas canvas;

    private boolean drawingActive = false;

    private Thread drawThread;

    /**
     * True when the surface is ready to draw
     */
    private boolean surfaceReady = false;

    private static final int MAX_FRAME_TIME = (int) (1000.0 / 120.0);

    SurfaceHolder holder;

    // Constructor
    public OverlayView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        setWillNotDraw(false);
        setZOrderOnTop(true);
        init();

        SurfaceHolder holder = getHolder();
        holder.addCallback(this);
        holder.setFormat(PixelFormat.TRANSPARENT);
    }

    // Initialize paint object
    private void init() {
        paint = new Paint();
        paint.setColor(Color.BLUE); // Set color of the circle
        paint.setStyle(Paint.Style.FILL); // Set fill style (solid color)
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Draw a blue circle at the center of the view with radius 100px

        if (coordinates == null)
            return;

        for (int i = 0;i < 17;++i)
        {
            canvas.drawCircle(getWidth() * coordinates[i][0] ,getHeight() * coordinates[i][1], 20, paint);
        }
        // canvas.drawCircle(getWidth() / 2, getHeight() / 2, 100, paint);
    }

    public void drawCoordinates(Float[][] coordinates)
    {
        // System.out.println(canvas.getWidth() * coordinates[1][0] + " " + canvas.getHeight() * coordinates[1][1]);

        this.coordinates = coordinates;

//        invalidate();
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
        holder = surfaceHolder;

        if (drawThread != null)
        {
            drawingActive = false;
            try
            {
                drawThread.join();
            } catch (InterruptedException e)
            { // do nothing
            }
        }

        System.out.println("drawing");


        surfaceReady = true;
        startDrawThread();
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {

    }

    @Override
    public void run()
    {
        System.out.println("drawing");

//        Log.d(LOGTAG, "Draw thread started");
        long frameStartTime;
        long frameTime;

        /*
         * In order to work reliable on Nexus 7, we place ~500ms delay at the start of drawing thread
         * (AOSP - Issue 58385)
         */
        if (android.os.Build.BRAND.equalsIgnoreCase("google") && android.os.Build.MANUFACTURER.equalsIgnoreCase("asus") && android.os.Build.MODEL.equalsIgnoreCase("Nexus 7"))
        {
//            Log.w(LOGTAG, "Sleep 500ms (Device: Asus Nexus 7)");
            try
            {
                Thread.sleep(500);
            } catch (InterruptedException ignored)
            {
            }
        }
        try
        {
            while (drawingActive)
            {
                if (holder == null)
                {
                    return;
                }

                frameStartTime = System.nanoTime();

//                System.out.println("drawing");
                Canvas canvas = holder.lockCanvas();

                if (canvas != null)
                {
                    // clear the screen using black
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
//                    canvas.drawARGB(0, 0, 0, 0);

                    try
                    {
//                        System.out.println("drawing");
                        for (int i = 0;i < 17 && coordinates != null;++i)
                        {
                            canvas.drawCircle(getWidth() * coordinates[i][0] ,getHeight() * coordinates[i][1], 20, paint);
                        }
                        // Your drawing here
                    } finally
                    {

                        holder.unlockCanvasAndPost(canvas);
                    }
                }

                // calculate the time required to draw the frame in ms
                frameTime = (System.nanoTime() - frameStartTime) / 1000000;

                if (frameTime < MAX_FRAME_TIME) // faster than the max fps - limit the FPS
                {
                    try
                    {
                        Thread.sleep(MAX_FRAME_TIME - frameTime);
                    } catch (InterruptedException e)
                    {
                        // ignore
                    }
                }
            }
        } catch (Exception e)
        {
//            Log.w(LOGTAG, "Exception while locking/unlocking");
        }
//        Log.d(LOGTAG, "Draw thread finished");
    }

    public void startDrawThread()
    {
        if (surfaceReady && drawThread == null)
        {
            drawThread = new Thread(this, "Draw thread");
            drawingActive = true;
            drawThread.start();
        }
    }
}