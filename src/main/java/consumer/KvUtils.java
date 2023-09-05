package consumer;


import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.kinesisvideo.parser.ebml.MkvTypeInfos;
import com.amazonaws.kinesisvideo.parser.mkv.Frame;
import com.amazonaws.kinesisvideo.parser.mkv.MkvDataElement;
import com.amazonaws.kinesisvideo.parser.mkv.MkvElement;
import com.amazonaws.kinesisvideo.parser.mkv.MkvElementVisitException;
import com.amazonaws.kinesisvideo.parser.mkv.MkvValue;
import com.amazonaws.kinesisvideo.parser.mkv.StreamingMkvReader;
import com.amazonaws.kinesisvideo.parser.utilities.FragmentMetadataVisitor;
import com.amazonaws.kinesisvideo.parser.utilities.MkvTrackMetadata;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideo;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoClientBuilder;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoMedia;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoMediaClientBuilder;
import com.amazonaws.services.kinesisvideo.model.APIName;
import com.amazonaws.services.kinesisvideo.model.GetDataEndpointRequest;
import com.amazonaws.services.kinesisvideo.model.GetMediaRequest;
import com.amazonaws.services.kinesisvideo.model.GetMediaResult;
import com.amazonaws.services.kinesisvideo.model.StartSelector;
import com.amazonaws.services.kinesisvideo.model.StartSelectorType;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.transcribestreaming.model.LanguageCode;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.websocket.Session;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

import static com.amazonaws.util.StringUtils.isNullOrEmpty;


public final class KvUtils {

    public enum TrackName {
        AUDIO_FROM_CUSTOMER("AUDIO_FROM_CUSTOMER"),
        AUDIO_TO_CUSTOMER("AUDIO_TO_CUSTOMER");

        private String name;

        TrackName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(KvUtils.class);

    private static final String  TRACK="AUDIO_FROM_CUSTOMER";

    public static void getByteBufferFromStream(Session session, Session session2,MkvElement mkvElement,String connectId) throws MkvElementVisitException {
      //  logger.info("connectId"+connectId);

        KVSContactTagProcessor tagProcessor = new KVSContactTagProcessor(connectId);


        FragmentMetadataVisitor fragmentVisitor = FragmentMetadataVisitor.create(Optional.of(tagProcessor));

                mkvElement.accept(fragmentVisitor);
                if (MkvTypeInfos.SIMPLEBLOCK.equals(mkvElement.getElementMetaData().getTypeInfo())) {
                    MkvDataElement dataElement = (MkvDataElement) mkvElement;
                    Frame frame = ((MkvValue<Frame>) dataElement.getValueCopy()).getVal();
                    ByteBuffer audioBuffer = frame.getFrameData();
                    long trackNumber = frame.getTrackNumber();
               //     logger.info("trackNumber"+trackNumber);
                    MkvTrackMetadata metadata = fragmentVisitor.getMkvTrackMetadata(trackNumber);
//                    if(metadata==null)  {
//                        logger.info("metadata is null");
//                    }else {
//                        logger.info("metaNumber" + metadata.getTrackName());
//                        logger.info("metaNumber code" + metadata.getCodecId());
                        if (trackNumber == 1) {
                        //    logger.info("returning "+audioBuffer.array().length);
                            transcribeAudio(session, resampleAudioBuffer(audioBuffer, 8000, 16000), LanguageCode.EN_US); ;
                        }
                    if (trackNumber == 2) {
                        //    logger.info("returning "+audioBuffer.array().length);
                        transcribeAudio(session2, resampleAudioBuffer(audioBuffer, 8000, 16000), LanguageCode.EN_US); ;
                    }
//                        } else if ("Track_audio/L16".equals(metadata.getTrackName()) && TrackName.AUDIO_FROM_CUSTOMER.getName().equals(TRACK)) {
//                            // backwards compatibility
//                            return audioBuffer;
//                        }
                    }



      //  return ByteBuffer.allocate(0);
    }

