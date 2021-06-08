package io.antmedia.storage;

import java.io.File;

public abstract class StorageClient {
	
	
	public static final String BEAN_NAME = "app.storageClient";
	
	public enum FileType {
		TYPE_PREVIEW("previews"),
		TYPE_STREAM("streams");
		
		private String value;

		private FileType(String value) {
			this.value = value;
		}
		
		public String getValue() {
			return value;
		}
	}

	/**
	 * Endpoint for the storage, it's optional and it's used in some platforms like Digital Ocean, Wasabi, OVH
	 */
	private String endpoint;
	
	/**
	 * Access key to access the storage
	 */
	private String accessKey;
	/**
	 * Secret key for the storage
	 */
	private String secretKey;
	
	/**
	 * The region of the storage. It's like us-west-1 in S3
	 */
	private String region;
	
	/**
	 * Name of the storage. It's bucketname for S3
	 */
	private String storageName;
	
	/**
	 * Permission of the file that is uploaded to the Storage. 
	 * It may differ according to the implemenation. Default value is public-read
	 */
	private String permission = "public-read";

	private boolean enabled;

	
	/**
	 * Delete file from storage
	 * 
	 * @param fileName
	 * @param type
	 */
	public abstract void delete(String fileName, FileType type);
	
	/**
	 * Save file to storage and delete the local file
	 * 
	 * @param file
	 * File to be saved to storage
	 * @param type
	 * type of the file
	 */
	public abstract void save(final File file, FileType type);
	
	/**
	 * Save file to storage and delete the local file 
	 * 
	 * @param key
	 * @param file
	 */
	public abstract void save(String key, File file);

	/**
	 * Checks file exists on storage
	 * 
	 * @param fileName
	 * @param type
	 * @return
	 */
	public abstract boolean fileExist(String fileName, FileType type);
	
	/**
	 * Check if the key exists in the bucket
	 * 
	 * @param key
	 * @return
	 */
	public abstract boolean fileExist(String key);

	public String getEndpoint() {
		return endpoint;
	}

	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	public String getAccessKey() {
		return accessKey;
	}

	public void setAccessKey(String accessKey) {
		this.accessKey = accessKey;
	}

	public String getSecretKey() {
		return secretKey;
	}

	public void setSecretKey(String secretKey) {
		this.secretKey = secretKey;
	}

	public String getRegion() {
		return region;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public String getStorageName() {
		return storageName;
	}

	public void setStorageName(String storageName) {
		this.storageName = storageName;
	}

	public void setPermission(String permission) {
		this.permission = permission;
	}
	
	public String getPermission() {
		return permission;
	}
	
	public boolean isEnabled() {
		return enabled;
	}
	
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
}
