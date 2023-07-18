package com.avmtechologies.cipr.utils;

import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Renderable;

import androidx.annotation.Nullable;

public class VideoAnchorNode extends AnchorNode {

    private final Node videoNode;

    public VideoAnchorNode() {
        videoNode = new Node();
        videoNode.setParent(this);
    }

    @Override
    public void setRenderable(@Nullable Renderable renderable) {
        videoNode.setRenderable(renderable);
    }

    public void setVideoProperties(
            float videoWidth, float videoHeight, float videoRotation,
            float imageWidth, float imageHeight
    ) {
        videoNode.setLocalScale(scaleCenterCrop(videoWidth, videoHeight, imageWidth, imageHeight));
        rotateNode(videoRotation);
    }

    private Vector3 scaleCenterCrop(float videoWidth, float videoHeight, float imageWidth, float imageHeight) {
        boolean isVideoVertical = videoHeight > videoWidth;
        float videoAspectRatio = isVideoVertical ? videoHeight / videoWidth : videoWidth / videoHeight;
        float imageAspectRatio = isVideoVertical ? imageHeight / imageWidth : imageWidth / imageHeight;

        if (isVideoVertical) {
            if (videoAspectRatio > imageAspectRatio) {
                return new Vector3(imageWidth, 1.0f, imageWidth);
            } else {
                return new Vector3(imageHeight, 1.0f, imageHeight);
            }
        } else {
            if (videoAspectRatio > imageAspectRatio) {
                return new Vector3(imageHeight, 1.0f, imageHeight);
            } else {
                return new Vector3(imageWidth, 1.0f, imageWidth);
            }
        }
    }

    private void rotateNode(float videoRotation) {
        videoNode.setLocalRotation(Quaternion.axisAngle(new Vector3(1.0f, 0.0f, 100.0f), 180f));
    }
}