    public static ByteBuffer resampleAudioBuffer(ByteBuffer inputBuffer, int inputSampleRate, int outputSampleRate) {

        AudioFormat inputFormat = new AudioFormat(inputSampleRate, 16, 1, true, false);
        AudioFormat outputFormat = new AudioFormat(outputSampleRate, 16, 1, true, false);

        byte[] inputBytes = new byte[inputBuffer.remaining()];
        inputBuffer.get(inputBytes);

        try {
            AudioInputStream inputStream = new AudioInputStream(
                    new ByteArrayInputStream(inputBytes), inputFormat, inputBytes.length / inputFormat.getFrameSize());

            AudioFormat targetFormat = new AudioFormat(
                    outputFormat.getEncoding(), outputSampleRate, 16, 1, 2, outputSampleRate, false);

            AudioInputStream resampledStream = AudioSystem.getAudioInputStream(targetFormat, inputStream);

            byte[] outputBytes = new byte[inputBytes.length * outputSampleRate / inputSampleRate];
            int bytesRead = resampledStream.read(outputBytes);
            return ByteBuffer.wrap(outputBytes, 0, bytesRead);
        } catch (Exception e) {
            logger.error("error in transform");
            e.printStackTrace();
            return ByteBuffer.allocate(0);
        }
    }

    /**
     * Fetches ByteBuffer of provided size from the KVS stream by repeatedly calling {@link KVSUtils#getByteBufferFromStream}
     * and concatenating the ByteBuffers to create a single chunk
     *
     * @param streamingMkvReader
     * @param fragmentVisitor
     * @param tagProcessor
     * @param contactId
     * @param chunkSizeInKB
     * @return
     * @throws MkvElementVisitException
     */


    /**
     * Makes a GetMedia call to KVS and retrieves the InputStream corresponding to the given streamName and startFragmentNum
     *
     * @param streamName
     * @param region
     * @param startFragmentNum
     * @param awsCredentialsProvider
     * @return
     */
    public static InputStream getInputStreamFromKVS(String streamName,
                                                    Regions region,
                                                    String startFragmentNum,
                                                    AWSCredentialsProvider awsCredentialsProvider,
                                                    String startSelectorType) {
        Validate.notNull(streamName);
        Validate.notNull(region);
        Validate.notNull(startFragmentNum);
        Validate.notNull(awsCredentialsProvider);

        AmazonKinesisVideo amazonKinesisVideo = (AmazonKinesisVideo) AmazonKinesisVideoClientBuilder.standard().build();

        String endPoint = amazonKinesisVideo.getDataEndpoint(new GetDataEndpointRequest()
                .withAPIName(APIName.GET_MEDIA)
                .withStreamName(streamName)).getDataEndpoint();

        AmazonKinesisVideoMediaClientBuilder amazonKinesisVideoMediaClientBuilder = AmazonKinesisVideoMediaClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endPoint, region.getName()))
                .withCredentials(awsCredentialsProvider);
        AmazonKinesisVideoMedia amazonKinesisVideoMedia = amazonKinesisVideoMediaClientBuilder.build();

        StartSelector startSelector;
        startSelectorType = isNullOrEmpty(startSelectorType) ? "NOW" : startSelectorType;
        switch (startSelectorType) {
            case "FRAGMENT_NUMBER":
                startSelector = new StartSelector()
                        .withStartSelectorType(StartSelectorType.FRAGMENT_NUMBER)
                        .withAfterFragmentNumber(startFragmentNum);
                logger.info("StartSelector set to FRAGMENT_NUMBER: " + startFragmentNum);
                break;
            case "NOW":
            default:
                startSelector = new StartSelector()
                        .withStartSelectorType(StartSelectorType.NOW);
                logger.info("StartSelector set to NOW");
                break;
        }

        GetMediaResult getMediaResult = amazonKinesisVideoMedia.getMedia(new GetMediaRequest()
                .withStreamName(streamName)
                .withStartSelector(startSelector));

        logger.info("GetMedia called on stream {} response {} requestId {}", streamName,
                getMediaResult.getSdkHttpMetadata().getHttpStatusCode(),
                getMediaResult.getSdkResponseMetadata().getRequestId());

        return getMediaResult.getPayload();
    }

    private static  void transcribeAudio(Session session, ByteBuffer audio, LanguageCode languageCode) {
        try {
            if(audio.array().length!=0) {
                session.getBasicRemote().sendBinary(ByteBuffer.wrap(audio.array()));
            }
        //   fileOutputStream.write(audio.array());
        } catch(Exception ex ) {
            logger.error("error in writing file"+ex.getMessage(),ex);

        }
    }
}
