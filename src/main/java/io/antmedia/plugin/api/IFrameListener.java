package io.antmedia.plugin.api;

import org.bytedeco.ffmpeg.avutil.AVFrame;

public interface IFrameListener {

	AVFrame onAudioFrame(String streamId, AVFrame audioFrame);
	AVFrame onVideoFrame(String streamId, AVFrame videoFrame);
	void writeTrailer();
	void setVideoStreamInfo(String streamId, StreamParametersInfo videoStreamInfo);
	void setAudioStreamInfo(String streamId, StreamParametersInfo audioStreamInfo);
	void start();

}
