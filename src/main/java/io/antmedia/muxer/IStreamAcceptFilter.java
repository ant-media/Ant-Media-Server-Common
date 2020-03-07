package io.antmedia.muxer;

import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;

public interface IStreamAcceptFilter {
	
	public static final String BEAN_NAME = "streamAcceptFilter";
	
	public boolean isValidStreamParameters(AVFormatContext inputFormatContext,AVPacket pkt);
	
	
	
}
