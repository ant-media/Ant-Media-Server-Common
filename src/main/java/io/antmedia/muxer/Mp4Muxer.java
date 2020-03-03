package io.antmedia.muxer;

import io.antmedia.storage.StorageClient;
import io.antmedia.storage.StorageClient.FileType;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.avcodec;
import org.bytedeco.javacpp.avcodec.*;
import org.bytedeco.javacpp.avformat;
import org.bytedeco.javacpp.avformat.*;
import org.bytedeco.javacpp.avutil.*;
import org.red5.server.api.IContext;
import org.red5.server.api.scheduling.IScheduledJob;
import org.red5.server.api.scheduling.ISchedulingService;
import org.red5.server.api.scope.IScope;
import org.red5.server.scheduling.QuartzSchedulingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.io.File;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.*;

import static org.bytedeco.javacpp.avcodec.*;
import static org.bytedeco.javacpp.avformat.*;
import static org.bytedeco.javacpp.avutil.*;

public class Mp4Muxer extends Muxer {

	protected static Logger logger = LoggerFactory.getLogger(Mp4Muxer.class);
	private List<Integer> registeredStreamIndexList = new ArrayList<>();
	private File fileTmp;
	private StorageClient storageClient = null;
	private String streamId;
	private int videoIndex;
	private int audioIndex;
	private AVBSFContext bsfContext;
	private int resolution; 

	private AVPacket tmpPacket;
	private Map<Integer, AVRational> codecTimeBaseMap = new HashMap<>();

	public static final String TEMP_EXTENSION = ".tmp_extension";
	private AVBSFContext bsfExtractdataContext = null;
	private boolean isAVCConversionRequired = false;
	private AVPacket videoPkt;
	private int rotation;
	private long startTimeInVideoTimebase = 0;
	private long startTimeInAudioTimebase = 0;
	/**
	 * By default first video key frame should be checked
	 * and below flag should be set to true
	 * If first video key frame should not be checked,
	 * then below should be flag in advance
	 */
	private boolean firstKeyFrameReceivedChecked = false;
	
	/**
	 * Dynamic means that this mp4 muxer is added on the fly. 
	 * It means it's started after broadcasting is started and it can be stopped before brodcasting has finished
	 */
	private boolean dynamic = false;


	public Mp4Muxer(StorageClient storageClient, QuartzSchedulingService scheduler) {
		super(scheduler);
		extension = ".mp4";
		format = "mp4";
		options.put("movflags", "faststart");
		this.storageClient = storageClient;
	}

	private static int[] MP4_SUPPORTED_CODECS = {
			AV_CODEC_ID_MOV_TEXT     ,
			AV_CODEC_ID_MPEG4        ,
			AV_CODEC_ID_H264         ,
			AV_CODEC_ID_HEVC         ,
			AV_CODEC_ID_AAC          ,
			AV_CODEC_ID_MP4ALS       , /* 14496-3 ALS */
			AV_CODEC_ID_MPEG2VIDEO  , /* MPEG-2 Main */
			AV_CODEC_ID_MPEG2VIDEO   , /* MPEG-2 Simple */
			AV_CODEC_ID_MPEG2VIDEO   , /* MPEG-2 SNR */
			AV_CODEC_ID_MPEG2VIDEO   , /* MPEG-2 Spatial */
			AV_CODEC_ID_MPEG2VIDEO   , /* MPEG-2 High */
			AV_CODEC_ID_MPEG2VIDEO   , /* MPEG-2 422 */
			AV_CODEC_ID_AAC          , /* MPEG-2 AAC Main */
			AV_CODEC_ID_AAC          , /* MPEG-2 AAC Low */
			AV_CODEC_ID_AAC          , /* MPEG-2 AAC SSR */
			AV_CODEC_ID_MP3          , /* 13818-3 */
			AV_CODEC_ID_MP2          , /* 11172-3 */
			AV_CODEC_ID_MPEG1VIDEO   , /* 11172-2 */
			AV_CODEC_ID_MP3          , /* 11172-3 */
			AV_CODEC_ID_MJPEG        , /* 10918-1 */
			AV_CODEC_ID_PNG          ,
			AV_CODEC_ID_JPEG2000     , /* 15444-1 */
			AV_CODEC_ID_VC1          ,
			AV_CODEC_ID_DIRAC        ,
			AV_CODEC_ID_AC3          ,
			AV_CODEC_ID_EAC3         ,
			AV_CODEC_ID_DTS          , /* mp4ra.org */
			AV_CODEC_ID_VP9          , /* nonstandard, update when there is a standard value */
			AV_CODEC_ID_TSCC2        , /* nonstandard, camtasia uses it */
			AV_CODEC_ID_VORBIS       , /* nonstandard, gpac uses it */
			AV_CODEC_ID_DVD_SUBTITLE , /* nonstandard, see unsupported-embedded-subs-2.mp4 */
			AV_CODEC_ID_QCELP        ,
			AV_CODEC_ID_MPEG4SYSTEMS ,
			AV_CODEC_ID_MPEG4SYSTEMS ,
			AV_CODEC_ID_NONE
	};


