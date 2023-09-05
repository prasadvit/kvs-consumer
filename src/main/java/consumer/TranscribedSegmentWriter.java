package consumer;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.transcribestreaming.model.Result;
import software.amazon.awssdk.services.transcribestreaming.model.TranscriptEvent;

import java.text.NumberFormat;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class TranscribedSegmentWriter {

    private String contactId;
    private DynamoDB ddbClient;
    private String tableName;
    private static final Logger logger = LoggerFactory.getLogger(TranscribedSegmentWriter.class);

    public TranscribedSegmentWriter(String contactId, String tableName) {

        AmazonDynamoDBClientBuilder builder = AmazonDynamoDBClientBuilder.standard();
        builder.setRegion(Regions.US_EAST_1.getName());
        this.contactId = Validate.notNull(contactId);

        this.ddbClient = new DynamoDB(builder.build());
        this.tableName = tableName;
    }

    public String getContactId() {

        return this.contactId;
    }

    public DynamoDB getDdbClient() {

        return this.ddbClient;
    }

    public void writeToDynamoDB(String transcriptData) {
        logger.info("table name: " + tableName);
        Validate.notNull(transcriptData);

         logger.info("transcriptData "+transcriptData);
                    Item ddbItem = toDynamoDbItem(transcriptData);
                    if (ddbItem != null) {
                        getDdbClient().getTable(tableName).putItem(ddbItem);
                    }

            }



    private Item toDynamoDbItem(String result ) {

        Item   ddbItem = new Item()
                        .withKeyComponent("CallId-UniqueNumber", getContactId()+ UUID.randomUUID().toString())
                        .withString("voice_to_text", result);

        return ddbItem;
    }


}
