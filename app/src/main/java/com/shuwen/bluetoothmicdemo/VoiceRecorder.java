package com.shuwen.bluetoothmicdemo;

import android.media.AudioRecord;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;

import static android.media.AudioFormat.CHANNEL_IN_MONO;
import static android.media.AudioFormat.CHANNEL_IN_STEREO;
import static android.media.AudioFormat.ENCODING_PCM_16BIT;
import static android.media.AudioFormat.ENCODING_PCM_8BIT;
import static android.media.AudioRecord.RECORDSTATE_RECORDING;
import static android.media.AudioRecord.STATE_INITIALIZED;
import static android.media.AudioRecord.STATE_UNINITIALIZED;
import static android.media.MediaRecorder.AudioSource.MIC;


/**
 * 声音录制类，音频录制由于MIC占用，当前只能有一个Recorder进行录制。
 */
/*
 * 需要考虑下面的支持情况：
 * 1、支持输出音频格式，目前可以支持的有mp3、opus
 * 2、支持按照指定格式进行音频文件保存，支持wav、mp3、opus格式
 * 3、支持采样率、通道数、录音位数的设置
 */
public class VoiceRecorder {

    public static final int BITRATE_16 = 16;
    public static final int BITRATE_8 = 8;
    private static final String TAG = "VoiceRecorder";
    public static final int DEFAULT_SAMPLE_RATE = 16000;
    public static final int DEFAULT_CHANNEL = 1;
    public static final int DEFAULT_BITRATE = BITRATE_16;
    public static final int DEFAULT_FRAME_TIME = 20;
    public static final int DEFAULT_BUFFER_SIZE = 1280;
    public static final int DEFAULT_MAX_RECORD_TIME = 15000;
    public static final int DEFAULT_SPLIT_RECORD_TIME = 5000;
    public static final int DEFAULT_OPUS_BITRATE = 27800;
    public static final int RECORD_ERROR = 1001;
    public static final int RECORD_PERMISSION_DENIED = 1002;
    public enum AudioFormat {
        PCM,
        OPU,
        OPUS,
        MP3,
        WAV
    }


    /* 以下内容是参数配置 */
    /** 该值目前默认为PCM格式，不支持设置，以后或许会支持扩展 */
    private AudioFormat audioFormat = AudioFormat.PCM;
    /** 如果不设置该值，将自动设置AudioRecord需要的最小缓存值 */
    private int bufferSize;
    private int sampleRate = DEFAULT_SAMPLE_RATE;
    private int channels = DEFAULT_CHANNEL;
    private int bitrate = DEFAULT_BITRATE;
    private int frameTime = DEFAULT_FRAME_TIME;
//    private String savePath = null;

    private Handler mUIHandler = new Handler(Looper.getMainLooper());

    private Callback mCallback;
    private volatile boolean isRecord = false;
    private AudioRecord mAudioRecord = null;

    /** 后台录制线程 */
    private Thread mVoiceThread = null;
    private Runnable mRunnable = new Runnable() {

        @Override
        public void run() {

            // short类型占用两个字节
            short[] pcmData = new short[getFrameSize() / 2];

            // 启动录制过程
            if (mAudioRecord != null && mAudioRecord.getState() == STATE_INITIALIZED) {
                try {
                    mAudioRecord.stop();
                    mAudioRecord.startRecording();
                } catch (Exception e) {
                    e.printStackTrace();
                    // 后面统一进行uninit操作
//                    unInitializeRecord();
                    doRecordFailed(RECORD_ERROR);
                    mAudioRecord = null;
                }
            }

            // 检测状态
            if (mAudioRecord != null && mAudioRecord.getState() == STATE_INITIALIZED &&
                    mAudioRecord.getRecordingState() != RECORDSTATE_RECORDING) {
                doRecordFailed(RECORD_ERROR);
                mAudioRecord = null;
            }
            if (mAudioRecord == null)  { isRecord = false; }

            int rLen;
            while(isRecord && mAudioRecord != null) {
                // 该read请求不会出现异常，不需要捕获
                rLen = mAudioRecord.read(pcmData, 0, pcmData.length);
                if (rLen != pcmData.length) {
                    isRecord = false;
                    doRecordFailed(RECORD_ERROR);
                    break;
                }
                // TODO: 2017/8/10 这里要进行转换
                mCallback.onRecord(pcmData);
            }
            unInitializeRecord();
            doRecordStop();
        }
    };

    private VoiceRecorder() {
    }

    public boolean start() {
        synchronized(this) {
            if (isStart()) {
                return true;
            }
            isRecord = true;
            if(readyToRecord() && initializeRecorder()) {
                Log.d(TAG, "start record");
                doRecordStart();
                mVoiceThread = new Thread(mRunnable);
                mVoiceThread.start();
                return true;
            }
        }
        isRecord = false;
        return false;
    }

    public void stop() {
        synchronized (this) {
            isRecord = false;
            /*if(mVoiceThread != null) {
                try {
                    mVoiceThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }*/
            mVoiceThread = null;
        }
    }

    public boolean isStart() {
        return isRecord;
    }

    private boolean readyToRecord() {
        Log.d(TAG, "doRecordReady");
        return mCallback == null || mCallback.doRecordReady();
    }

    private void doRecordStart() {
        runOnUIThread(new Runnable() {
            @Override
            public void run() {
                if(mCallback != null) {
                    mCallback.onRecordStart();
                }
            }
        });
    }

