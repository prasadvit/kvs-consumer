package consumer;

import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;


public final class AudioUtils {

    private static final Logger logger = LoggerFactory.getLogger(AudioUtils.class);


    public static void fetchAudio(Regions region, String bucketName, String objectKey, String audioFilePath, AWSCredentialsProvider awsCredentials) {

        AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                .withRegion(region)
                .withCredentials(awsCredentials)
                .build();

        // save the object locally
        logger.info(String.format("Fetching %s/%s to %s", bucketName, objectKey, audioFilePath));

        File localFile = new File(audioFilePath);
        GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, objectKey);
        ObjectMetadata metaData = s3Client.getObject(getObjectRequest, localFile);

        logger.info(String.format("fetchAudio:  getObject completed successfully %d byte(s) %s",
                metaData.getContentLength(), metaData.getETag()));
    }

    private static File convertToWav(String audioFilePath) throws IOException, UnsupportedAudioFileException {
        File outputFile = new File(audioFilePath.replace(".raw", ".wav"));
        AudioInputStream source = new AudioInputStream(Files.newInputStream(Paths.get(audioFilePath)),
                new AudioFormat(8000, 16, 1, true, false), -1); // 8KHz, 16 bit, 1 channel, signed, little-endian
        AudioSystem.write(source, AudioFileFormat.Type.WAVE, outputFile);
        source.close();
        return outputFile;
    }



//    private static byte[] convertRawInputStreamToWav(byte[] b) {
//        logger.info("Is conversion supported" + AudioSystem.isConversionSupported(Encoding.PCM_FLOAT,AudioFormat.Encoding.
//
//    }


    public static void uploadRawAudio(Regions region, String bucketName, String keyPrefix, String audioFilePath, String contactId, boolean publicReadAcl, AWSCredentialsProvider awsCredentials) {
        File wavFile = null;
        try {

            AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                    .withRegion(region)
                    .withCredentials(awsCredentials)
                    .build();

            wavFile = convertToWav(audioFilePath);

            // upload the raw audio file to the designated S3 location
            String objectKey = keyPrefix + wavFile.getName();

            logger.info(String.format("Uploading Audio: to %s/%s from %s", bucketName, objectKey, wavFile));
            PutObjectRequest request = new PutObjectRequest(bucketName, objectKey, wavFile);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType("audio/wav");
            metadata.addUserMetadata("contact-id", contactId);
            request.setMetadata(metadata);

            if (publicReadAcl) {
                request.setCannedAcl(CannedAccessControlList.PublicRead);
            }

            PutObjectResult s3result = s3Client.putObject(request);

            logger.info("putObject completed successfully " + s3result.getETag());

        } catch (SdkClientException e) {
            logger.error("Audio upload to S3 failed: ", e);
            throw e;
        } catch (UnsupportedAudioFileException|IOException e) {
            logger.error("Failed to convert to wav: ", e);
        }
        finally {
            if (wavFile != null) {
                wavFile.delete();
            }
        }
    }
}
