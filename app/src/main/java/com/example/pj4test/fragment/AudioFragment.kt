package com.example.pj4test.fragment

import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.pj4test.ProjectConfiguration
import com.example.pj4test.audioInference.SnapClassifier
import com.example.pj4test.databinding.FragmentAudioBinding
import java.util.LinkedList
import kotlin.math.sqrt


class EvictingQueue<T>(private val maxSize: Int) {
    private val queue: LinkedList<T> = LinkedList()

    fun add(element: T) {
        if (queue.size >= maxSize) {
            queue.removeFirst()
        }
        queue.add(element)
    }

    fun remove(): T? {
        return queue.poll()
    }

    fun peek(): T? {
        return queue.peek()
    }

    fun size(): Int {
        return queue.size
    }

    fun isEmpty(): Boolean {
        return queue.isEmpty()
    }

    fun toArray(): Array<out Any> {
        return queue.toArray()
    }
}

fun euclideanDistance(point1: Triple<Float,Float, Float>, point2: Triple<Float,Float, Float>): Float {
    val dx = point2.first - point1.first
    val dy = point2.second - point1.second
    val dz = point2.third - point1.third

    val distanceSquared = dx * dx + dy * dy + dz * dz
    return sqrt(distanceSquared.toDouble()).toFloat()
}


class AudioFragment: Fragment(), SnapClassifier.DetectorListener, SensorEventListener {
    private val TAG = "AudioFragment"

    private var _fragmentAudioBinding: FragmentAudioBinding? = null
    private lateinit var sensorManager: SensorManager
    private lateinit var mAccelerometer: Sensor
    private lateinit var mGyroscope: Sensor
    private val queueAccelerometer = EvictingQueue<Triple<Float,Float,Float>>(100)
    private val queueGyroscope = EvictingQueue<Triple<Float,Float,Float>>(100)

    private val fragmentAudioBinding
        get() = _fragmentAudioBinding!!

    // classifiers
    lateinit var snapClassifier: SnapClassifier

    // views
    lateinit var snapView: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentAudioBinding = FragmentAudioBinding.inflate(inflater, container, false)

        return fragmentAudioBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        snapView = fragmentAudioBinding.SnapView

        snapClassifier = SnapClassifier()
        snapClassifier.initialize(requireContext())
        snapClassifier.setDetectorListener(this)

        sensorManager = requireActivity().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        mGyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        sensorManager.registerListener(this,
            mAccelerometer,
            SensorManager.SENSOR_DELAY_NORMAL,
            SensorManager.SENSOR_DELAY_NORMAL
        )
        sensorManager.registerListener(this,
            mGyroscope,
            SensorManager.SENSOR_DELAY_NORMAL,
            SensorManager.SENSOR_DELAY_NORMAL
        )
    }

    override fun onPause() {
        super.onPause()
        snapClassifier.stopInferencing()
    }

    override fun onResume() {
        super.onResume()
        snapClassifier.startInferencing()
    }

    override fun onResults(score: Float) {
        activity?.runOnUiThread {
            var gyroArray = queueGyroscope.toArray()
            var accelArray = queueAccelerometer.toArray()
            var prev = Triple(0.0f,0.0f,0.0f)
            var gyroSum = 0.0
            var accelSum = 0.0

            for (elem in gyroArray) {
                val item = elem as Triple<Float, Float, Float>
                gyroSum += euclideanDistance(prev, item)
                prev = item
            }
            prev = Triple(0.0f,0.0f,0.0f)
            for (elem in accelArray) {
                val item = elem as Triple<Float, Float, Float>
                accelSum += euclideanDistance(prev, item)
                prev = item
            }

            var isHectic = "/ NO HECTIC"
            if (gyroSum * 0.7 + accelSum * 0.3 >= SnapClassifier.HECTIC_THRESHOLD) {
                isHectic = "/ HECTIC"
                snapClassifier.startInferencing()
            } else {
                snapClassifier.stopInferencing()
            }
            Log.v("SCORE", score.toString())
            if (score > SnapClassifier.THRESHOLD) {
                snapView.setBackgroundColor(ProjectConfiguration.activeBackgroundColor)
                if (isHectic == "/ HECTIC") {
                    snapView.setBackgroundColor(Color.GREEN)
                    Log.v("GUARDIAN", "TRUE")
//                    callGuardian()
                }
                snapView.text = "SNAP" + isHectic
                snapView.setTextColor(ProjectConfiguration.activeTextColor)
            } else {
                snapView.text = "NO SNAP" + isHectic
                snapView.setBackgroundColor(ProjectConfiguration.idleBackgroundColor)
                if (isHectic == "/ HECTIC") {
                    snapView.setBackgroundColor(Color.BLUE)
                }
                snapView.setTextColor(ProjectConfiguration.idleTextColor)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
//            val sides = event.values[0]
//            val upDown = event.values[1]
//            val tmp = event.values.size
//            for (item in event.values) {
//                Log.v("MotionFragment acc", item.toString());
//            }
            queueAccelerometer.add(Triple(event.values[0], event.values[1], event.values[2]))
        }
        if (event?.sensor?.type == Sensor.TYPE_GYROSCOPE) {
//            for (item in event.values) {
//                Log.v("MotionFragment gyro", item.toString());
//            }
            queueGyroscope.add(Triple(event.values[0], event.values[1], event.values[2]))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        return
    }

}