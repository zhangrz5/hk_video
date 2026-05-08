package com.example.hkvideo.web.dto;

import java.util.List;

public record CameraPageResponse(
        int pageNo,
        int pageSize,
        long total,
        List<CameraView> list
) {
}
