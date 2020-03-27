package io.antmedia.datastore.db.types;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value="RoomStream", description="The Room Stream class")
public class RoomStream {
	
	@ApiModelProperty(value = "the ID of Stream")
	private  String streamId;
	
	@ApiModelProperty(value = "the IP of Stream Origin")
	private  String streamOriginIP;
	
	public String getStreamId() {
		return streamId;
	}
	public void setStreamId(String streamId) {
		this.streamId = streamId;
	}
	public String getStreamOriginIP() {
		return streamOriginIP;
	}
	public void setStreamOriginIP(String streamOriginIP) {
		this.streamOriginIP = streamOriginIP;
	}
}
