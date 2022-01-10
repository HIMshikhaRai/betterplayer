package com.jhomlala.better_player;

import static android.content.Context.AUDIO_SERVICE;
import static android.media.AudioManager.STREAM_MUSIC;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.TrackNameProvider;
import com.google.android.exoplayer2.util.DebugTextViewHelper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.LogRecord;

public class NerdStatHelper extends DebugTextViewHelper implements AnalyticsListener {
    /// nerd stats variables
    private double totalBufferedDs = 0;
    private long bitrateEstimateValue = 0;
    private double bytesDownloaded = 0;
    private double currentBytesDownloaded = 0;
    private QueuingEventSink eventSink;
    private TrackSelectionArray trackSelections;
    private TrackNameProvider trackNameProvider;
    private String audioTrackName = "";
    private String videoTrackName = "";
    private Context context;
    private SimpleExoPlayer exoPlayer;
    private Handler statsHandler = new Handler();
    private Runnable statsRunnable = new Runnable() {
        @Override
        public void run() {
            getVideoString();
            statsHandler.removeCallbacks(statsRunnable);
            statsHandler.postDelayed(statsRunnable, 1000);
        }
    };

    public NerdStatHelper(SimpleExoPlayer exoPlayer, TextView textView, QueuingEventSink queuingEventSink, TrackSelectionArray trackSelectionArray, TrackNameProvider trackNameProvider,
                          Context context) {
        super(exoPlayer, textView);
        eventSink = queuingEventSink;
        trackSelections = trackSelectionArray;
        this.trackNameProvider = trackNameProvider;
        this.context = context;
        this.exoPlayer = exoPlayer;
    }

    public void init() {
        exoPlayer.addAnalyticsListener(this);
        start();
    }

    @Override
    public void onBandwidthEstimate(EventTime eventTime, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate) {
        totalBufferedDs = eventTime.totalBufferedDurationMs;
        bitrateEstimateValue = bitrateEstimate;
        currentBytesDownloaded = totalBytesLoaded;
        bytesDownloaded = totalBytesLoaded;
    }

    @Override
    public void onLoadCompleted(EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
        bytesDownloaded = 0;
    }

    @Override
    public void onLoadStarted(EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
        bytesDownloaded = currentBytesDownloaded;
    }

    @Override
    public void onLoadError(EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData, IOException error, boolean wasCanceled) {
        bytesDownloaded = 0;
        bitrateEstimateValue = 0;
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        this.trackSelections = trackSelections;
    }

    @Override
    public void onPlaybackStateChanged(EventTime eventTime, int state) {
        if(Player.STATE_READY == state){
            statsHandler.postDelayed(statsRunnable,1000);
        }

    }

    @Override
    protected String getDebugString() {
        return super.getDebugString();
    }

    @Override
    protected String getVideoString() {
        if (trackSelections.get(C.TRACK_TYPE_AUDIO) != null) {
            audioTrackName = (trackNameProvider.getTrackName(trackSelections.get(C.TRACK_TYPE_AUDIO).getFormat(0)));
        }

        if (trackSelections.get(C.TRACK_TYPE_DEFAULT) != null) {
            videoTrackName = (trackNameProvider.getTrackName(trackSelections.get(C.TRACK_TYPE_DEFAULT).getFormat(0)));
        }

        AudioManager audioManager = (AudioManager) context.getSystemService(AUDIO_SERVICE);
        int currentVolume = audioManager.getStreamVolume(STREAM_MUSIC);
        int maxVolume = audioManager.getStreamMaxVolume(STREAM_MUSIC);
        int currentVolumePercentage = 100 * currentVolume / maxVolume;

        Format format = exoPlayer.getVideoFormat();
        DecoderCounters decoderCounters = exoPlayer.getVideoDecoderCounters();
        String buffer = DemoUtil.getFormattedDouble(exoPlayer.getTotalBufferedDuration() / Math.pow(10.0, 3.0), 1);

        String brEstimateFloat = DemoUtil.getFormattedDouble(bitrateEstimateValue / Math.pow(10.0, 3.0), 1);
        if (format == null)
            return "";
        if (format != null || decoderCounters != null) {
            String data = "Buffer Health: " + buffer + " s" + "\n" +
                    "Conn Speed: " + DemoUtil.humanReadableByteCount(
                    bitrateEstimateValue, true, true) + "ps" + "\n" +
                    "Video: " + format.width + "x" + format.height + " / " + format.sampleMimeType.replace("video/", "") + "\n" +
                    "Audio: " + currentVolumePercentage + "% / " + exoPlayer.getAudioFormat().sampleMimeType.replace("audio/", "") + "\n" +
                    "Current: " + videoTrackName + " / " + audioTrackName + "\n" +
                    "Frames: " + getDecoderCountersBufferCountString(decoderCounters);
            Map<String, Object> event = new HashMap<>();
            event.put("event", "nerdStat");
            event.put("values", data);
            eventSink.success(event);
        } else if (format == null || decoderCounters == null) {
            Map<String, Object> event = new HashMap<>();
            event.put("event", "nerdStat");
            event.put("values", "");
            eventSink.success(event);
            return "";
        } else {
            String data = "Buffer Health: " + buffer + " s" + "\n" +
                    "Conn Speed: " + DemoUtil.humanReadableByteCount(
                    bitrateEstimateValue, true, true) + "ps" + "\n" +
                    "Video: " + format.width + "x" + format.height + " / " + format.sampleMimeType.replace("video/", "") + "\n" +
                    "Audio: " + currentVolumePercentage + "% / " + exoPlayer.getAudioFormat().sampleMimeType.replace("audio/", "") + "\n" +
                    "Current: " + videoTrackName + " / " + audioTrackName + "\n" +
                    "Frames: " + getDecoderCountersBufferCountString(decoderCounters);

            Map<String, Object> event = new HashMap<>();
            event.put("event", "nerdStat");
            event.put("values", data);
            eventSink.success(event);
            return data;
        }


        return super.getVideoString();
    }

    private String getDecoderCountersBufferCountString(DecoderCounters counters) {
        if (counters == null) {
            return "";
        }
        counters.ensureUpdated();
        return (counters.droppedBufferCount + " dropped of " + counters.renderedOutputBufferCount);
    }
}