package com.avmtechologies.cipr.fragments;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.res.AssetFileDescriptor;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.Toast;

import com.avmtechologies.cipr.R;
import com.avmtechologies.cipr.utils.VideoAnchorNode;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.ExternalTexture;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.PlaneDiscoveryController;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import androidx.annotation.NonNull;

import static android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT;
import static android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION;
import static android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH;

public class CiprArFragment extends ArFragment {

    private static final String TAG = "ArVideoFragment";
    private static final boolean VIDEO_CROP_ENABLED = true;
    private static final String MATERIAL_IMAGE_SIZE = "imageSize";
    private static final String MATERIAL_VIDEO_SIZE = "videoSize";
    private static final String MATERIAL_VIDEO_CROP = "videoCropEnabled";
    private static final String MATERIAL_VIDEO_ALPHA = "videoAlpha";
    private static final String TEXTURE_VIDEO_NAME = "texture_video.mp4";

    private MediaPlayer mediaPlayer;
    private ExternalTexture externalTexture;
    private ModelRenderable videoRenderable;
    private VideoAnchorNode videoAnchorNode;

    private AugmentedImage currentAugmentedImage;

    @Override
    public void onCreate(@Nullable @androidx.annotation.Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mediaPlayer = new MediaPlayer();
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            @Nullable @androidx.annotation.Nullable ViewGroup container,
            @Nullable @androidx.annotation.Nullable Bundle savedInstanceState
    ) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        PlaneDiscoveryController planeDiscoveryController = getPlaneDiscoveryController();
        planeDiscoveryController.hide();
        planeDiscoveryController.setInstructionView(null);

        getArSceneView().getPlaneRenderer().setEnabled(false);
        getArSceneView().setLightEstimationEnabled(false);

        initializeSession();
        createArScene();

