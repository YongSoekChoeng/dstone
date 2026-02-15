package net.dstone.boot.sample.swagger.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(description = "호출결과를 담는 모델")
public class ResultVo {
	@ApiModelProperty(value = "성공여부", example = "Y")
    private String successYn;
	@ApiModelProperty(value = "오류메세지", example = "")
    private String errMsg;
	public String getSuccessYn() {
		return successYn;
	}
	public void setSuccessYn(String successYn) {
		this.successYn = successYn;
	}
	public String getErrMsg() {
		return errMsg;
	}
	public void setErrMsg(String errMsg) {
		this.errMsg = errMsg;
	}
	@Override
	public String toString() {
		return "Result [successYn=" + successYn + ", errMsg=" + errMsg + "]";
	}
}
