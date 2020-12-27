package edu.osu.pcv.marslogger;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;

import edu.osu.pcv.marslogger.ImuViewContent.SingleAxis;

/**
 * A fragment representing a list of Items.
 * <p/>
 * Activities containing this fragment MUST implement the {@link OnListFragmentInteractionListener}
 * interface.
 */
public class ImuViewFragment extends Fragment implements SensorEventListener {
    private static final String ARG_COLUMN_COUNT = "column-count";
    private int mColumnCount = 1;
    private OnListFragmentInteractionListener mListener;
    ImuRecyclerViewAdapter mAdapter;
    private final int mSensorRate = SensorManager.SENSOR_DELAY_UI;
    private SensorManager mSensorManager;
    private Sensor mAccel;
    private Sensor mGyro;
    private Sensor mMag;
    private class SensorPacket {
        long timestamp;
        float[] values;

        SensorPacket(long time, float[] vals) {
            timestamp = time;
            values = vals;
        }
    }
    private HandlerThread mSensorThread;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ImuViewFragment() {
    }

    // TODO: Customize parameter initialization
    @SuppressWarnings("unused")
    public static ImuViewFragment newInstance(int columnCount) {
        ImuViewFragment fragment = new ImuViewFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_COLUMN_COUNT, columnCount);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            mColumnCount = getArguments().getInt(ARG_COLUMN_COUNT);
        }

        mSensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        mAccel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER); // warn: mAccel can be null.
        mGyro = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE); // warn: mGyro can be null.
        mMag = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_imu_list, container, false);
        if (view instanceof RecyclerView) {
            Context context = view.getContext();
            RecyclerView recyclerView = (RecyclerView) view;
            if (mColumnCount <= 1) {
                recyclerView.setLayoutManager(new LinearLayoutManager(context));
            } else {
                recyclerView.setLayoutManager(new GridLayoutManager(context, mColumnCount));
            }
            mAdapter = new ImuRecyclerViewAdapter(ImuViewContent.ITEMS, mListener);
            recyclerView.setAdapter(mAdapter);
        }
        return view;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnListFragmentInteractionListener) {
            mListener = (OnListFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnListFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        registerImu();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterImu();
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p/>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnListFragmentInteractionListener {
        void onListFragmentInteraction(SingleAxis item);
    }


    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            int dummya = accuracy;
        } else if (sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            int dummyg = accuracy;
        }
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            SensorPacket sp = new SensorPacket(event.timestamp, event.values);
            for (int i = 0; i < 3; ++i) {
                mAdapter.updateListItem(i, sp.values[i]);
            }
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            SensorPacket sp = new SensorPacket(event.timestamp, event.values);
            for (int i = 0; i < 3; ++i) {
                mAdapter.updateListItem(i + 3, sp.values[i]);
            }
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            SensorPacket sp = new SensorPacket(event.timestamp, event.values);
            for (int i = 0; i < 3; ++i) {
                mAdapter.updateListItem(i + 6, sp.values[i]);
            }
        }
        getActivity().runOnUiThread(new Runnable(){
            public void run() {
                mAdapter.notifyDataSetChanged();
            }
        });
    }

    /**
     * This will register all IMU listeners
     * https://stackoverflow.com/questions/3286815/sensoreventlistener-in-separate-thread
     */
    public void registerImu() {
        mSensorThread = new HandlerThread("Sensor thread",
                Process.THREAD_PRIORITY_MORE_FAVORABLE);
        mSensorThread.start();
        // Blocks until looper is prepared, which is fairly quick
        Handler sensorHandler = new Handler(mSensorThread.getLooper());
        mSensorManager.registerListener(
                this, mAccel, mSensorRate, sensorHandler);
        mSensorManager.registerListener(
                this, mGyro, mSensorRate, sensorHandler);
        mSensorManager.registerListener(
                this, mMag, mSensorRate, sensorHandler);
    }

    /**
     * This will unregister all IMU listeners
     */
    public void unregisterImu() {
        mSensorManager.unregisterListener(this, mAccel);
        mSensorManager.unregisterListener(this, mGyro);
        mSensorManager.unregisterListener(this, mMag);
        mSensorManager.unregisterListener(this);
        mSensorThread.quitSafely();
    }
}
