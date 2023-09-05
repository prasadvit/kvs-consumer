package consumer;


import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.kinesisvideo.parser.ebml.InputStreamParserByteSource;
import com.amazonaws.kinesisvideo.parser.mkv.MkvElement;
import com.amazonaws.kinesisvideo.parser.mkv.StreamingMkvReader;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideo;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoClientBuilder;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoMedia;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoMediaClientBuilder;
import com.amazonaws.services.kinesisvideo.model.*;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jcodec.common.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.transcribestreaming.model.LanguageCode;

import javax.swing.plaf.synth.Region;
import javax.websocket.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class KVSStreamConsumer  implements RequestHandler<AsrRequest, AsrResponse> {

    private static final Logger logger  = LoggerFactory.getLogger(KVSStreamConsumer.class);

    private  FileOutputStream fileOutputStream ;

    private  Path saveAudioFilePath;

    private static final CountDownLatch latch = new CountDownLatch(2);

    private static final String WS_URI = "ws://100.25.148.213:3030/asr/v0.1/stream?content_type=audio/x-raw;format=S16LE;channels=1;rate=16000";


    @Override
    public AsrResponse handleRequest(AsrRequest request, Context context) {


            try {
                WebSocketContainer container = ContainerProvider.getWebSocketContainer();
                Session session = getSession(container);
                Session session2 = getSession(container);
                MessageHandler mh = new MessageHandler();
                MessageHandler mh2 = new MessageHandler();
                session.addMessageHandler(mh);
                session2.addMessageHandler(mh2);

                logger.info("is open" +session.isOpen());

                // Load wav file
                logger.info("context info"+context.toString());
                logger.info("AsrRequest info"+ request.toString());
                String streamARN = request.getStreamARN();
                String streamName = streamARN.substring(streamARN.indexOf("/") + 1, streamARN.lastIndexOf("/"));
                String fileName = String.format("%s_%s.raw", UUID.randomUUID().toString(), "Customer");
                saveAudioFilePath = Paths.get("/tmp", fileName);
                fileOutputStream = new FileOutputStream(saveAudioFilePath.toString());
                logger.info("stream Name info", streamName);

                startKVSToTranscribeStreaming(streamName, request.getStartFragmentName(),
                        request.getConnectContactId(),session,session2);
                session.getBasicRemote().sendBinary(ByteBuffer.wrap(new byte[0]));
                session2.getBasicRemote().sendBinary(ByteBuffer.wrap(new byte[0]));
                latch.await();
                TranscribedSegmentWriter ts = new TranscribedSegmentWriter(  request.getConnectContactId(), "voxflow");
                logger.info("mh.getData()" +mh.getData().length());
                logger.info("mh2.getData()" +mh2.getData().length());
                ts.writeToDynamoDB(mh.getData().toString());
                ts.writeToDynamoDB(mh2.getData().toString());
                ts.
                logger.info("DB write success");
        } catch (Exception e) {
                e.printStackTrace();
        }
        return new AsrResponse(200);
    }

    private Session getSession(WebSocketContainer container) throws DeploymentException, IOException {
        Session session = container.connectToServer(new Endpoint() {
            @Override
            public void onOpen(Session session, EndpointConfig config) {
                session.setMaxIdleTimeout(10 * 60 * 1000);
                logger.info("is open" +session.isOpen());
            }

            @Override
            public void onClose(Session session1,CloseReason closeReason ){
                latch.countDown();
                logger.info("closing session "+ closeReason.getReasonPhrase());
            }

            @Override
            public void onError(Session session1, Throwable thr){
                latch.countDown();
                logger.error("error closing session");
                thr.printStackTrace();
            }
        }, URI.create(WS_URI));
        return session;
    }


//        try {
//            // Start KVS to Transcribe Streaming for events
//            logger.info("context info"+context.toString());
//            logger.info("AsrRequest info"+ request.toString());
//            String streamARN = request.getStreamARN();
//            String streamName = streamARN.substring(streamARN.indexOf("/") + 1, streamARN.lastIndexOf("/"));
//            String fileName = String.format("%s_%s.raw", UUID.randomUUID().toString(), "Customer");
//            saveAudioFilePath = Paths.get("/tmp", fileName);
//            fileOutputStream = new FileOutputStream(saveAudioFilePath.toString());
//
//
//
//
//            logger.info("stream Name info", streamName);
//            startKVSToTranscribeStreaming(streamName, request.getStartFragmentName(),
//                    request.getConnectContactId());
//            return new AsrResponse(200);
//        } catch (Exception e) {
//            logger.error("KVS to Transcribe Streaming failed with: ", e);
//            return new AsrResponse(400);
//        }

    private void startKVSToTranscribeStreaming(String streamARN, String startFragmentNum,
                                               String connectContactId,Session session, Session session2) {

        InputStream inputStream =  KvUtils.getInputStreamFromKVS(streamARN, Regions.US_EAST_1,startFragmentNum,DefaultAWSCredentialsProviderChain.getInstance(),"FRAGMENT_NUMBER" );
        StreamingMkvReader streamingMkvReader = StreamingMkvReader.createDefault(new InputStreamParserByteSource(inputStream));

        while (streamingMkvReader.mightHaveNext()) {
            Optional<MkvElement> mkvElementOptional = streamingMkvReader.nextIfAvailable();
            if (mkvElementOptional.isPresent()) {
                MkvElement mkvElement = mkvElementOptional.get();
                // Extract audio buffer from frame and perform transcription
                try{
                    KvUtils.getByteBufferFromStream(session,session2,mkvElement,connectContactId);

                    //transcribeAudio(session, audioBuffer, LanguageCode.EN_US);
                } catch (Exception e) {
                    logger.error("failed  in startKVSToTranscribeStreaming : ", e);
                    e.printStackTrace();
                }
            }
        }


        AudioUtils.uploadRawAudio(Regions.US_EAST_1,"audiobucketprasad","aud",saveAudioFilePath.toString(),"audiofromCustomer",false,DefaultAWSCredentialsProviderChain.getInstance());


    }

    private void transcribeAudio(Session session,ByteBuffer audio,LanguageCode languageCode) {
        try {
            if(audio.array().length!=0) {
                session.getBasicRemote().sendBinary(ByteBuffer.wrap(audio.array()));
            }
            fileOutputStream.write(audio.array());
        } catch(Exception ex ) {
            logger.error("error in writing file"+ex.getMessage(),ex);

        }
    }
}
