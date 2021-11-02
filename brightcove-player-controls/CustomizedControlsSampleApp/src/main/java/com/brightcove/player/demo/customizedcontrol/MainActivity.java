package com.brightcove.player.demo.customizedcontrol;

import com.brightcove.player.edge.Catalog;
import com.brightcove.player.edge.VideoListener;
import com.brightcove.player.event.Event;
import com.brightcove.player.event.EventEmitter;
import com.brightcove.player.event.EventListener;
import com.brightcove.player.event.EventType;

import com.brightcove.player.controller.BrightcoveAudioTracksController;
import com.brightcove.player.mediacontroller.BrightcoveMediaController;
import com.brightcove.player.model.Video;
import com.brightcove.player.view.BaseVideoView;
import com.brightcove.player.view.BrightcoveExoPlayerVideoView;
import com.brightcove.player.view.BrightcovePlayer;

import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.List;


/**
 * This app illustrates how to customize the Android default media controller.
 *
 * @author Sergio Martinez
 */
public class MainActivity extends BrightcovePlayer {
    //This TTF font is included in the Brightcove SDK.
    public static final String FONT_AWESOME = "fontawesome-webfont.ttf";

    private int currentTrack = 0;
    private String selectedTrack;
    private List<String> tracks;

    @Override protected void onCreate(Bundle savedInstanceState) {
        // When extending the BrightcovePlayer, we must assign the BrightcoveVideoView before
        // entering the superclass. This allows for some stock video player lifecycle
        // management.  Establish the video object and use it's event emitter to get important
        // notifications and to control logging.
        setContentView(R.layout.default_activity_main);
        brightcoveVideoView = (BrightcoveExoPlayerVideoView) this.<View>findViewById(R.id.brightcove_video_view);
        initMediaController(brightcoveVideoView);

        super.onCreate(savedInstanceState);

        String account = getString(R.string.account);
        EventEmitter eventEmitter = brightcoveVideoView.getEventEmitter();

        Catalog catalog = new Catalog.Builder(eventEmitter, account)
                .setBaseURL(Catalog.DEFAULT_EDGE_BASE_URL)
                .setPolicy(getString(R.string.policy))
                .build();

        catalog.findVideoByID(getString(R.string.videoId), new VideoListener() {

            // Add the video found to the queue with add().
            // Start playback of the video with start().
            @Override
            public void onVideo(Video video) {
                brightcoveVideoView.add(video);
                fullScreen();
                brightcoveVideoView.start();
            }
        });

        eventEmitter.on(EventType.ENTER_FULL_SCREEN, event -> setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE));

        eventEmitter.on(EventType.EXIT_FULL_SCREEN, event -> setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
    }

    public void initMediaController(final BaseVideoView brightcoveVideoView) {
        if (BrightcoveMediaController.checkTvMode(this)) {
            // Use this method to verify if we're running in Android TV
            brightcoveVideoView.setMediaController(new BrightcoveMediaController(brightcoveVideoView, R.layout.my_tv_media_controller));
        } else {
            brightcoveVideoView.setMediaController(new BrightcoveMediaController(brightcoveVideoView, R.layout.my_media_controller));
        }
        initButtons(brightcoveVideoView);

        // This event is sent by the BrightcovePlayer Activity when the onConfigurationChanged has been called.
        brightcoveVideoView.getEventEmitter().on(EventType.CONFIGURATION_CHANGED, event -> initButtons(brightcoveVideoView));
    }

    private void initButtons(final BaseVideoView brightcoveVideoView) {

        Typeface font = Typeface.createFromAsset(this.getAssets(), FONT_AWESOME);
        Button thumbsUp = brightcoveVideoView.findViewById(R.id.thumbs_up);
        if (thumbsUp != null) {
            // By setting this type face, we can use the symbols(icons) present in the font awesome file.
            thumbsUp.setTypeface(font);
        }
        thumbsUp.setOnClickListener(v -> Toast.makeText(MainActivity.this, "TEST", Toast.LENGTH_SHORT).show());

        BrightcoveAudioTracksController customAudioControl = new BrightcoveAudioTracksController(brightcoveVideoView, this) {

            @Override
            public void showAudioTracksDialog() {
                Log.d("Custom Tag", "Custom showAudioTracksDialog");

                CharSequence[] list = new CharSequence[tracks.size()];

                for (int i = 0; i < tracks.size(); i++) {
                    switch (tracks.get(i)) {
                        case "en-au": list[i] = "English (Australian)"; break;
                        case "en": list[i] = "English"; break;
                        case "ja": list[i] = "Japanese"; break;
                        default:
                            Log.e("Audio Tracks", "Error - No matching tracks");
                            return;
                    }

                    Log.d("List of tracks", "Tracks are: " + list[i]);
                }

                new AlertDialog.Builder(context)
                        .setTitle(R.string.brightcove_audio_track_selection)
                        .setSingleChoiceItems(list, currentTrack, (dialog, which) -> {
                            currentTrack = which;
                            Log.v(TAG, "onClick: which = " + which);
                            selectAudioTrack(which);
                        })
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            eventEmitter.emit(EventType.AUDIO_TRACKS_DIALOG_OK);
                            dialog.dismiss();
                        })
                        .show();
            }
        };

        Button audioTracks = brightcoveVideoView.findViewById(R.id.audio_tracks);
        audioTracks.setOnClickListener(new View.OnClickListener() {
            private int audioTracksDialogOkToken;
            private int audioTracksDialogSettingsToken;
            private int activityResumedToken;
            private int fragmentResumedToken;

            EventEmitter eventEmitter = brightcoveVideoView.getEventEmitter();

            @Override
            public void onClick(View view) {

                if (brightcoveVideoView.isPlaying()) {
                    brightcoveVideoView.pause();
                    audioTracksDialogOkToken = eventEmitter.once(EventType.AUDIO_TRACKS_DIALOG_OK, event -> {
                       brightcoveVideoView.start();
                        eventEmitter.off(EventType.AUDIO_TRACKS_DIALOG_SETTINGS, audioTracksDialogSettingsToken);
                    });

                    audioTracksDialogSettingsToken = eventEmitter.once(EventType.AUDIO_TRACKS_DIALOG_SETTINGS, event -> {
                        activityResumedToken = eventEmitter.once(EventType.ACTIVITY_RESUMED, event1 -> {
                            brightcoveVideoView.start();
                            eventEmitter.off(EventType.FRAGMENT_RESUMED, fragmentResumedToken);
                        });

                        fragmentResumedToken = eventEmitter.once(EventType.FRAGMENT_RESUMED, event12 -> {
                            brightcoveVideoView.start();
                            eventEmitter.off(EventType.ACTIVITY_RESUMED, activityResumedToken);
                        });

                        eventEmitter.off(EventType.AUDIO_TRACKS_DIALOG_OK, audioTracksDialogOkToken);
                    });
                }
                customAudioControl.showAudioTracksDialog();
            }
        });

        customAudioControl.getEventEmitter().on(EventType.AUDIO_TRACKS, event -> {
            tracks = (List<String>) event.properties.get(Event.TRACKS);
            selectedTrack = (String) event.properties.get(Event.SELECTED_TRACK);
            Log.v("Custom Audio Dialog Tag", "Custom Audio Controller: tracks = " + tracks);

            if (selectedTrack != null) {
                for (int i = 0; i < tracks.size(); i++) {
                    if (tracks.get(i).equals(selectedTrack)) {
                        currentTrack = i;
                        break;
                    }
                }
            }
        });
    }
}