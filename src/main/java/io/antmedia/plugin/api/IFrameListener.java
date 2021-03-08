package io.antmedia.plugin.api;

import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avutil.AVFrame;

public interface IFrameListener {

	AVFrame onAudioFrame(String streamId, AVFrame audioFrame);
	AVFrame onVideoFrame(String streamId, AVFrame frame);
	void writeTrailer();
	void setVideoCodecParameter(String streamId, AVCodecParameters videoCodecParameters);
	void setAudioCodecParameter(String streamId, AVCodecParameters audioCodecParameters);
	void start();

}
