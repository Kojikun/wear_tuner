package com.example.koji.tuner;

import android.graphics.Typeface;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.BoxInsetLayout;
import android.view.View;
import android.widget.TextView;
import android.support.v4.graphics.ColorUtils;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.lang.Math;
import java.util.Vector;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;

public class MainActivity extends WearableActivity {

    private static final SimpleDateFormat AMBIENT_DATE_FORMAT =
            new SimpleDateFormat("HH:mm", Locale.US);

    private BoxInsetLayout mContainerView;
    private TextView mHertzView;
    private TextView mLetterView;
    private TextView mClockView;

    private Thread audioThread;
    private AudioDispatcher dispatcher;
    private AudioProcessor p;

    private float pitch_hz = 0;
    private double detune = 0;
    private double detune_avg = 0;
    private double avg;
    private int col = 0;
    private char letter;
    private Vector<Double> flataverage = new Vector<Double>();
    private Vector<Double> sharpaverage = new Vector<Double>();
    private Vector<Double> tuneaverage = new Vector<Double>();
    private int vMax = 4;  // max vector size for pitch average
    private double tune_threshold = 0.3;
    private double tune_detect = 0.1;
    private String[] letters = {"G#", "A", "A#", "B", "C", "C#", "D", "D#", "E", "F", "F#", "G"};


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i("Activity", "onCreate");
        setContentView(R.layout.activity_main);
        setAmbientEnabled();

        mContainerView = (BoxInsetLayout) findViewById(R.id.container);
        mHertzView = (TextView) findViewById(R.id.text_hz);
        mLetterView = (TextView) findViewById(R.id.text_letter);
        mClockView = (TextView) findViewById(R.id.clock);

        beginAudioProcessing();

    }

    private void beginAudioProcessing() {
        Log.i("Audio", "Creating dispatcher");
        dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(22050, 1024, 0);

        Log.i("Audio", "Dispatcher created; creating detection handler");
        PitchDetectionHandler pdh = new PitchDetectionHandler() {
            @Override
            public void handlePitch(PitchDetectionResult pitchDetectionResult, AudioEvent audioEvent) {
                final float pitchInHz = pitchDetectionResult.getPitch();

                // calculation variables

                final int key_num = (int)Math.round((12 * (Math.log(pitchInHz/440)/Math.log(2))) + 49);
                final double expected_pitch = (Math.pow(2, (key_num-49)/12.0)) * 440.0;
                // calculate lower and upper bounds
                final double ep_lower = (Math.pow(2, (key_num-49-0.5)/12.0)) * 440.0;
                final double ep_upper = (Math.pow(2, (key_num-49+0.5)/12.0)) * 440.0;
                final double offset = pitchInHz - expected_pitch;
                // calculate detune - negative is flat, positive is sharp
                if (pitchInHz >= 0) {
                    detune_avg = 0;
                    if (pitchInHz < expected_pitch) {
                        detune = offset / (expected_pitch - ep_lower);
                        if (Math.abs(detune) < tune_threshold) {
                            tuneaverage.addElement(detune);
                            if (tuneaverage.size() > vMax)
                                tuneaverage.removeElementAt(0);
                            for (int i = 0; i < tuneaverage.size(); ++i) {
                                detune_avg += tuneaverage.get(i);
                            }
                            detune_avg /= tuneaverage.size();
                        }
                        else {
                            flataverage.addElement(detune);
                            if (flataverage.size() > vMax)
                                flataverage.removeElementAt(0);
                            for (int i = 0; i < flataverage.size(); ++i) {
                                detune_avg += flataverage.get(i);
                            }
                            detune_avg /= flataverage.size();
                        }
                        col = ColorUtils.blendARGB(getResources().getColor(android.R.color.holo_green_light), getResources().getColor(android.R.color.holo_blue_light), (float) Math.abs(detune_avg));
                    } else {
                        detune = offset / (ep_upper - expected_pitch);
                        if (Math.abs(detune) < tune_threshold) {
                            tuneaverage.addElement(detune);
                            if (tuneaverage.size() > vMax)
                                tuneaverage.removeElementAt(0);
                            for (int i = 0; i < tuneaverage.size(); ++i) {
                                detune_avg += tuneaverage.get(i);
                            }
                            detune_avg /= tuneaverage.size();
                        }
                        else {
                            sharpaverage.addElement(detune);
                            if (sharpaverage.size() > vMax)
                                sharpaverage.removeElementAt(0);
                            for (int i = 0; i < sharpaverage.size(); ++i) {
                                detune_avg += sharpaverage.get(i);
                            }
                            detune_avg /= sharpaverage.size();
                        }
                        col = ColorUtils.blendARGB(getResources().getColor(android.R.color.holo_green_light), getResources().getColor(android.R.color.holo_red_light), (float) Math.abs(detune_avg));
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView text = (TextView) findViewById(R.id.text_hz);
                        TextView text2 = (TextView) findViewById(R.id.text_letter);
                        text.setText("" + pitchInHz + " Hz");
                        text2.setText(letters[(key_num) % 12] + generateSubscript((key_num+8)/12));
                        pitch_hz = pitchInHz;
                        updateDisplay();
                    }
                });
            }
        };
        p = new PitchProcessor(PitchProcessor.PitchEstimationAlgorithm.FFT_YIN, 22050, 1024, pdh);
        dispatcher.addAudioProcessor(p);
        audioThread = new Thread(dispatcher, "Audio Dispatcher");
    }

    @Override
    public void onEnterAmbient(Bundle ambientDetails) {
        super.onEnterAmbient(ambientDetails);
        updateDisplay();
    }

    @Override
    public void onUpdateAmbient() {
        super.onUpdateAmbient();
        updateDisplay();
    }

    @Override
    public void onExitAmbient() {
        updateDisplay();
        super.onExitAmbient();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i("Activity", "onResume");
        audioThread.start();
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.i("Activity", "onPause");
    }

    @Override
    public void onStop() {
        p.processingFinished();
        dispatcher.stop();
        dispatcher.removeAudioProcessor(p);
        audioThread.interrupt();
        super.onStop();
        Log.i("Activity", "onStop");
    }

    private void updateDisplay() {
        if (isAmbient()) {
            mContainerView.setBackgroundColor(getResources().getColor(android.R.color.black));
            mHertzView.setTextColor(col);
            mLetterView.setTextColor(col);
            mClockView.setVisibility(View.VISIBLE);

            mClockView.setText(AMBIENT_DATE_FORMAT.format(new Date()));
        } else {
            mContainerView.setBackgroundColor(col);
            mHertzView.setTextColor(getResources().getColor(android.R.color.white));
            mLetterView.setTextColor(getResources().getColor(android.R.color.white));
            mClockView.setVisibility(View.GONE);
        }

        if (pitch_hz >= 0) {
            mHertzView.setVisibility(View.VISIBLE);
            mLetterView.setVisibility(View.VISIBLE);
        }
        else {
            mHertzView.setVisibility(View.GONE);
            mLetterView.setVisibility(View.GONE);
        }

        if (Math.abs(detune_avg) < tune_detect)
            mLetterView.setTypeface(Typeface.DEFAULT_BOLD, 1);
        else
            mLetterView.setTypeface(Typeface.DEFAULT, 0);
    }

    private String generateSubscript(int i) {
        // http://stackoverflow.com/questions/41856139/how-to-display-subscript-in-java
        StringBuilder sb = new StringBuilder();
        for (char ch : String.valueOf(i).toCharArray()) {
            sb.append((char) ('\u2080' + (ch - '0')));
        }
        return sb.toString();
    }


}
