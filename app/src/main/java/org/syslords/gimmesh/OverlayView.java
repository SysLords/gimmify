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
import java.util.HashMap;
import java.util.Map;

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

    Map<String, Integer> bodyPartMap = new HashMap<>();

    private boolean surfaceReady = false;

    private static final int MAX_FRAME_TIME = (int) (1000.0 / 120.0);

    SurfaceHolder holder;

    ArrayList<Triple<Integer, Integer, String>> lines;
    ArrayList<Integer> circles;


    // Constructor
    public OverlayView(Context context, AttributeSet attrs)
    {
        super(context, attrs);

        lines = new ArrayList<Triple<Integer, Integer, String>>(Arrays.asList(new Triple<>(11, 12, "Shoulders"), new Triple<>(23, 24, "Hips"), new Triple<>(11, 23, "Left Torso"), new Triple<>(12, 24, "Right Torso"), new Triple<>(11, 13, "Left Shoulder-Elbow"), new Triple<>(13, 15, "Left Elbow-Wrist"), new Triple<>(23, 25, "Left Hip-Knee"), new Triple<>(25, 27, "Left Knee-Ankle"), new Triple<>(12, 14, "Right Shoulder-Elbow"), new Triple<>(14, 16, "Right Elbow-Wrist"), new Triple<>(24, 26, "Right Hip-Knee"), new Triple<>(26, 28, "Right Knee-Ankle"), new Triple<>(28, 30, "Right Ankle-Heel"), new Triple<>(30, 32, "Right Heel-FootIndex"), new Triple<>(28, 32, "Right Ankle-FootIndex"), new Triple<>(27, 29, "Left Ankle-Heel"), new Triple<>(29, 31, "Left Heel-FootIndex"), new Triple<>(27, 31, "Left Ankle-FootIndex")

        ));


        bodyPartMap.put("nose", 0);
        bodyPartMap.put("left eye (inner)", 1);
        bodyPartMap.put("left eye", 2);
        bodyPartMap.put("left eye (outer)", 3);
        bodyPartMap.put("right eye (inner)", 4);
        bodyPartMap.put("right eye", 5);
        bodyPartMap.put("right eye (outer)", 6);
        bodyPartMap.put("left ear", 7);
        bodyPartMap.put("right ear", 8);
        bodyPartMap.put("mouth (left)", 9);
        bodyPartMap.put("mouth (right)", 10);
        bodyPartMap.put("left shoulder", 11);
        bodyPartMap.put("right shoulder", 12);
        bodyPartMap.put("left elbow", 13);
        bodyPartMap.put("right elbow", 14);
        bodyPartMap.put("left wrist", 15);
        bodyPartMap.put("right wrist", 16);
        bodyPartMap.put("left pinky", 17);
        bodyPartMap.put("right pinky", 18);
        bodyPartMap.put("left index", 19);
        bodyPartMap.put("right index", 20);
        bodyPartMap.put("left thumb", 21);
        bodyPartMap.put("right thumb", 22);
        bodyPartMap.put("left hip", 23);
        bodyPartMap.put("right hip", 24);
        bodyPartMap.put("left knee", 25);
        bodyPartMap.put("right knee", 26);
        bodyPartMap.put("left ankle", 27);
        bodyPartMap.put("right ankle", 28);
        bodyPartMap.put("left heel", 29);
        bodyPartMap.put("right heel", 30);
        bodyPartMap.put("left foot index", 31);
        bodyPartMap.put("right foot index", 32);

        circles = new ArrayList<Integer>(Arrays.asList(bodyPartMap.get("left hip"), bodyPartMap.get("right hip"), bodyPartMap.get("left shoulder"), bodyPartMap.get("right shoulder"), bodyPartMap.get("left elbow"), bodyPartMap.get("right elbow"), bodyPartMap.get("left knee"), bodyPartMap.get("right knee"), bodyPartMap.get("left ankle"), bodyPartMap.get("right ankle"), bodyPartMap.get("left heel"), bodyPartMap.get("right heel"), bodyPartMap.get("left foot index"), bodyPartMap.get("right foot index")));

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
                        paint.setColor(Color.WHITE);
                        paint.setStrokeWidth(5);
                        paint.setAntiAlias(true);

                        for (Triple triple : lines)
                        {
                            float startX = getWidth() * coordinates[(int) triple.component1()][0];
                            float startY = getHeight() * coordinates[(int) triple.component1()][1];
                            float endX = getWidth() * coordinates[(int) triple.component2()][0];
                            float endY = getHeight() * coordinates[(int) triple.component2()][1];
                            canvas.drawLine(startX, startY, endX, endY, paint);
                        }

                        for (Integer i : circles)
                        {
                            Paint fillPaint = new Paint();
                            fillPaint.setColor(Color.BLUE);
                            fillPaint.setStyle(Paint.Style.FILL);

                            Paint strokePaint = new Paint();
                            strokePaint.setColor(Color.WHITE);
                            strokePaint.setStrokeWidth(5);
                            strokePaint.setStyle(Paint.Style.STROKE);

                            // Draw a circle
                            float centerX = (float) getWidth() * coordinates[(int) i][0];       // X coordinate of the center
                            float centerY = (float) getHeight() * coordinates[(int) i][1];      // Y coordinate of the center
                            float radius;

                            if (i == bodyPartMap.get("left hip") || i == bodyPartMap.get("right hip"))
                            {
                                radius = 24;
                            }
                            else if (i == bodyPartMap.get("left shoulder") || i == bodyPartMap.get("right shoulder"))
                            {
                                radius = 20;
                            }
                            else if (i == bodyPartMap.get("left heel") || i == bodyPartMap.get("right heel") || i == bodyPartMap.get("left foot index") || i == bodyPartMap.get("right foot index") || i == bodyPartMap.get("left ankle") || i == bodyPartMap.get("right ankle"))
                            {
                                radius = 12;
                            }
                            else
                            {
                                radius = 16;
                            }

                            canvas.drawCircle(centerX, centerY, radius - 2.5f, fillPaint);
                            canvas.drawCircle(centerX, centerY, radius, strokePaint);
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