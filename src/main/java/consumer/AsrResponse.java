package consumer;

import lombok.Data;

public class AsrResponse {

    public AsrResponse(int code) {
        this.code = code;
    }
    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    int code;

}
