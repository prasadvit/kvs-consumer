//
//import com.amazonaws.auth.AWSCredentialsProvider;
//import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
//import com.amazonaws.client.builder.AwsClientBuilder;
//import com.amazonaws.kinesisvideo.parser.mkv.MkvElement;
//import com.amazonaws.kinesisvideo.parser.utilities.FragmentMetadataVisitor;
//import com.amazonaws.regions.Regions;
//import com.amazonaws.services.kinesisvideo.AmazonKinesisVideo;
//import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoClientBuilder;
//import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoMedia;
//import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoMediaClientBuilder;
//import com.amazonaws.services.kinesisvideo.model.*;
//import com.amazonaws.services.lambda.runtime.Context;
//import com.amazonaws.services.lambda.runtime.RequestHandler;
//import com.amazonaws.services.transcribestreaming.model.LanguageCode;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.io.InputStream;
//import java.nio.ByteBuffer;
//
//
//public class KVSTranscribeStreamingLambda implements RequestHandler<TranscriptionRequest, String> {
//
//    private static final Logger logger = LoggerFactory.getLogger(KVSTranscribeStreamingLambda.class);
//
//    @Override
//    public String handleRequest(TranscriptionRequest request, Context context) {
//        try {
//
//            initializeSegmentWriters(request);
//
//            // Start KVS to Transcribe Streaming for events
//            startKVSToTranscribeStreaming(request.getStreamARN(), request.getStartFragmentNum(),
//                    request.getConnectContactId(), request.isStreamAudioFromCustomer(), request.isStreamAudioToCustomer());
//
//            return "{ \"result\": \"Success\" }";
//        } catch (Exception e) {
//            logger.error("KVS to Transcribe Streaming failed with: ", e);
//            return "{ \"result\": \"Failed\" }";
//        }
//    }
//
//    private void initializeSegmentWriters(TranscriptionRequest request) {
//        // Initialize segment writers as needed
//    }
//
//    private void startKVSToTranscribeStreaming(String streamARN, String startFragmentNum,
//                                               String connectContactId, boolean streamAudioFromCustomer, boolean streamAudioToCustomer) {
//        AmazonKinesisVideo amazonKinesisVideo = AmazonKinesisVideoClientBuilder.standard().build();
//        AWSCredentialsProvider awsCredentialsProvider = DefaultAWSCredentialsProviderChain.getInstance();
//        String streamName = streamARN.substring(streamARN.indexOf("/") + 1, streamARN.lastIndexOf("/"));
//// Fetch KVS stream's data endpoint
//        GetDataEndpointRequest getDataEndpointRequest = new GetDataEndpointRequest()
//                .withAPIName(APIName.GET_MEDIA)
//                .withStreamName(streamName);
//
//        String endPoint = amazonKinesisVideo.getDataEndpoint(getDataEndpointRequest).getDataEndpoint();
//
//// Create KVS streaming reader
//        AmazonKinesisVideoMediaClientBuilder kinesisVideoMediaClientBuilder = AmazonKinesisVideoMediaClientBuilder.standard()
//                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endPoint, Regions.US_EAST_1.getName()))
//                .withCredentials(awsCredentialsProvider);
//
//        AmazonKinesisVideoMedia kinesisVideoMedia = kinesisVideoMediaClientBuilder.build();
//
//// Create a StartSelector based on the startFragmentNum
//        StartSelector startSelector = new StartSelector()
//                .withStartSelectorType(StartSelectorType.FRAGMENT_NUMBER)
//                .withAfterFragmentNumber(startFragmentNum);
//
//// Create a GetMediaRequest
//        GetMediaRequest getMediaRequest = new GetMediaRequest()
//                .withStreamName(streamName)
//                .withStartSelector(startSelector);
//
//        GetMediaResult getMediaResult = kinesisVideoMedia.getMedia(getMediaRequest);
//        InputStream inputStream = getMediaResult.getPayload();
//
//// Create a StreamingMkvReader to read the stream
//        StreamingMkvReader streamingMkvReader = StreamingMkvReader.createDefault(inputStream);
//
//// Initialize required components for processing frames
//        FragmentMetadataVisitor fragmentVisitor = new FragmentMetadataVisitor();
//        KVSContactTagProcessor tagProcessor = new KVSContactTagProcessor();
//
//// Example: Iterate through KVS stream and process frames for transcription
//        while (streamingMkvReader.mightHaveNext()) {
//            Optional<MkvElement> mkvElementOptional = streamingMkvReader.nextIfAvailable();
//            if (mkvElementOptional.isPresent()) {
//                MkvElement mkvElement = mkvElementOptional.get();
//                // Process the mkvElement, e.g., check for specific MkvTypeInfos
//                // ...
//
//                // Extract audio buffer from frame and perform transcription
//                ByteBuffer audioBuffer = KVSUtils.getByteBufferFromStream(streamingMkvReader, fragmentVisitor, tagProcessor, connectContactId, track);
//                if (streamAudioFromCustomer) {
//                    // Transcribe audio from customer
//                    transcribeAudio(audioBuffer, LanguageCode.EN_US);
//                }
//                if (streamAudioToCustomer) {
//                    // Transcribe audio to customer
//                    transcribeAudio(audioBuffer, LanguageCode.EN_GB);
//                }
//            }
//        }
//    }
//
//    private void transcribeAudio(ByteBuffer audioBuffer, LanguageCode languageCode) {
//        // Set up Transcribe Streaming client and transcribe audio buffer
//        // ...
//    }