package io.antmedia.plugin.api;

import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;

public interface IPacketListener {
	AVPacket onPacket(String streamId, AVPacket packet);
	void writeTrailer();
	void setVideoStreamInfo(String streamId, StreamParametersInfo videoStreamInfo);
	void setAudioStreamInfo(String streamId, StreamParametersInfo audioStreamInfo);
}
