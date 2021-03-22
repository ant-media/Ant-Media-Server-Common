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

	private String endpoint;
	private String accessKey;
	private String secretKey;
	private String region;
	private String storageName;
	
	/**
	 * Permission of the file that is uploaded to the Storage. 
	 * It may differ according to the implemenation. Default value is public-read
	 */
	private String permission = "public-read";

	
	/**
	 * Delete file from storage
	 * 
	 * @param fileName
	 * @param type
	 */
	public abstract void delete(String fileName, String type);
	
	/**
	 * Save file to storage and delete the local file
	 * 
	 * @param file
	 * File to be saved to storage
	 * @param type
	 * type of the file
	 */
	public abstract void save(final File file, String type);
	
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
}
