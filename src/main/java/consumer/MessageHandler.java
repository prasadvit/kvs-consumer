package consumer;

import com.amazonaws.util.StringInputStream;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jcodec.common.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


public class MessageHandler implements javax.websocket.MessageHandler.Whole<String> {


    private static  final Logger logger = LoggerFactory.getLogger(MessageHandler.class);
    StringBuffer sb = new StringBuffer();


    @Override
    public void onMessage(String message) {
        try {
            ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree((String) message);
                String transcript = root.get("alternatives").get(0).get("transcript").asText();
                if (!StringUtils.isEmpty(transcript)) {

                    sb.append(transcript);
                    logger.info("message is" + sb.toString());
                }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public StringBuffer getData() {
        return sb;
    }

}
