package com.mmrtr.lol.backfill.gcs;

import com.google.cloud.WriteChannel;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;

@Component
public class GcsUploader {

    private final Storage storage;

    public GcsUploader(Storage storage) {
        this.storage = storage;
    }

    /**
     * 지정된 bucket/object 위치로 스트리밍 업로드를 시작한다.
     * 반환된 OutputStream 은 caller 가 닫아야 하며, close 시점에 실제 업로드가 커밋된다.
     */
    public OutputStream openWriteStream(String bucket, String objectName, String contentType) {
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucket, objectName))
                .setContentType(contentType)
                .build();
        WriteChannel channel = storage.writer(blobInfo);
        return Channels.newOutputStream(channel);
    }

    public void uploadBytes(String bucket, String objectName, byte[] content, String contentType) {
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucket, objectName))
                .setContentType(contentType)
                .build();
        try (WriteChannel channel = storage.writer(blobInfo)) {
            channel.write(ByteBuffer.wrap(content));
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to upload to gs://" + bucket + "/" + objectName, e);
        }
    }
}
