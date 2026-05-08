package com.example.hkvideo.web.dto;

public record PreviewResponse(
        String cameraIndexCode,
        String protocol,
        String url,
        String proxiedUrl
) {
}
