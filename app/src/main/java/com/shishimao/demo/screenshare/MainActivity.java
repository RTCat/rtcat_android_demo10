package com.shishimao.demo.screenshare;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.shishimao.sdk.Configs;
import com.shishimao.sdk.Errors;
import com.shishimao.sdk.LocalStream;
import com.shishimao.sdk.RTCat;
import com.shishimao.sdk.Receiver;
import com.shishimao.sdk.RemoteStream;
import com.shishimao.sdk.Sender;
import com.shishimao.sdk.Session;
import com.shishimao.sdk.WebRTCLog;
import com.shishimao.sdk.apprtc.AppRTCAudioManager;
import com.shishimao.sdk.http.RTCatRequests;
import com.shishimao.sdk.tools.L;
import com.shishimao.sdk.view.VideoPlayer;
import com.shishimao.sdk.view.VideoPlayerLayout;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class MainActivity extends Activity {

    private final static String TAG  = "ScreenShareDemo";
    RTCat cat;
    VideoPlayer localVideoRenderer;
    VideoPlayerLayout videoRenderLayout;
    LocalStream localStream;
    String token;
    Session session;


    HashMap<String,Sender> senders = new HashMap<>();
    HashMap<String,Receiver> receivers = new HashMap<>();

    ArrayList<VideoPlayer> render_list = new ArrayList<>();
    HashMap<String, VideoPlayerLayout> render2_list = new HashMap<>();


    int layout_width = 50;
    int layout_height = 50;

    int x = 0;
    int y = 0;

    Intent screenIntent;

    private static final int PERMISSION_REQUEST_CODE = 777;
    private static final int SCREEN_REQUEST_CODE = 751;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_screen_share);

        localVideoRenderer = (VideoPlayer) findViewById(R.id.local_screen_render);
        videoRenderLayout = (VideoPlayerLayout) findViewById(R.id.local_screen_layout);
        videoRenderLayout.setPosition(50,50,50,50);



        String[] permissions = {
                android.Manifest.permission.RECORD_AUDIO,
        };

        if (ContextCompat.checkSelfPermission(this,android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
                && Build.VERSION.SDK_INT >= 23) {
            requestPermissions(permissions, PERMISSION_REQUEST_CODE);
        }else {
            start();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if(requestCode== PERMISSION_REQUEST_CODE){
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                start();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == SCREEN_REQUEST_CODE){
            Log.i(TAG,"result " + resultCode);
            if(resultCode == RESULT_OK){
                screenIntent = data;
                cat = new RTCat(this,true,true,true,true, AppRTCAudioManager.AudioDevice.SPEAKER_PHONE, RTCat.CodecSupported.H264, L.DEBUG);
                cat.addObserver(new RTCat.RTCatObserver() {
                    @Override
                    public void init() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                createScreenStream();
                            }
                        });
                    }
                    @Override
                    public void error(Errors error) {

                    }
                });
                cat.init();
            }
        }
    }


    private void start(){
        MediaProjectionManager manager = (MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Intent intent = manager.createScreenCaptureIntent();
        startActivityForResult(intent, SCREEN_REQUEST_CODE);
    }

    private  void  createScreenStream(){
        cat.initVideoPlayer(localVideoRenderer);

        localStream = cat.createStream(true,true,15,1024,1024,screenIntent);

        localStream.addObserver(new LocalStream.StreamObserver() {

            @Override
            public void error(Errors errors) {
                Log.e(TAG, errors.message);
            }

            @Override
            public void afterSwitch(boolean isFrontCamera) {}

            @Override
            public void accepted() {
                localStream.play(localVideoRenderer);
                createSession(null);
            }
        });

        localStream.init();
    }

    public void createSession(View view)
    {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {

                    RTCatRequests requests = new RTCatRequests(TestConfig.APIKEY, TestConfig.SECRET);
                    token = requests.getToken(TestConfig.P2P_SESSION, "pub");
                    l("token is " + token);
                    session = cat.createSession(token, Session.SessionType.P2P);

                    class SessionHandler implements Session.SessionObserver {
                        @Override
                        public void in(String token) {
                            l(token + " is in");
                            l(String.valueOf(session.getWits().size()));

                            if (session.getWits().size() < 3)
                            {
                                JSONObject attr = new JSONObject();
                                session.sendTo(localStream,true,attr, token);
                            }
                        }

                        @Override
                        public void close() {
                            finish();
                        }

                        @Override
                        public void out(String token) {
                            final VideoPlayerLayout layout =  render2_list.get(token);

                            if( x == 0 && y == 50)
                            {
                                x = 50 ; y =0;
                            }else if(x == 50 && y == 0)
                            {
                                x = 0;
                            }


                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if(layout != null)
                                    {
                                        RelativeLayout relativeLayout = (RelativeLayout) findViewById(R.id.video_layout);
                                        relativeLayout.removeView(layout);
                                    }
                                }
                            });
                        }

                        @Override
                        public void connected(ArrayList wits) {
                            l("connected main");

                            String wit = "";
                            for (int i = 0; i < wits.size(); i++) {
                                if( i == 3)
                                {
                                    break;
                                }
                                try {
                                    wit = wit + wits.get(i);

                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }


                            JSONObject attr = new JSONObject();
                            try {
                                attr.put("type", "main");
                                attr.put("name", "old wang");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            session.send(localStream,true,attr);
                        }

                        @Override
                        public void remote(final Receiver receiver) {
                            try {
                                receivers.put(receiver.getId(), receiver);

                                receiver.addObserver(new Receiver.ReceiverObserver() {
                                    @Override
                                    public void log(WebRTCLog.ReceiverClientLog object) {
                                        Log.d("Receiver Log ->",object.toString());
                                    }


                                    @Override
                                    public void error(Errors errors) {

                                    }

                                    @Override
                                    public void stream(final RemoteStream stream) {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                t(receiver.getFrom() + " stream");
                                                VideoPlayer videoViewRemote = new VideoPlayer(MainActivity.this);
                                                render_list.add(videoViewRemote);

                                                cat.initVideoPlayer(videoViewRemote);

                                                RelativeLayout layout = (RelativeLayout) findViewById(R.id.video_layout);
                                                VideoPlayerLayout remote_video_layout = new VideoPlayerLayout(MainActivity.this);

                                                render2_list.put(receiver.getFrom(),remote_video_layout);

                                                remote_video_layout.addView(videoViewRemote);

                                                remote_video_layout.setPosition(x,y,layout_width,layout_height);

                                                if( x == 0 && y == 0)
                                                {
                                                    x = 50;
                                                }else if(x == 50 && y == 0)
                                                {
                                                    x = 0; y= 50;
                                                }

                                                layout.addView(remote_video_layout);

                                                stream.play(videoViewRemote);
                                            }
                                        });

                                    }

                                    @Override
                                    public void message(String message) {
                                        try {
                                            JSONObject data = new JSONObject(message);
                                            String mes = data.getString("content");
                                        } catch (JSONException e) {
                                            l(e.toString());
                                        }

                                    }

                                    @Override
                                    public void close() {

                                    }

                                    @Override
                                    public void receiveFile(String fileName) {

                                    }

                                    @Override
                                    public void receiveFileFinish(File file) {

                                    }
                                });

                                receiver.response();
                            } catch (Exception e) {
                                l(e.toString());
                            }


                        }

                        @Override
                        public void local(final Sender sender) {
                            senders.put(sender.getId(), sender);
                            sender.addObserver(new Sender.SenderObserver() {
                                @Override
                                public void log(WebRTCLog.SenderClientLog object) {
                                    Log.d("Sender Log ->",object.toString());
                                }

                                @Override
                                public void close() {
                                    if(session.getState() == Configs.ConnectState.CONNECTED)
                                    {
                                        try{
                                            session.sendTo(localStream,false,null,sender.getTo());
                                        }catch (NullPointerException e){
                                            e.printStackTrace();
                                        }

                                    }
                                }

                                @Override
                                public void error(Errors errors) {

                                }

                                @Override
                                public void fileSendFinished() {

                                }
                            });
                        }

                        @Override
                        public void message(String token, String message) {
                            l(token + ":" +message);
                        }

                        @Override
                        public void error(Errors error) {
                            Log.e(TAG,error.message);
                        }
                    }

                    SessionHandler sh = new SessionHandler();

                    session.addObserver(sh);

                    session.connect();

                } catch (Exception e) {
                    l(e.toString());
                }
            }
        }).start();

    }

    public void l(String o)
    {

        Log.d(TAG, o);
    }


    public void t(String o)
    {
        Toast.makeText(this, o,
                Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {

        if(session != null)
        {
            session.disconnect();
        }


        if(localStream != null)
        {
            localStream.release();
        }

        if(localVideoRenderer != null)
        {
            localVideoRenderer.release();
            localVideoRenderer = null;
        }

        for (VideoPlayer renderer:render_list)
        {
            renderer.release();
        }

        if(cat != null)
        {
            cat.release();
        }

        Log.d("Test","EXIT");

        super.onDestroy();

    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

}
