package com.example.hkvideo.web.dto;

public record PreviewRequest(
        String protocol,
        Integer streamType,
        Integer transmode,
        String expand,
        String streamform
) {
}
