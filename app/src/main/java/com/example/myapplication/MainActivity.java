package com.example.myapplication;

import android.content.Intent;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;

import androidx.appcompat.app.AppCompatActivity;

import com.google.ar.core.Anchor;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.Frame;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.ExternalTexture;
import com.google.ar.sceneform.rendering.ModelRenderable;

import java.util.Collection;

public class MainActivity extends AppCompatActivity {
    private ExternalTexture texture;
    private MediaPlayer mediaPlayer;
    private CustomArFragment arFragment;
    private Scene scene;
    private ModelRenderable renderable;
    private boolean isImageDetected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        texture = new ExternalTexture();

        mediaPlayer = MediaPlayer.create(this, R.raw.video);
        mediaPlayer.setSurface(texture.getSurface());
        mediaPlayer.setLooping(true);

        ModelRenderable
                .builder()
                .setSource(this, Uri.parse("video_screen.sfb"))
                .build()
                .thenAccept(modelRenderable -> {
                    modelRenderable.getMaterial().setExternalTexture("videoTexture",
                            texture);
                    modelRenderable.getMaterial().setFloat4("keyColor",
                            new Color(0.01843f, 1f, 0.098f));

                    renderable = modelRenderable;
                });

        arFragment = (CustomArFragment)
                getSupportFragmentManager().findFragmentById(R.id.arFragment);

        assert arFragment != null;
        arFragment.path = "";
        scene = arFragment.getArSceneView().getScene();

        scene.addOnUpdateListener(this::onUpdate);

        findViewById(R.id.button).setOnClickListener(view -> chooseVideo());
        findViewById(R.id.playButton).setOnClickListener(view -> playVideo());
        findViewById(R.id.playButton).setEnabled(false);
    }

    private void onUpdate(FrameTime frameTime) {
        if (isImageDetected)
            return;

        Frame frame = arFragment.getArSceneView().getArFrame();

        assert frame != null;
        Collection<AugmentedImage> augmentedImages =
                frame.getUpdatedTrackables(AugmentedImage.class);

        for (AugmentedImage image : augmentedImages) {
            if (image.getTrackingState() == TrackingState.TRACKING) {
                if (image.getName().equals("image")) {
                    isImageDetected = true;
                    prepareVideo(image.createAnchor(image.getCenterPose()), image.getExtentX(), image.getExtentZ());
                    break;
                }
            }
        }
    }

    private void prepareVideo(Anchor anchor, float extentX, float extentZ) {
        AnchorNode anchorNode = new AnchorNode(anchor);
        texture.getSurfaceTexture().setOnFrameAvailableListener(surfaceTexture -> {
            anchorNode.setRenderable(renderable);
            texture.getSurfaceTexture().setOnFrameAvailableListener(null);
        });
        anchorNode.setWorldScale(new Vector3(extentX, 1f, extentZ));
        anchorNode.setOnTapListener((var1, var2) -> onTap());
        scene.addChild(anchorNode);
        findViewById(R.id.playButton).setEnabled(true);
    }

    public void onTap() {
        playVideo();
    }

    public void playVideo() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
        else {
            mediaPlayer.start();
        }
    }

    public void updateMediaPlayer(String path) {
        mediaPlayer.release();
        mediaPlayer = MediaPlayer.create(this, Uri.parse(path));
        mediaPlayer.setSurface(texture.getSurface());
        mediaPlayer.setLooping(true);

        mediaPlayer.start();
        mediaPlayer.pause();
    }

    public void chooseVideo() {
        Intent intent = new Intent();
        intent.setType("video/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent,"Select Video"), 3);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == 3) {
            Uri selectedImageUri = data.getData();
            String path = getPath(selectedImageUri);
            updateMediaPlayer(path);
        }
    }

    public String getPath(Uri uri) {
        String[] projection = { MediaStore.Video.Media.DATA };
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        if (cursor != null) {
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        } else
            return null;
    }
}
