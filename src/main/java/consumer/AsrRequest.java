package consumer;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Optional;


public class AsrRequest {


    private String streamARN;
    private String startFragmentName;

    private String connectContactId;



    public String getStreamARN() {
        return streamARN;
    }

    public void setStreamARN(String streamARN) {
        this.streamARN = streamARN;
    }

    public String getStartFragmentName() {
        return startFragmentName;
    }

    public void setStartFragmentName(String startFragmentName) {
        this.startFragmentName = startFragmentName;
    }



    public String getConnectContactId() {
        return connectContactId;
    }

    public void setConnectContactId(String connectContactId) {
        this.connectContactId = connectContactId;
    }


    @Override
    public String toString() {
        return "AsrRequest{" +
                "streamARN='" + streamARN + '\'' +
                ", startFragmentName='" + startFragmentName + '\'' +
                ", connectContactId='" + connectContactId + '\'' +
                '}';
    }
}