    private void doRecordStop() {
        if(mCallback != null) {
            mCallback.onRecordStop();
        }

        /*runOnUIThread(new Runnable() {
            @Override
            public void run() {
                if(mCallback != null) {
                    mCallback.onRecordStop();
                }
            }
        });*/
    }

    private void doRecordFailed(final int errorCode) {
        runOnUIThread(new Runnable() {
            @Override
            public void run() {
                if(mCallback != null) {
                    mCallback.onRecordFailed(errorCode);
                }
            }
        });
    }

    /**
     * 初始化Recorder，进行记录，如果出现问题，返回false，导致{@link #start()}方法返回false。
     * @return 初始化结果
     */
    private boolean initializeRecorder() {
        if(this.mCallback == null) {
            Log.d(TAG, "initializeRecorder failed, mCallback is null.");
            return false;
        }
        // 由于目前都是PCM格式进行录制，因此直接为pcm相应格式
        int format = getPcmFormat(bitrate);
        // 获取channelConfig 对应值
        int channelConfig = channels!=2 ? CHANNEL_IN_MONO : CHANNEL_IN_STEREO;

        // 重设缓冲区大小
        int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, format);
        if(bufferSize < minBufferSize) {
            bufferSize = minBufferSize;
            Log.d(TAG, "Resize bufferSize to minBufferSize: " + bufferSize);
        }
        
        if (mAudioRecord != null && mAudioRecord.getState() != STATE_UNINITIALIZED) {
            unInitializeRecord();
        }
        mAudioRecord = new AudioRecord(MIC, sampleRate, channelConfig, format, bufferSize);
        if (mAudioRecord.getState() == STATE_UNINITIALIZED) {
            mCallback.onRecordFailed(RECORD_PERMISSION_DENIED);
        }

        if (mAudioRecord.getState() != STATE_INITIALIZED) {
            Log.d(TAG, "initializeRecorder failed audioRecord status error.");
            return false;
        }
        Log.d(TAG, "initializeRecorder succeed.");
        return true;
    }

    private void unInitializeRecord() {
        Log.d(TAG, "unInitializeRecord: start");
        synchronized(this) {
            if(mAudioRecord != null) {
                try {
                    mAudioRecord.stop();
                    mAudioRecord.release();
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.d(TAG, "unInitializeRecord: release failed! error occur");
                }
                mAudioRecord = null;
            }
        }
    }

    private void runOnUIThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            if (mUIHandler != null) {
                mUIHandler.post(runnable);
            }
        }
    }

    private int getPcmFormat(int bitrate) {
        switch (bitrate) {
            case BITRATE_16:
                return ENCODING_PCM_16BIT;
            case BITRATE_8:
                return ENCODING_PCM_8BIT;
            default :
                return ENCODING_PCM_16BIT;
        }
    }

    private int getFrameSize() {
        // 将20ms作为一帧音频展示
        return sampleRate * channels * changeBitrate(bitrate) * frameTime / 1000;
    }

    private int changeBitrate(int bitrate) {
        switch (bitrate) {
            case BITRATE_16:
                return 2;
            case BITRATE_8:
                return 1;
            default :
                return 2;
        }
    }

    public static class Builder {

        private final VoiceRecorder recorder;
        private boolean built = false;

        public Builder(@NonNull Callback callback) {
            built = false;
            recorder = new VoiceRecorder();
            recorder.mCallback = callback;
        }

        public Builder sampleRate(int sampleRate) {
            checkBuilt();
            recorder.sampleRate = sampleRate;
            return this;
        }

        /**
         * 通道数设置，目前只支持两个值：1 和 2
         * @param channels 通道数
         * @return 建造器
         */
        public Builder channels(int channels) {
            checkBuilt();
            if (channels != 1 && channels != 2) {
                throw new IllegalArgumentException("channels: " + channels + " not support");
            }
            recorder.channels = channels;
            return this;
        }

        /**
         * 音频比特率设置，目前只支持两个值：See {}、
         * {@link }
         * @param bitrate 比特率
         * @return 建造器
         */
        public Builder bitrate(int bitrate) {
            checkBuilt();
            if (bitrate != BITRATE_16 && bitrate != BITRATE_8) {
                throw new IllegalArgumentException("bitrate: " + bitrate + " not support");
            }
            recorder.bitrate = bitrate;
            return this;
        }

        public Builder format(AudioFormat format) {
            checkBuilt();
            switch (format) {
                case PCM:
                case OPU:
                    recorder.audioFormat = format;
                    break;
                case MP3:
                case OPUS:
                case WAV:
                default:
                    throw new IllegalArgumentException("audio format " + format.name() + " not support now.");

            }
            return this;
        }

        public Builder bufferSize(int bufferSize) {
            checkBuilt();
            recorder.bufferSize = bufferSize;
            return this;
        }

        public Builder frameTime(int frameTime) {
            checkBuilt();
            recorder.frameTime = frameTime;
            return this;
        }

        public VoiceRecorder build() {
            built = true;
            return recorder;
        }
        private void checkBuilt(){
            if(built) throw new IllegalStateException("VoiceRecorder object has already been built from this Builder object");
        }
    }

    public interface Callback {

        /**
         * 录音操作前的准备工作，根据返回结果判断是否需要要继续进行录制。
         *
         * @return 是否进行录制
         */
        boolean doRecordReady();

        void onRecordStart();

        void onRecordStop();

        /**
         * 每一帧的音频数据，格式为pcm，只有该回调方法在子线程中被调用，其它回调方法在主线程中被调用。
         * @param voice 音频数据
         */
        void onRecord(short[] voice);

        void onRecordFailed(int errorCode);
    }
}
