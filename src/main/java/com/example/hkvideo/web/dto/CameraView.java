package com.example.hkvideo.web.dto;

public record CameraView(
        String cameraIndexCode,
        String cameraName,
        String regionIndexCode,
        String regionName,
        String deviceIndexCode,
        String channelNo,
        String status,
        String statusName
) {
}