	public boolean isCodecSupported(AVCodecParameters avCodecParameters) {
		return isCodecSupported(avCodecParameters.codec_id());
	}

	private boolean isCodecSupported(int codecId) {
		for (int i=0; i< MP4_SUPPORTED_CODECS.length; i++) {
			if (codecId == MP4_SUPPORTED_CODECS[i]) {
				return true;
			}
		}
		return false;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void init(IScope scope, final String name, int resolutionHeight) {
		super.init(scope, name, resolutionHeight, false);

		this.streamId = name;
		this.resolution = resolutionHeight;

		tmpPacket = avcodec.av_packet_alloc();
		av_init_packet(tmpPacket);

		videoPkt = avcodec.av_packet_alloc();
		av_init_packet(videoPkt);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized boolean addVideoStream(int width, int height, AVRational timebase, int codecId, int streamIndex,
			boolean isAVC, AVCodecParameters codecpar) {
		boolean result = false;
		AVFormatContext outputContext = getOutputFormatContext();
		if (outputContext != null && isCodecSupported(codecId))
		{
			registeredStreamIndexList.add(streamIndex);
			AVStream outStream = avformat_new_stream(outputContext, null);
			outStream.codecpar().width(width);
			outStream.codecpar().height(height);
			outStream.codecpar().codec_id(codecId);
			outStream.codecpar().codec_type(AVMEDIA_TYPE_VIDEO);
			outStream.codecpar().format(AV_PIX_FMT_YUV420P);
			outStream.codecpar().codec_tag(0);
			AVRational timeBase = new AVRational();
			timeBase.num(1).den(1000);
			codecTimeBaseMap.put(streamIndex, timeBase);
			isAVCConversionRequired = true;
			result = true;
		}

		return result;
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized boolean addStream(AVCodec codec, AVCodecContext codecContext, int streamIndex) {

		AVFormatContext outputContext = getOutputFormatContext();

		if (outputContext == null) {
			return false;
		}

		if (isCodecSupported(codecContext.codec_id())) {
			registeredStreamIndexList.add(streamIndex);
			AVStream outStream = avformat_new_stream(outputContext, null);

			outStream.time_base(codecContext.time_base());
			int ret = avcodec_parameters_from_context(outStream.codecpar(), codecContext);

			logger.info("codec par extradata size {} codec id: {}", outStream.codecpar().extradata_size(), codecContext.codec_id());
			if (ret < 0) {
				logger.error("codec context cannot be copied for {}", streamId);
			}

			outStream.codecpar().codec_tag(0);
			codecTimeBaseMap.put(streamIndex, codecContext.time_base());
		}
		return true;
	}

	public AVFormatContext getOutputFormatContext() {
		if (outputFormatContext == null) {
			outputFormatContext= new AVFormatContext(null);
			fileTmp = new File(file.getAbsolutePath() + TEMP_EXTENSION);
			int ret = avformat_alloc_output_context2(outputFormatContext, null, format, fileTmp.getAbsolutePath());
			if (ret < 0) {
				logger.info("Could not create output context for {}", streamId);
				return null;
			}
		}
		return outputFormatContext;
	}

	public AVStream avNewStream(AVFormatContext context) {
		return avformat_new_stream(context, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized boolean prepare(AVFormatContext inputFormatContext) {
		AVFormatContext context = getOutputFormatContext();

		int streamIndex = 0;
		for (int i=0; i < inputFormatContext.nb_streams(); i++) {
			AVStream inStream = inputFormatContext.streams(i);
			
			if (isCodecSupported(inStream.codecpar())) 
			{
				int codecType = inStream.codecpar().codec_type();
				
				if ( codecType == AVMEDIA_TYPE_VIDEO) 
				{
					AVStream outStream = avNewStream(context);
					videoIndex = streamIndex;
					int ret = avcodec_parameters_copy(outStream.codecpar(), inStream.codecpar());

					if (ret < 0) {
						logger.error("Cannot get codec parameters for {}", streamId);
						return false;
					}
					logger.info("video codec par extradata size {} codec id: {}", outStream.codecpar().extradata_size(), outStream.codecpar().codec_id());
					streamIndex++;
					registeredStreamIndexList.add(i);

					outStream.codecpar().codec_tag(0);
				}
				else if (codecType == AVMEDIA_TYPE_AUDIO) 
				{
					AVStream outStream = avNewStream(context);
					audioIndex = streamIndex;

					if (bsfName != null) {
						AVBitStreamFilter adtsToAscBsf = av_bsf_get_by_name(this.bsfName);
						bsfContext = new AVBSFContext(null);

						int ret = av_bsf_alloc(adtsToAscBsf, bsfContext);
						if (ret < 0) {
							logger.info("cannot allocate bsf context for {}", streamId);
							return false;
						}

						ret = avcodec_parameters_copy(bsfContext.par_in(), inStream.codecpar());
						if (ret < 0) {
							logger.info("cannot copy input codec parameters for {}", streamId);
							return false;
						}
						bsfContext.time_base_in(inStream.time_base());

						ret = av_bsf_init(bsfContext);
						if (ret < 0) {
							logger.info("cannot init bit stream filter context for {}", streamId);
							return false;
						}

						ret = avcodec_parameters_copy(outStream.codecpar(), bsfContext.par_out());
						if (ret < 0) {
							logger.info("cannot copy codec parameters to output for {}", streamId);
							return false;
						}

						outStream.time_base(bsfContext.time_base_out());
					}
					else {
						int ret = avcodec_parameters_copy(outStream.codecpar(), inStream.codecpar());
						if (ret < 0) {
							logger.info("Cannot get codec parameters for {}", streamId);
							return false;
						}
					}
					
					streamIndex++;
					registeredStreamIndexList.add(i);
					outStream.codecpar().codec_tag(0);
					
				}
				else {
					logger.error("undefined codec type: {}" , codecType);
				}

			}
		}

		prepareIO();
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized boolean prepareIO() {
	

		AVFormatContext context = getOutputFormatContext();
		if (context == null || context.pb() != null) {
			//return false if it is already prepared
			return false;
		}

		AVIOContext pb = new AVIOContext(null);

		int ret = avformat.avio_open(pb, fileTmp.getAbsolutePath(), AVIO_FLAG_WRITE);
		if (ret < 0) {
			logger.warn("Could not open output file: {}" +
					" parent file exists:{}" , fileTmp.getAbsolutePath() , fileTmp.getParentFile().exists());
			return false;
		}

		context.pb(pb);

		AVDictionary optionsDictionary = null;

		if (!options.isEmpty()) {
			optionsDictionary = new AVDictionary();
			Set<String> keySet = options.keySet();
			for (String key : keySet) {
				av_dict_set(optionsDictionary, key, options.get(key), 0);
			}
		}

		ret = avformat_write_header(context, optionsDictionary);
		if (ret < 0) {
			logger.warn("could not write header for {}", fileTmp.getName());

			clearResource();
			return false;
		}
		if (optionsDictionary != null) {
			av_dict_free(optionsDictionary);
		}
		
		isRunning.set(true);
		return true;
	}

	public static void remux(String srcFile, String dstFile, int rotation) {
		AVFormatContext inputContext = new AVFormatContext(null);
		int ret;
		if ((ret = avformat_open_input(inputContext,srcFile, null, null)) < 0) {
			logger.warn("cannot open input context {} errror code: {}", srcFile, ret);
			return;
		}

		ret = avformat_find_stream_info(inputContext, (AVDictionary)null);

		if (ret < 0) {
			logger.warn("Cannot find stream info {}", srcFile);
			return;
		}


		AVFormatContext outputContext = new AVFormatContext(null);
		avformat_alloc_output_context2(outputContext, null, null, dstFile);

		int streamCount = inputContext.nb_streams();
		for (int i = 0; i < streamCount; i++) {
			AVStream stream = avformat_new_stream(outputContext, null);
			ret = avcodec_parameters_copy(stream.codecpar(), inputContext.streams(i).codecpar());
			if (ret < 0) {
				logger.warn("Cannot copy codecpar parameters from {} to {} for stream index {}", srcFile, dstFile, i);
				return;
			}
			stream.codecpar().codec_tag(0);

			if (stream.codecpar().codec_type() == AVMEDIA_TYPE_VIDEO) {
				AVDictionary metadata = new AVDictionary();
				av_dict_set(metadata, "rotate", rotation+"", 0);
				stream.metadata(metadata);
			}
		}

		AVIOContext pb = new AVIOContext(null);
		ret = avio_open(pb, dstFile, AVIO_FLAG_WRITE);
		if (ret < 0) {
			logger.warn("Cannot open io context {}", dstFile);
			return;
		}
		outputContext.pb(pb);

		ret = avformat_write_header(outputContext, (AVDictionary)null);
		if (ret < 0) {
			logger.warn("Cannot write header to {}", dstFile);
			return;
		}

		AVPacket pkt = new AVPacket();
		while (av_read_frame(inputContext, pkt) == 0) {

			AVStream inStream = inputContext.streams(pkt.stream_index());
			AVStream outStream = outputContext.streams(pkt.stream_index());

			/* copy packet */
			pkt.pts(av_rescale_q_rnd(pkt.pts(), inStream.time_base(), outStream.time_base(), AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX));
			pkt.dts(av_rescale_q_rnd(pkt.dts(), inStream.time_base(), outStream.time_base(), AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX));
			pkt.duration(av_rescale_q(pkt.duration(), inStream.time_base(), outStream.time_base()));
			pkt.pos(-1);
			av_write_frame(outputContext, pkt);
			av_packet_unref(pkt);
		}

		av_write_trailer(outputContext);

		avformat_close_input(inputContext);

		avio_closep(outputContext.pb());
		avformat_free_context(outputContext);
	}

	@Override
	public void writeVideoBuffer(ByteBuffer encodedVideoFrame, long timestamp, int frameRotation, int streamIndex,boolean isKeyFrame,long firstFrameTimeStamp) {
		/*
		 * this control is necessary to prevent server from a native crash 
		 * in case of initiation and preparation takes long.
		 * because native objects like videoPkt can not be initiated yet
		 */
		if (!isRunning.get()) {
			logger.warn("Not writing to VideoBuffer for {} because Is running:{}", streamId, isRunning.get());
			return;
		}
		
		/*
		* Rotation field is used add metadata to the mp4.
		* this method is called in directly creating mp4 from coming encoded WebRTC H264 stream
		*/
		this.rotation = frameRotation;
		videoPkt.stream_index(streamIndex);
		videoPkt.pts(timestamp);
		videoPkt.dts(timestamp);
        if(isKeyFrame) {
            videoPkt.flags(videoPkt.flags() | AV_PKT_FLAG_KEY);
        }

		((Buffer)encodedVideoFrame).rewind();
		videoPkt.data(new BytePointer(encodedVideoFrame));
		videoPkt.size(((Buffer)encodedVideoFrame).limit());
		videoPkt.position(0);
		writePacket(videoPkt);

		av_packet_unref(videoPkt);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void writeTrailer() {

		if (!isRunning.get() || outputFormatContext == null || outputFormatContext.pb() == null) {
			//return if it is already null
			logger.warn("OutputFormatContext is not initialized or it is freed for file {}", fileTmp != null ? fileTmp.getName() : null);
			return;
		}

		logger.info("Mp4Muxer writing trailer for stream: {}", streamId);
		isRunning.set(false);

		av_write_trailer(outputFormatContext);

		clearResource();

		isRecording = false;
		
		scheduler.addScheduledOnceJob(0, new IScheduledJob() {

			@Override
			public void execute(ISchedulingService service) throws CloneNotSupportedException {
				try {
					
					String absolutePath = fileTmp.getAbsolutePath();

					String origFileName = absolutePath.replace(TEMP_EXTENSION, "");
					
					final File f = new File(origFileName);
					
					logger.info("File: {} exist: {}", fileTmp.getAbsolutePath(), fileTmp.exists());
					if (isAVCConversionRequired ) {
						logger.info("AVC conversion needed for MP4 {}", fileTmp.getName());
						remux(fileTmp.getAbsolutePath(),f.getAbsolutePath(), rotation);
						Files.delete(fileTmp.toPath());
					}
					else {
						Files.move(fileTmp.toPath(),f.toPath());
					}

					logger.info("MP4 {} is ready", f.getName());

					IContext context = Mp4Muxer.this.scope.getContext();
					ApplicationContext appCtx = context.getApplicationContext();
					Object bean = appCtx.getBean("web.handler");
					if (bean instanceof IAntMediaStreamHandler) {
						((IAntMediaStreamHandler)bean).muxingFinished(streamId, f, getDuration(f), resolution);
					}

					if (storageClient != null) {
						logger.info("Storage client is available saving {} to storage", f.getName());
						scheduler.addScheduledOnceJob(1000, new IScheduledJob() {

							@Override
							public void execute(ISchedulingService service) throws CloneNotSupportedException {
								
								// Check file exist in S3 and change file names. In this way, new file is created after the file name changed.
								
								String fileName = streamId + ".mp4";
								if (storageClient.fileExist(FileType.TYPE_STREAM.getValue() + "/" + fileName)) {
									
									String tmpName =  fileName;
									
									int i = 0;
									do {
										i++;
										fileName = tmpName.replace(".", "_"+ i +".");
									} while (storageClient.fileExist(FileType.TYPE_STREAM.getValue() + "/" + fileName));
								}
								
								storageClient.save(FileType.TYPE_STREAM.getValue() + "/" + fileName, f);
							}
						});

					}
				} catch (Exception e) {
					logger.error(e.getMessage());
				}
			}
		});

	}

	public long getDuration(File f) {
		AVFormatContext inputFormatContext = avformat.avformat_alloc_context();
		int ret;
		if (avformat_open_input(inputFormatContext, f.getAbsolutePath(), null, (AVDictionary)null) < 0) {
			logger.info("cannot open input context for duration for stream: {}", streamId);
			avformat_close_input(inputFormatContext);
			return -1L;
		}

		ret = avformat_find_stream_info(inputFormatContext, (AVDictionary)null);
		if (ret < 0) {
			logger.info("Could not find stream informatio for stream: {}", streamId);
			avformat_close_input(inputFormatContext);
			return -1L;
		}
		long durationInMS = -1;
		if (inputFormatContext.duration() != AV_NOPTS_VALUE)
		{
			durationInMS = inputFormatContext.duration() / 1000;
		}
		avformat_close_input(inputFormatContext);
		return durationInMS;
	}

	private void clearResource() {

		if (bsfContext != null) {
			av_bsf_free(bsfContext);
			bsfContext = null;
		}
		if (tmpPacket != null) {
			av_packet_free(tmpPacket);
			tmpPacket = null;
		}

		if (videoPkt != null) {
			av_packet_free(videoPkt);
			videoPkt = null;
		}

		/* close output */
		if ((outputFormatContext.flags() & AVFMT_NOFILE) == 0)
			avio_closep(outputFormatContext.pb());

		avformat_free_context(outputFormatContext);
		outputFormatContext = null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void writePacket(AVPacket pkt, AVStream stream) {

		if (!firstKeyFrameReceivedChecked && stream.codec().codec_type() == AVMEDIA_TYPE_VIDEO) {
			//we set start time here because we start recording with key frame and drop the other
			//setting here improves synch between audio and video
			setVideoStartTime(pkt.pts());
			int keyFrame = pkt.flags() & AV_PKT_FLAG_KEY;
			if (keyFrame == 1) {
				firstKeyFrameReceivedChecked = true;
				logger.warn("First key frame received for stream: {}", streamId);
			} else {
				logger.warn("First video packet is not key frame. It will drop for direct muxing. Stream {}", streamId);
				// return if firstKeyFrameReceived is not received
				// below return is important otherwise it does not work with like some encoders(vidiu)
				return;
			}
		}

		if (!isRunning.get() || !registeredStreamIndexList.contains(pkt.stream_index())) {
			logger.warn("Not writing packet1 for {} - Is running:{} or stream index({}) is registered: {}", streamId, isRunning.get(), pkt.stream_index(), registeredStreamIndexList.contains(pkt.stream_index()));
			return;
		}
		int streamIndex;
		if (stream.codecpar().codec_type() == AVMEDIA_TYPE_VIDEO) {
			streamIndex = videoIndex;
		}
		else if (stream.codecpar().codec_type() == AVMEDIA_TYPE_AUDIO) {
			streamIndex = audioIndex;
		}
		else {
			logger.error("Undefined codec type for stream: {} ", streamId);
			return;
		}

		AVStream outStream = outputFormatContext.streams(streamIndex);
		int index = pkt.stream_index();
		pkt.stream_index(streamIndex);

		writePacket(pkt, stream.time_base(),  outStream.time_base(), outStream.codecpar().codec_type());

		pkt.stream_index(index);
	}

	private void setVideoStartTime(long time) {
		if (startTimeInVideoTimebase == 0) {
			startTimeInVideoTimebase = time;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void writePacket(AVPacket pkt) {
		if (!isRunning.get() || !registeredStreamIndexList.contains(pkt.stream_index())) {
			logger.warn("Not writing packet for {} - Is running:{} or stream index({}) is registered: {}", streamId, isRunning.get(), pkt.stream_index(), registeredStreamIndexList.contains(pkt.stream_index()));
			return;
		}

		AVStream outStream = outputFormatContext.streams(pkt.stream_index());
		AVRational codecTimebase = codecTimeBaseMap.get(pkt.stream_index());
		int codecType = outStream.codecpar().codec_type();

		if (!firstKeyFrameReceivedChecked && codecType == AVMEDIA_TYPE_VIDEO) {
			int keyFrame = pkt.flags() & AV_PKT_FLAG_KEY;
			//we set start time here because we start recording with key frame and drop the other
			//setting here improves synch between audio and video
			setVideoStartTime(pkt.pts());
			if (keyFrame == 1) {
				firstKeyFrameReceivedChecked = true;
				logger.warn("First key frame received for stream: {}", streamId);
			} else {
				logger.info("First video packet is not key frame. It will drop for direct muxing. Stream {}", streamId);
				// return if firstKeyFrameReceived is not received
				// below return is important otherwise it does not work with like some encoders(vidiu)
				return;
			}
		}
		writePacket(pkt, codecTimebase,  outStream.time_base(), codecType);

	}


	/**
	 * All other writePacket functions call this function to make the job
	 * 
	 * @param pkt 
	 * Content of the data in AVPacket class
	 * 
	 * @param inputTimebase
	 * input time base is required to calculate the correct dts and pts values for the container
	 * 
	 * @param outputTimebase
	 * output time base is required to calculate the correct dts and pts values for the container
	 */
	private void writePacket(AVPacket pkt, AVRational inputTimebase, AVRational outputTimebase, int codecType) 
	{
		
		AVFormatContext context = getOutputFormatContext();
		if (context == null || context.pb() == null) {
			logger.warn("output context.pb field is null for stream: {}", streamId);
			return;
		}
		
		long pts = pkt.pts();
		long dts = pkt.dts();
		long duration = pkt.duration();
		long pos = pkt.pos();

		pkt.duration(av_rescale_q(pkt.duration(), inputTimebase, outputTimebase));
		pkt.pos(-1);

		if (codecType == AVMEDIA_TYPE_AUDIO) 
		{
			if (startTimeInAudioTimebase == 0) {
				startTimeInAudioTimebase = pkt.pts();
			}
			pkt.pts(av_rescale_q_rnd(pkt.pts() - startTimeInAudioTimebase, inputTimebase, outputTimebase, AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX));
			pkt.dts(av_rescale_q_rnd(pkt.dts() - startTimeInAudioTimebase, inputTimebase, outputTimebase, AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX));
			
			
			int ret = av_packet_ref(tmpPacket , pkt);
			if (ret < 0) {
				logger.error("Cannot copy audio packet for {}", streamId);
				return;
			}
			if (bsfContext != null) {
				ret = av_bsf_send_packet(bsfContext, tmpPacket);
				if (ret < 0)
					return;

				while (av_bsf_receive_packet(bsfContext, tmpPacket) == 0) 
				{

					ret = av_write_frame(context, tmpPacket);
					if (ret < 0 && logger.isInfoEnabled()) {
						byte[] data = new byte[2048];
						av_strerror(ret, data, data.length);
						logger.info("cannot write audio frame to muxer({}) av_bsf_receive_packet. Error is {} ", file.getName(), new String(data, 0, data.length));
						logger.info("input timebase num/den {}/{}"
								+ "output timebase num/den {}/{}", inputTimebase.num(), inputTimebase.den(),
								outputTimebase.num(),  outputTimebase.den());

						logger.info("received dts {}", dts);
						logger.info("calculated dts {}", pkt.dts());
					}

				}
			}
			else {
				ret = av_write_frame(context, tmpPacket);
				if (ret < 0 && logger.isInfoEnabled()) {

					byte[] data = new byte[2048];
					av_strerror(ret, data, data.length);
					logger.info("cannot write audio frame to muxer({}). Error is {} ", file.getName(), new String(data, 0, data.length));
				}
			}

			av_packet_unref(tmpPacket);
		}
		else if (codecType == AVMEDIA_TYPE_VIDEO) 
		{
			// we don't set startTimeInVideoTimebase here because we only start with key frame and we drop all frames 
			// until the first key frame
			pkt.pts(av_rescale_q_rnd(pkt.pts() - startTimeInVideoTimebase, inputTimebase, outputTimebase, AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX));
			pkt.dts(av_rescale_q_rnd(pkt.dts() - startTimeInVideoTimebase, inputTimebase, outputTimebase, AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX));
			
			
			int ret = av_packet_ref(tmpPacket , pkt);
			if (ret < 0) {
				logger.error("Cannot copy video packet for {}", streamId);
				return;
			}

			if (bsfExtractdataContext != null) {
				ret = av_bsf_send_packet(bsfExtractdataContext, tmpPacket);
				if (ret < 0)
					return;

				while (av_bsf_receive_packet(bsfExtractdataContext, tmpPacket) == 0) 
				{
					ret = av_write_frame(context, tmpPacket);
					if (ret < 0 && logger.isWarnEnabled()) {
						byte[] data = new byte[2048];
						av_strerror(ret, data, data.length);
						logger.warn("cannot write video frame to muxer({}) av_bsf_receive_packet. Error is {} ", file.getName(), new String(data, 0, data.length));
					}

				}
			}
			else {
				ret = av_write_frame(context, pkt);
				if (ret < 0 && logger.isWarnEnabled()) {
					byte[] data = new byte[2048];
					av_strerror(ret, data, data.length);
					logger.warn("cannot write video frame to muxer({}) not audio. Error is {} ", file.getName(), new String(data, 0, data.length));
				}
			}
			
			av_packet_unref(tmpPacket);

		}
		else {
			//for any other stream like subtitle, etc.
			int ret = av_write_frame(context, pkt);
			if (ret < 0 && logger.isWarnEnabled()) {
				byte[] data = new byte[2048];
				av_strerror(ret, data, data.length);
				logger.warn("cannot write frame to muxer({}) not audio. Error is {} ", file.getName(), new String(data, 0, data.length));
			}
		}


		pkt.pts(pts);
		pkt.dts(dts);
		pkt.duration(duration);
		pkt.pos(pos);

	}
	
	public List<Integer> getRegisteredStreamIndexList() {
		return registeredStreamIndexList;
	}

	public void setDynamic(boolean dynamic) {
		this.dynamic = dynamic;
	}
	
	public boolean isDynamic() {
		return dynamic;
	}
}