        return view;
    }

    @Override
    protected Config getSessionConfiguration(Session session) {
        Config config = super.getSessionConfiguration(session);
        config.setLightEstimationMode(Config.LightEstimationMode.AMBIENT_INTENSITY);
        config.setFocusMode(Config.FocusMode.AUTO);
        try (InputStream inputStream = requireContext().getAssets().open("cipr_db.imgdb")) {
            config.setAugmentedImageDatabase(AugmentedImageDatabase.deserialize(session, inputStream));
        } catch (IllegalArgumentException e) {
            Toast.makeText(
                    requireActivity(),
                    "Could not add bitmap to augmented image database",
                    Toast.LENGTH_SHORT
            ).show();
        } catch (IOException e) {
            // The Augmented Image database could not be deserialized; handle this error appropriately.
            Toast.makeText(
                    requireActivity(),
                    "Problems with deserialization",
                    Toast.LENGTH_SHORT
            ).show();
        }
        return config;
    }

    private void createArScene() {
        // Create an ExternalTexture for displaying the contents of the video.
        externalTexture = new ExternalTexture();
        mediaPlayer.setSurface(externalTexture.getSurface());

        // Create a renderable with a material that has a parameter of type 'samplerExternal' so that
        // it can display an ExternalTexture.
        ModelRenderable.builder()
                .setSource(requireContext(), R.raw.t_shirt)
                .build()
                .thenAccept(renderable -> {
                    videoRenderable = renderable;
                    renderable.setShadowCaster(false);
                    renderable.setShadowReceiver(false);
                    renderable.getMaterial().setExternalTexture("videoTexture", externalTexture);
                    renderable.getMaterial().setFloat4("keyColor", new Color(0.0f, 0.0f, 1.0f));
                })
                .exceptionally(throwable -> {
                    Toast.makeText(requireContext(), "Unable to load renderable", Toast.LENGTH_LONG).show();
                    return null;
                });

        videoAnchorNode = new VideoAnchorNode();
        videoAnchorNode.setParent(getArSceneView().getScene());
    }

    @Override
    public void onUpdate(FrameTime frameTime) {

        Frame frame = getArSceneView().getArFrame();
        if (frame == null) {
            return;
        }

        Collection<AugmentedImage> updatedAugmentedImages = frame.getUpdatedTrackables(AugmentedImage.class);
        for (AugmentedImage augmentedImage : updatedAugmentedImages) {
            switch (augmentedImage.getTrackingState()) {
                case PAUSED:
                case STOPPED:
                    if (isArVideoPlaying()) {
                        pauseArVideo();
                    }
                    break;
                case TRACKING:
                    handleTrackingImage(augmentedImage);
                    break;
            }
        }
    }

    private void handleTrackingImage(AugmentedImage augmentedImage) {
        switch (augmentedImage.getTrackingMethod()) {
            case FULL_TRACKING:
                if (currentAugmentedImage != null) {
                    if (!isArVideoPlaying() && currentAugmentedImage.getIndex() == augmentedImage.getIndex()) {
                        resumeArVideo();
                    } else {
                        return;
                    }
                } else {
                    try {
                        playbackArVideo(augmentedImage);
                    } catch (IOException e) {
                        Log.e(TAG, "Could not play video [${augmentedImage.name}]", e);
                    }
                }
                break;
            case NOT_TRACKING:
            case LAST_KNOWN_POSE:
                if (currentAugmentedImage != null
                        && isArVideoPlaying()
                        && currentAugmentedImage.getIndex() == augmentedImage.getIndex()) {
                    pauseArVideo();
                }
                break;
        }
    }

    private void playbackArVideo(AugmentedImage augmentedImage) throws IOException {
        Log.d(TAG, "playbackVideo = ${augmentedImage.name}");

        AssetFileDescriptor descriptor;
        descriptor = requireContext().getAssets().openFd(TEXTURE_VIDEO_NAME);

        MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
        metadataRetriever.setDataSource(
                descriptor.getFileDescriptor(),
                descriptor.getStartOffset(),
                descriptor.getLength()
        );

        float videoWidth = Float.parseFloat(metadataRetriever.extractMetadata(METADATA_KEY_VIDEO_WIDTH));
        float videoHeight = Float.parseFloat(metadataRetriever.extractMetadata(METADATA_KEY_VIDEO_HEIGHT));
        float videoRotation = Float.parseFloat(metadataRetriever.extractMetadata(METADATA_KEY_VIDEO_ROTATION));

        // Account for video rotation, so that scale logic math works properly
        RectF imageSize = new RectF(0f, 0f, augmentedImage.getExtentX(), augmentedImage.getExtentZ());
        Matrix matrix = new Matrix();
        matrix.setRotate(videoRotation);

        videoAnchorNode.setVideoProperties(videoWidth, videoHeight, videoRotation, imageSize.width(), imageSize.height());

        // Update the material parameters
        videoRenderable.getMaterial().setFloat2(MATERIAL_IMAGE_SIZE, imageSize.width(), imageSize.height());
        videoRenderable.getMaterial().setFloat2(MATERIAL_VIDEO_SIZE, videoWidth, videoHeight);
        videoRenderable.getMaterial().setBoolean(MATERIAL_VIDEO_CROP, VIDEO_CROP_ENABLED);

        mediaPlayer.reset();
        mediaPlayer.setDataSource(descriptor);
        mediaPlayer.setLooping(true);
        mediaPlayer.prepare();
        mediaPlayer.start();

        if (videoAnchorNode.getAnchor() != null) {
            videoAnchorNode.getAnchor().detach();
        }
        videoAnchorNode.setAnchor(augmentedImage.createAnchor(augmentedImage.getCenterPose()));

        currentAugmentedImage = augmentedImage;

        externalTexture.getSurfaceTexture().setOnFrameAvailableListener(surfaceTexture -> {
            surfaceTexture.setOnFrameAvailableListener(null);
            fadeInVideo();
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            metadataRetriever.close();
        } else {
            metadataRetriever.release();
        }
    }

    private boolean isArVideoPlaying() {
        return mediaPlayer.isPlaying();
    }

    private void pauseArVideo() {
        videoAnchorNode.setRenderable(null);
        mediaPlayer.pause();
    }

    private void resumeArVideo() {
        mediaPlayer.start();
        fadeInVideo();
    }

    private void dismissArVideo() {
        if (videoAnchorNode.getAnchor() != null) {
            videoAnchorNode.getAnchor().detach();
        }
        videoAnchorNode.setRenderable(null);
        currentAugmentedImage = null;
        mediaPlayer.reset();
    }

    private void fadeInVideo() {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(400L);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(v ->
                videoRenderable.getMaterial().setFloat(MATERIAL_VIDEO_ALPHA, (float) v.getAnimatedValue())
        );
        animator.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(@NonNull Animator animation, boolean isReverse) {
                        super.onAnimationStart(animation, isReverse);
                        videoAnchorNode.setRenderable(videoRenderable);
                    }
                });
        animator.start();
    }

    @Override
    public void onPause() {
        super.onPause();
        dismissArVideo();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mediaPlayer.release();
    }
}
