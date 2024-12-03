package org.syslords.gimmesh;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;

import kotlin.Triple;

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

    ArrayList<Triple<Integer, Integer, String>> lines;

    // Constructor
    public OverlayView(Context context, AttributeSet attrs)
    {
        super(context, attrs);

        lines = new ArrayList<Triple<Integer, Integer, String>>(
                Arrays.asList(
                        new Triple<>(11, 13, "Left Shoulder-Elbow"),
                        new Triple<>(13, 15, "Left Elbow-Wrist"),
                        new Triple<>(23, 25, "Left Hip-Knee"),
                        new Triple<>(25, 27, "Left Knee-Ankle"),
                        new Triple<>(12, 14, "Right Shoulder-Elbow"),
                        new Triple<>(14, 16, "Right Elbow-Wrist"),
                        new Triple<>(24, 26, "Right Hip-Knee"),
                        new Triple<>(26, 28, "Right Knee-Ankle")
                )
        );


        setWillNotDraw(false);
        setZOrderOnTop(true);
        init();

        SurfaceHolder holder = getHolder();
        holder.addCallback(this);
        holder.setFormat(PixelFormat.TRANSPARENT);
    }

    // Initialize paint object
    private void init()
    {
        paint = new Paint();
        paint.setColor(Color.BLUE); // Set color of the circle
        paint.setStyle(Paint.Style.FILL); // Set fill style (solid color)
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        super.onDraw(canvas);
        // Draw a blue circle at the center of the view with radius 100px

        if (coordinates == null)
        {
            return;
        }

        try
        {
            for (Float[] coordinate : coordinates)
            {
                System.out.println(getWidth() * coordinate[0]);
                canvas.drawCircle(getWidth() * coordinate[0], getHeight() * coordinate[1], 20, paint);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        // canvas.drawCircle(getWidth() / 2, getHeight() / 2, 100, paint);
    }

    public void drawCoordinates(Float[][] coordinates)
    {
//         System.out.println(canvas.getWidth() * coordinates[1][0] + " " + canvas.getHeight() * coordinates[1][1]);

        this.coordinates = coordinates;

//        invalidate();
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder)
    {
        holder = surfaceHolder;

        if (drawThread != null)
        {
            drawingActive = false;
            try
            {
                drawThread.join();
            }
            catch (InterruptedException e)
            { // do nothing
            }
        }

        System.out.println("drawing");


        surfaceReady = true;
        startDrawThread();
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2)
    {
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder)
    {

    }

    @Override
    public void run()
    {
//        System.out.println("drawing");

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
            }
            catch (InterruptedException ignored)
            {
            }
        }
        try
        {

            while (drawingActive)
            {
                System.out.println("asdasd asd as sdrawing");

                if (holder == null)
                {
                    return;
                }

                frameStartTime = System.nanoTime();

                System.out.println("drawing");
                Canvas canvas = holder.lockCanvas();

                if (canvas != null)
                {
                    // clear the screen using black
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
//                    canvas.drawARGB(0, 0, 0, 0);

                    try
                    {
//                        System.out.println("drawing");
//                        for (int i = 0; i < coordinates.length && coordinates != null; ++i)
//                        {
//                            canvas.drawCircle(getWidth() * coordinates[i][0], getHeight() * coordinates[i][1], 20, paint);
//                        }

                        Paint paint = new Paint();
                        paint.setColor(Color.RED);      // Set the line color
                        paint.setStrokeWidth(5);        // Set the line thickness
                        paint.setAntiAlias(true);

                        for (Triple triple : lines)
                        {
                            float startX = getWidth() * coordinates[(int) triple.component1()][0];
                            float startY = getHeight() * coordinates[(int) triple.component1()][1];
                            float endX = getWidth() * coordinates[(int) triple.component2()][0];
                            float endY = getHeight() * coordinates[(int) triple.component2()][1];
                            canvas.drawLine(startX, startY, endX, endY, paint);
                        }

                        // Your drawing here
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                    finally
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
                    }
                    catch (InterruptedException e)
                    {
                        // ignore
                    }
                }
            }
        }
        catch (Exception e)
        {
//            Log.w(LOGTAG, "Exception while locking/unlocking");
        }
//        Log.d(LOGTAG, "Draw thread finished");
    }

    public void startDrawThread()
    {
        if (surfaceReady && drawThread == null)
        {
//            System.out.println("Drawing thread");
            drawThread = new Thread(this, "Draw thread");
            drawingActive = true;
            drawThread.start();
        }
    }
}