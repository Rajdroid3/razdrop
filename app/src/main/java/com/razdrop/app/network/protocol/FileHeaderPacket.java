package com.razdrop.app.network.protocol;

import com.google.gson.annotations.SerializedName;

public class FileHeaderPacket {
    @SerializedName("index")
    private int index;

    @SerializedName("id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("size")
    private long size;

    @SerializedName("mimeType")
    private String mimeType;

    public FileHeaderPacket() {}

    public FileHeaderPacket(int index, String id, String name, long size, String mimeType) {
        this.index = index;
        this.id = id;
        this.name = name;
        this.size = size;
        this.mimeType = mimeType;
    }

    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
}
